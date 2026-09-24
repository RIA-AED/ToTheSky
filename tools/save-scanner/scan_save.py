#!/usr/bin/env python3
"""
ToTheSky 存档扫描器 — 从迁移前的备份中提取所有需要回填的 kubejs 物品。

设计要点：
  * 零依赖：自带 NBT 解析器与 SNBT 序列化器，任何装了 Python 3.9+ 的机器都能跑。
  * 类型无损：物品以 SNBT 文本输出，模组侧 `ItemStack.of(TagParser.parseTag(snbt))` 直接还原。
  * 与模组同步：直接解析 MissingMappingEvents.java 的清单来判定 missing / remapped。
  * 覆盖全部存储面：玩家物品栏、末影箱、饰品栏、世界容器、实体容器、展示框、
    Plonk 放置物、精妙背包（物品态/放置态/全局文件）、AE2 元件与驱动器、潜影盒（递归）。

用法：
    python scan_save.py --save <存档目录> --gate                 # 闸门检查
    python scan_save.py --save <存档目录> --out restore.json     # 完整提取
    python scan_save.py --save <存档目录> --out restore.json --missing-only
"""

from __future__ import annotations

import argparse
import gzip
import json
import re
import struct
import sys
import time
import zlib
from pathlib import Path

VERSION = "1.0.0"
FORMAT_VERSION = 1

# --------------------------------------------------------------------------------------
# NBT 标签类型
# --------------------------------------------------------------------------------------

TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

_INT_TAGS = (TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG)
_FLOAT_TAGS = (TAG_FLOAT, TAG_DOUBLE)


class Tag:
    """带类型信息的 NBT 值。Compound 的 value 是 dict[str, Tag]，List 的是 list[Tag]。"""

    __slots__ = ("tid", "value")

    def __init__(self, tid: int, value):
        self.tid = tid
        self.value = value

    def __repr__(self):
        return f"Tag({self.tid},{self.value!r})"


class NBTError(Exception):
    pass


class NBTReader:
    """纯 Python NBT 解析器，保留全部类型信息（byte/short/int/long/float/double 区分）。"""

    def __init__(self, buf: bytes):
        self.b = buf
        self.i = 0

    def _need(self, n: int):
        if self.i + n > len(self.b):
            raise NBTError("unexpected end of NBT data")

    def _u1(self):
        self._need(1)
        v = self.b[self.i]
        self.i += 1
        return v

    def _u2(self):
        self._need(2)
        v = struct.unpack_from(">H", self.b, self.i)[0]
        self.i += 2
        return v

    def _i4(self):
        self._need(4)
        v = struct.unpack_from(">i", self.b, self.i)[0]
        self.i += 4
        return v

    def _i8(self):
        self._need(8)
        v = struct.unpack_from(">q", self.b, self.i)[0]
        self.i += 8
        return v

    def _str(self):
        n = self._u2()
        self._need(n)
        v = self.b[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return v

    def payload(self, tid: int) -> Tag:
        if tid == TAG_BYTE:
            v = self._u1()
            return Tag(tid, v - 256 if v > 127 else v)
        if tid == TAG_SHORT:
            return Tag(tid, self._u2())
        if tid == TAG_INT:
            return Tag(tid, self._i4())
        if tid == TAG_LONG:
            return Tag(tid, self._i8())
        if tid == TAG_FLOAT:
            self._need(4)
            v = struct.unpack_from(">f", self.b, self.i)[0]
            self.i += 4
            return Tag(tid, v)
        if tid == TAG_DOUBLE:
            self._need(8)
            v = struct.unpack_from(">d", self.b, self.i)[0]
            self.i += 8
            return Tag(tid, v)
        if tid == TAG_BYTE_ARRAY:
            n = self._i4()
            self._need(n)
            v = list(self.b[self.i:self.i + n])
            self.i += n
            return Tag(tid, v)
        if tid == TAG_STRING:
            return Tag(tid, self._str())
        if tid == TAG_LIST:
            et = self._u1()
            n = self._i4()
            if n <= 0:
                return Tag(tid, [])
            if et == 0:
                raise NBTError("list of TAG_End with positive length")
            items = [None] * n
            for k in range(n):
                items[k] = self.payload(et)
            return Tag(tid, items)
        if tid == TAG_COMPOUND:
            out = {}
            while True:
                t = self._u1()
                if t == 0:
                    break
                name = self._str()
                out[name] = self.payload(t)
            return Tag(tid, out)
        if tid == TAG_INT_ARRAY:
            n = self._i4()
            if n <= 0:
                return Tag(tid, [])
            self._need(4 * n)
            v = list(struct.unpack_from(f">{n}i", self.b, self.i))
            self.i += 4 * n
            return Tag(tid, v)
        if tid == TAG_LONG_ARRAY:
            n = self._i4()
            if n <= 0:
                return Tag(tid, [])
            self._need(8 * n)
            v = list(struct.unpack_from(f">{n}q", self.b, self.i))
            self.i += 8 * n
            return Tag(tid, v)
        raise NBTError(f"unknown tag type {tid}")

    def read_root(self) -> Tag:
        """读根标签：TAG_Compound（带名字）或裸 TAG_Compound。"""
        tid = self._u1()
        if tid == 0:
            raise NBTError("empty NBT")
        if tid == TAG_COMPOUND:
            # 根要么是 {name}{payload}，要么直接是 payload —— 按能否解出名字判断
            save = self.i
            try:
                self._str()
                return self.payload(TAG_COMPOUND)
            except Exception:
                self.i = save
                return self.payload(TAG_COMPOUND)
        return self.payload(tid)


# --------------------------------------------------------------------------------------
# SNBT 序列化（类型无损，Minecraft 的 TagParser 可原生解析）
# --------------------------------------------------------------------------------------

_BARE_KEY = re.compile(r"^[A-Za-z0-9._+-]+$")
_ESCAPES = {"\\": "\\\\", '"': '\\"'}


def _quote(s: str) -> str:
    out = ['"']
    for ch in s:
        out.append(_ESCAPES.get(ch, ch))
    out.append('"')
    return "".join(out)


def _num_str(v) -> str:
    """浮点保证有小数点，避免 SNBT 解析成 int。"""
    if v != v:  # NaN
        return "NaN"
    if v == float("inf"):
        return "Infinity"
    if v == float("-inf"):
        return "-Infinity"
    s = repr(float(v))
    if "e" in s or "E" in s or "." in s:
        return s
    return s + ".0"


def to_snbt(tag: Tag, out: list[str] | None = None) -> str:
    """Tag -> SNBT 文本。类型后缀保留。"""
    buf = out if out is not None else []
    tid, v = tag.tid, tag.value

    if tid == TAG_BYTE:
        buf.append(f"{v}b")
    elif tid == TAG_SHORT:
        buf.append(f"{v}s")
    elif tid == TAG_INT:
        buf.append(str(v))
    elif tid == TAG_LONG:
        buf.append(f"{v}L")
    elif tid == TAG_FLOAT:
        buf.append(_num_str(v) + "f")
    elif tid == TAG_DOUBLE:
        buf.append(_num_str(v) + "d")
    elif tid == TAG_STRING:
        buf.append(_quote(v))
    elif tid == TAG_BYTE_ARRAY:
        buf.append("[B;")
        buf.append(",".join(f"{x}b" for x in v))
        buf.append("]")
    elif tid == TAG_INT_ARRAY:
        buf.append("[I;")
        buf.append(",".join(str(x) for x in v))
        buf.append("]")
    elif tid == TAG_LONG_ARRAY:
        buf.append("[L;")
        buf.append(",".join(f"{x}L" for x in v))
        buf.append("]")
    elif tid == TAG_LIST:
        buf.append("[")
        for k, item in enumerate(v):
            if k:
                buf.append(",")
            to_snbt(item, buf)
        buf.append("]")
    elif tid == TAG_COMPOUND:
        buf.append("{")
        first = True
        for key, val in v.items():
            if not first:
                buf.append(",")
            first = False
            buf.append(key if _BARE_KEY.match(key) else _quote(key))
            buf.append(":")
            to_snbt(val, buf)
        buf.append("}")
    else:
        raise NBTError(f"cannot serialize tag type {tid}")

    return "".join(buf)


# --------------------------------------------------------------------------------------
# NBT 便捷访问器
# --------------------------------------------------------------------------------------


def cget(tag: Tag | None, key: str) -> Tag | None:
    """取 compound 成员，非 compound 返回 None。"""
    if tag is None or tag.tid != TAG_COMPOUND:
        return None
    return tag.value.get(key)


def as_str(tag: Tag | None) -> str | None:
    if tag is not None and tag.tid == TAG_STRING:
        return tag.value
    return None


def as_num(tag: Tag | None):
    if tag is not None and (tag.tid in _INT_TAGS or tag.tid in _FLOAT_TAGS):
        return tag.value
    return None


def as_list(tag: Tag | None) -> list[Tag]:
    if tag is not None and tag.tid == TAG_LIST:
        return tag.value
    return []


def as_seq(tag: Tag | None) -> list[Tag]:
    """List 或数组（IntArray/LongArray/ByteArray）统一成 list[Tag]。

    AE2 的 amts 是 LongArray 而非 List，只认 TAG_LIST 会静默拿到空列表。
    """
    if tag is None:
        return []
    if tag.tid == TAG_LIST:
        return tag.value
    if tag.tid == TAG_LONG_ARRAY:
        return [Tag(TAG_LONG, v) for v in tag.value]
    if tag.tid == TAG_INT_ARRAY:
        return [Tag(TAG_INT, v) for v in tag.value]
    if tag.tid == TAG_BYTE_ARRAY:
        return [Tag(TAG_BYTE, v) for v in tag.value]
    return []


def as_compound(tag: Tag | None) -> dict | None:
    if tag is not None and tag.tid == TAG_COMPOUND:
        return tag.value
    return None


def is_item_stack(tag: Tag | None) -> bool:
    """ItemStack 签名：id 是字符串且存在 Count。

    不可放宽成"含 id 的 compound"——方块实体与实体都有 id，但没有 Count。
    """
    if tag is None or tag.tid != TAG_COMPOUND:
        return False
    v = tag.value
    idt = v.get("id")
    return idt is not None and idt.tid == TAG_STRING and "Count" in v


def stack_id(tag: Tag) -> str:
    return tag.value["id"].value


def stack_count(tag: Tag) -> int:
    return int(as_num(tag.value.get("Count")) or 1)


# --------------------------------------------------------------------------------------
# 区域文件读取
# --------------------------------------------------------------------------------------


def _decompress(blob: bytes, scheme: int) -> bytes | None:
    try:
        if scheme == 1:
            return gzip.decompress(blob)
        if scheme == 2:
            return zlib.decompress(blob)
        if scheme == 3:
            return blob
    except Exception:
        return None
    return None


def iter_region_chunks(path: Path):
    """产出 (chunk_x, chunk_z, 解压后的区块 NBT 字节)。损坏区块静默跳过。"""
    try:
        data = path.read_bytes()
    except OSError:
        return
    if len(data) < 8192:
        return
    for k in range(1024):
        entry = struct.unpack_from(">I", data, 4 * k)[0]
        offset = entry >> 8
        sectors = data[4 * k + 3]
        if sectors == 0 or offset == 0:
            continue
        start = offset * 4096
        if start + 5 > len(data):
            continue
        length = struct.unpack_from(">I", data, start)[0]
        if length <= 1 or start + 4 + length > len(data):
            continue
        scheme = data[start + 4]
        blob = data[start + 5:start + 4 + length]
        raw = _decompress(blob, scheme)
        if raw:
            yield k % 32, k // 32, raw


def decompress_file(path: Path) -> bytes:
    """读 .dat：自动识别 gzip / zlib / 未压缩。"""
    data = path.read_bytes()
    for fn in (gzip.decompress, zlib.decompress):
        try:
            return fn(data)
        except Exception:
            pass
    return data


# --------------------------------------------------------------------------------------
# 维度识别
# --------------------------------------------------------------------------------------

_DIM_BY_FOLDER = {
    "DIM-1": "minecraft:the_nether",
    "DIM1": "minecraft:the_end",
}


def dimension_of(save: Path, sub: Path | None) -> str:
    """由 region/entities 所在目录推断维度 id。"""
    if sub is None:
        return "minecraft:overworld"
    parts = sub.relative_to(save).parts
    if not parts:
        return "minecraft:overworld"
    head = parts[0]
    if head in _DIM_BY_FOLDER:
        return _DIM_BY_FOLDER[head]
    if head == "dimensions" and len(parts) >= 3:
        return f"{parts[1]}:{parts[2]}"
    return "minecraft:overworld"


# --------------------------------------------------------------------------------------
# 模组 remap 清单解析（与 MissingMappingEvents.java 保持同步）
# --------------------------------------------------------------------------------------

_JAVA_LIST = re.compile(r"private static final List<String> (\w+)\s*=\s*List\.of\((.*?)\);", re.S)
_JAVA_STRING = re.compile(r'"([^"]+)"')

# 三张清单里任意一张命中的 kubejs 路径，其方块/物品都会被 remap 接住
_REMAP_LISTS = ("MIGRATED_BLOCKS", "MIGRATED_ITEMS", "MIGRATED_BUCKETS")


def load_remap_set(java_path: Path) -> set[str]:
    """解析 MissingMappingEvents.java，返回被 remap 保住的名字集合（不含命名空间）。"""
    text = java_path.read_text(encoding="utf-8", errors="replace")
    kept: set[str] = set()
    for name, body in _JAVA_LIST.findall(text):
        if name in _REMAP_LISTS:
            # 去掉行注释，避免把注释里的示例当成条目
            body = re.sub(r"//[^\n]*", "", body)
            kept.update(_JAVA_STRING.findall(body))
    return kept


def default_remap_source() -> Path | None:
    """默认向上查找模组源码里的 MissingMappingEvents.java。"""
    here = Path(__file__).resolve()
    for parent in (here.parent, *here.parents):
        cand = parent / "src/main/java/com/fst/tothesky/event/MissingMappingEvents.java"
        if cand.is_file():
            return cand
    return None


def default_baseline() -> Path | None:
    """默认使用与本脚本同目录的 migration-baseline.json。"""
    cand = Path(__file__).resolve().parent / "migration-baseline.json"
    return cand if cand.is_file() else None


def load_baseline(path: Path) -> dict:
    """读迁移基线：迁移时清单 + 被销毁物品表（含/不含替代品）。"""
    doc = json.loads(path.read_text(encoding="utf-8"))
    if "destroyed" not in doc:
        raise ValueError(f"{path} 不是有效的迁移基线（缺少 destroyed 段）")
    return doc


def load_survived_aliases(world: Path | None) -> set[str]:
    """从世界存档 level.dat 的注册表快照里读出"已成功 remap"的旧名集合。

    Forge 的 `MissingMappingsEvent` 一旦把 `kubejs:X` remap 到 `tothesky:X`，
    就会 `addAlias(旧名, 新名)`，并随注册表快照永久写进 `level.dat`
    （`fml.Registries.<registry>.aliases`）。

    因此别名表是"该物品是否已换名存活"的唯一权威记录：
      * 备份是**迁移前**的快照 ⇒ aliases 为空 ⇒ 一切 kubejs 物品都还没被救过。
      * 迁移后再次打开的存档 ⇒ remap 命中的那些名字会留下别名。
    """
    if world is None:
        return set()
    level = world / "level.dat"
    if not level.is_file():
        return set()
    try:
        root = NBTReader(decompress_file(level)).read_root()
    except Exception:
        return set()
    regs = cget(cget(root, "fml"), "Registries")
    if regs is None or regs.tid != TAG_COMPOUND:
        return set()
    survived: set[str] = set()
    for reg in regs.value.values():
        for entry in as_list(cget(reg, "aliases")):
            if entry.tid != TAG_COMPOUND:
                continue
            src = as_str(cget(entry, "K"))
            dst = as_str(cget(entry, "V"))
            if not src or not dst:
                continue
            if src.startswith("kubejs:") and dst.startswith("tothesky:"):
                survived.add(src.split(":", 1)[1])
    return survived


# --------------------------------------------------------------------------------------
# 扫描器
# --------------------------------------------------------------------------------------


class ScanStats:
    def __init__(self):
        self.regions = 0
        self.chunks = 0
        self.files = 0
        self.errors = 0


class Progress:
    """零依赖进度条。

    * TTY：单行 `\\r` 刷新，带方块条。
    * 非 TTY（重定向到日志，如服务器）：按固定间隔输出整行，避免刷屏；
      条用 ASCII 字符，防止非 UTF-8 代码页报错。

    每步都可能被调用上千次，因此绘制按时间节流（TTY 100ms / 日志 15s），
    且只在需要时才构造字符串。
    """

    def __init__(self, label: str, total: int, enabled: bool = True,
                 stream=None, width: int = 26,
                 min_interval: float = 0.1, log_interval: float = 15.0):
        self.label = label
        self.total = max(int(total), 1)
        self.count = 0
        self.enabled = enabled
        self.stream = stream if stream is not None else sys.stderr
        try:
            self.tty = bool(self.stream.isatty())
        except Exception:
            self.tty = False
        self.width = width
        self.min_interval = min_interval
        self.log_interval = log_interval
        self._extra: dict[str, object] = {}
        self._start = time.time()
        self._last_draw = 0.0
        self._last_len = 0
        self._closed = False
        self._ascii = not self.tty
        if self.enabled:
            self._draw()

    def set(self, **extra):
        """更新常驻附加信息（如区块数、命中数）。"""
        self._extra.update(extra)

    def advance(self, n: int = 1, **extra):
        self.count += n
        if extra:
            self._extra.update(extra)
        if not self.enabled or self._closed:
            return
        interval = self.min_interval if self.tty else self.log_interval
        if time.time() - self._last_draw >= interval:
            self._draw()

    @staticmethod
    def _fmt_dur(sec: float) -> str:
        sec = int(max(0.0, sec))
        if sec < 60:
            return f"{sec}s"
        m, s = divmod(sec, 60)
        if m < 60:
            return f"{m}m{s:02d}s"
        h, m = divmod(m, 60)
        return f"{h}h{m:02d}m"

    def _render(self) -> str:
        frac = min(1.0, self.count / self.total)
        filled = int(self.width * frac)
        full, empty = ("#", "-") if self._ascii else ("█", "░")
        bar = full * filled + empty * (self.width - filled)
        parts = [f"{self.label} [{bar}] {frac * 100:5.1f}%",
                 f"{self.count}/{self.total}"]
        for k, v in self._extra.items():
            parts.append(f"{k} {v}")
        elapsed = time.time() - self._start
        if elapsed >= 1.0:
            rate = self.count / elapsed
            parts.append(f"{rate:.1f} 文件/s")
            if rate > 0 and self.count < self.total:
                parts.append(f"剩余 {self._fmt_dur((self.total - self.count) / rate)}")
        return "  ".join(parts)

    def _draw(self):
        self._last_draw = time.time()
        line = self._render()
        try:
            if self.tty:
                pad = max(0, self._last_len - len(line))
                self.stream.write("\r" + line + " " * pad)
            else:
                self.stream.write(line + "\n")
            self.stream.flush()
        except UnicodeEncodeError:
            # 代码页不支持方块字符：退回 ASCII 重画一次
            self._ascii = True
            line = self._render()
            if self.tty:
                pad = max(0, self._last_len - len(line))
                self.stream.write("\r" + line + " " * pad)
            else:
                self.stream.write(line + "\n")
            self.stream.flush()
        except Exception:
            self.enabled = False
            return
        self._last_len = len(line)

    def close(self):
        """收尾：把最终状态定格成一行。不强行凑满 100%，如实反映进度。"""
        if not self.enabled or self._closed:
            return
        self._closed = True
        line = self._render()
        try:
            if self.tty:
                pad = max(0, self._last_len - len(line))
                self.stream.write("\r" + line + " " * pad + "\n")
            else:
                self.stream.write(line + "\n")
            self.stream.flush()
        except Exception:
            pass

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()
        return False


class Scanner:
    def __init__(self, save: Path, namespaces: set[str], baseline: dict,
                 current_remap: set[str] | None = None,
                 survived: set[str] | None = None,
                 progress=None):
        self.save = save
        self.namespaces = namespaces
        # 判据：迁移时被销毁的物品表（冻结的历史事实）
        self.destroyed_target = set(baseline.get("destroyed", {}).get("withTarget", []))
        self.destroyed_no_target = set(baseline.get("destroyed", {}).get("withoutTarget", []))
        self.legacy = set(baseline.get("legacyRemap", []))
        # 当前源码清单：仅作基线之外的兜底判断
        self.current_remap = current_remap or set()
        # 存档别名表：实证幸存（最高优先级）
        self.survived = survived or set()
        # 进度条工厂：(label, total) -> Progress
        self._progress = progress
        self.entries: list[dict] = []
        self.stats = ScanStats()
        # 精妙背包全局存储：uuid(4 ints) -> 内容 NBT
        self.backpack_store: dict[tuple, dict] = {}
        self.backpack_referenced: set[tuple] = set()
        self.player_names: dict[str, str] = {}

    # ---- 进度 ----

    def _bar(self, label: str, total: int) -> "Progress | None":
        """按需创建进度条；total 为 0 或未配置工厂时返回 None（调用处无需分支）。"""
        if not self._progress or total <= 0:
            return None
        return self._progress(label, total)

    def _hits(self) -> int:
        return len(self.entries)

    # ---- 判定 ----

    def wanted(self, item_id: str) -> bool:
        ns = item_id.split(":", 1)[0] if ":" in item_id else "minecraft"
        return ns in self.namespaces

    def status_of(self, item_id: str) -> tuple[str, str | None]:
        """判定一个 kubejs 物品是否需要补偿。

        判据是**迁移时被销毁的物品表**（migration-baseline.json），不是当前源码清单：
        事后补进清单的条目救不回已经销毁的物品，拿它当判据会把全部损失漏报成"无需处理"。

        三层优先级：
          1. 存档别名表有 `kubejs:X -> tothesky:X` ⇒ remap 确实执行过 ⇒ 幸存（实证）。
          2. X 在迁移时清单内 ⇒ 当时 remap 生效 ⇒ 幸存（由基线推断）。
          3. X 在"漏配被销毁"表内 ⇒ 被销毁：
               * 有同名替代品 ⇒ missing，可自动补偿到 tothesky:X
               * 无同名替代品 ⇒ no_target，需人工定夺
          4. 基线之外 ⇒ unknown，留待人工确认（可能是非本 mod 迁移范围的物品）。
        """
        path = item_id.split(":", 1)[1] if ":" in item_id else item_id
        if path in self.survived:
            return "already_remapped", f"tothesky:{path}"
        if path in self.legacy:
            return "already_remapped", f"tothesky:{path}"
        if path in self.destroyed_target:
            return "missing", f"tothesky:{path}"
        if path in self.destroyed_no_target:
            return "no_target", None
        if path in self.current_remap:
            # 基线未收录但当前清单有 —— 兜底，通常是基线生成后才新增的注册
            return "missing", f"tothesky:{path}"
        return "unknown", None

    # ---- 产出 ----

    def emit(self, stack: Tag, origin: dict, path: str, chain: list[dict],
             slot: int | None = None):
        """记录一个 ItemStack（若其 id 落在关注命名空间内）。"""
        item_id = stack_id(stack)
        if item_id == "minecraft:air" or not self.wanted(item_id):
            return
        status, remap_to = self.status_of(item_id)
        self.entries.append({
            "status": status,
            "remapTo": remap_to,
            "origin": origin,
            "path": path,
            "slot": slot,
            "chain": chain,
            "item": {
                "id": item_id,
                "count": stack_count(stack),
                "snbt": to_snbt(stack),
            },
        })

    # ---- 物品栈处理：记录自身 + 递归进入内嵌容器 ----

    def handle_stack(self, stack: Tag, origin: dict, path: str, chain: list[dict],
                     slot: int | None = None):
        if not is_item_stack(stack):
            return
        self.emit(stack, origin, path, chain, slot)

        item_id = stack_id(stack)
        tag = cget(stack, "tag")
        if tag is None:
            return
        inner_chain = chain + [{
            "type": origin.get("type"),
            "pos": origin.get("pos"),
            "blockEntity": origin.get("blockEntity"),
            "player": origin.get("player"),
            "itemId": item_id,
        }]

        # 潜影盒等：内容直接存在物品 NBT 里
        block_entity_tag = cget(tag, "BlockEntityTag")
        if block_entity_tag is not None:
            self.handle_container_items(
                cget(block_entity_tag, "Items"),
                self.child_origin(origin, "shulker_item", {"label": item_id}),
                f"{path}.tag.BlockEntityTag.Items", inner_chain)

        # 精妙背包：内容在全局文件里，按 contentsUuid 关联
        uuid = cget(tag, "contentsUuid")
        if uuid is not None:
            key = uuid_key(uuid)
            if key is not None:
                self.backpack_referenced.add(key)
                stored = self.backpack_store.get(key)
                if stored is not None:
                    self.handle_container_items(
                        stored,
                        self.child_origin(origin, "backpack_item", {"label": item_id}),
                        f"{path}.tag.contentsUuid->global", inner_chain)
                else:
                    # 背包内容不在本存档：记录缺口，便于人工核对
                    self.entries.append({
                        "status": "missing_content",
                        "remapTo": None,
                        "origin": self.child_origin(origin, "backpack_item", {"label": item_id}),
                        "path": f"{path}.tag.contentsUuid",
                        "slot": slot,
                        "chain": inner_chain,
                        "item": {"id": item_id, "count": stack_count(stack),
                                 "snbt": to_snbt(stack)},
                        "note": "contentsUuid 在全局文件中无对应内容（背包内容可能已丢失）",
                    })

        # AE2 存储元件：keys/amts 平行数组
        self.handle_ae2_cell(stack, origin, path, inner_chain)

    def handle_ae2_cell(self, stack: Tag, origin: dict, path: str, chain: list[dict]):
        tag = cget(stack, "tag")
        keys = as_seq(cget(tag, "keys"))
        if not keys:
            return
        amts = as_seq(cget(tag, "amts"))
        for idx, key in enumerate(keys):
            if key.tid != TAG_COMPOUND:
                continue
            key_id = as_str(key.value.get("id"))
            if not key_id or not self.wanted(key_id):
                continue
            amount = as_num(amts[idx]) if idx < len(amts) else None
            status, remap_to = self.status_of(key_id)
            self.entries.append({
                "status": status,
                "remapTo": remap_to,
                "origin": {
                    **origin,
                    "type": "ae2_cell",
                    "label": stack_id(stack),
                },
                "path": f"{path}.tag.keys[{idx}]",
                "slot": None,
                "chain": chain,
                "item": {
                    "id": key_id,
                    "count": int(amount) if amount is not None else None,
                    # AE2 键不是 ItemStack；给出最小可用 SNBT 以便模组统一处理
                    "snbt": f'{{id:"{key_id}",Count:1b}}',
                    "ae2": {
                        "keyIndex": idx,
                        "keyTag": to_snbt(key),
                        "amount": int(amount) if amount is not None else None,
                        "cellCount": as_num(cget(tag, "ic")),
                    },
                },
            })

    def child_origin(self, origin: dict, new_type: str, extra: dict | None = None) -> dict:
        o = dict(origin)
        o["type"] = new_type
        if extra:
            o.update(extra)
        return o

    def handle_container_items(self, items: Tag | None, origin: dict, path: str,
                               chain: list[dict]):
        """处理 `Items[]` 这类带 Slot 的列表。"""
        for i, stack in enumerate(as_list(items)):
            if not is_item_stack(stack):
                continue
            slot = as_num(cget(stack, "Slot"))
            slot = int(slot) if slot is not None else i
            self.handle_stack(stack, origin, f"{path}[{i}]", chain, slot)

    # ---- 各类来源 ----

    def scan_playerdata(self):
        pdir = self.save / "playerdata"
        if not pdir.is_dir():
            return
        files = [f for f in sorted(pdir.glob("*.dat")) if not f.name.endswith("_old")]
        bar = self._bar("扫描玩家数据", len(files))
        for f in files:
            try:
                root = NBTReader(decompress_file(f)).read_root()
            except Exception:
                self.stats.errors += 1
                if bar:
                    bar.advance()
                continue
            self.stats.files += 1
            uuid = f.stem
            name = self.player_names.get(uuid.replace("-", "").lower())
            base = {"uuid": uuid, "name": name}
            dim = as_str(cget(root, "Dimension")) or "minecraft:overworld"

            for key, otype in (("Inventory", "player_inventory"), ("EnderItems", "player_ender")):
                lst = as_list(cget(root, key))
                if not lst:
                    continue
                origin = {"type": otype, "dimension": dim, "pos": None,
                          "blockEntity": None, "player": base, "label": None}
                for i, stack in enumerate(lst):
                    if not is_item_stack(stack):
                        continue
                    slot = as_num(cget(stack, "Slot"))
                    slot = int(slot) if slot is not None else i
                    self.handle_stack(stack, origin, f"{key}[{i}]", [], slot)

            self.scan_curios(root, base, dim)
            if bar:
                bar.advance(命中=self._hits())
        if bar:
            bar.close()

    def scan_curios(self, root: Tag, player: dict, dim: str):
        caps = cget(root, "ForgeCaps")
        cur = cget(caps, "curios:inventory")
        for handler in as_list(cget(cur, "Curios")):
            if handler.tid != TAG_COMPOUND:
                continue
            ident = as_str(handler.value.get("Identifier")) or "?"
            stacks = cget(handler, "StacksHandler")
            for key in ("Stacks", "Cosmetics"):
                inner = cget(stacks, key)
                items = as_list(cget(inner, "Items"))
                for i, stack in enumerate(items):
                    if not is_item_stack(stack):
                        continue
                    slot = as_num(cget(stack, "Slot"))
                    slot = int(slot) if slot is not None else i
                    origin = {"type": "curios", "dimension": dim, "pos": None,
                              "blockEntity": None, "player": player,
                              "label": ident if key == "Stacks" else f"{ident}(cosmetic)"}
                    self.handle_stack(stack, origin,
                                      f'ForgeCaps.curios:inventory.Curios[{ident}].{key}.Items[{i}]',
                                      [], slot)

    def scan_world(self):
        """遍历所有维度的 region / entities 目录。

        进度以**区域文件**为单位：区块总数只有解压后才知道，而区域文件数是可预知的，
        且它就是耗时的主要来源（解压 + NBT 解析）。
        """
        plan = self._plan_world()
        bar = self._bar("扫描世界", sum(len(f) for _, _, f in plan))
        for folder, dim, files in plan:
            for mca in files:
                self.stats.regions += 1
                for cx, cz, raw in iter_region_chunks(mca):
                    self.stats.chunks += 1
                    try:
                        root = NBTReader(raw).read_root()
                    except Exception:
                        self.stats.errors += 1
                        continue
                    self.scan_chunk(root, dim, mca.name, cx, cz)
                if bar:
                    bar.advance(区块=self.stats.chunks, 命中=self._hits())
        if bar:
            bar.close()

    def scan_chunk(self, root: Tag, dim: str, region: str, cx: int, cz: int):
        # 方块实体
        for i, be in enumerate(as_list(cget(root, "block_entities"))):
            self.scan_block_entity(be, dim, region, cx, cz, i)
        # 实体
        for i, ent in enumerate(as_list(cget(root, "Entities"))):
            self.scan_entity(ent, dim, region, cx, cz, i)

    def scan_block_entity(self, be: Tag, dim: str, region: str, cx: int, cz: int, idx: int):
        if be.tid != TAG_COMPOUND:
            return
        be_id = as_str(be.value.get("id"))
        if not be_id:
            return
        x, y, z = (as_num(be.value.get(k)) for k in ("x", "y", "z"))
        pos = [int(x), int(y), int(z)] if None not in (x, y, z) else None

        origin = {"type": "block_entity", "dimension": dim, "pos": pos,
                  "blockEntity": be_id, "player": None, "label": None}
        base_path = f"block_entities[{idx}]"

        # Plonk 放置在地上的物品
        if be_id == "plonk:placed_items":
            origin["type"] = "placed_item"
            self.handle_container_items(be.value.get("Items"), origin,
                                        f"{base_path}.Items", [])
            return

        # 精妙背包（放置态）：backpackData 是背包物品，内容在全局文件
        if be_id == "sophisticatedbackpacks:backpack":
            origin["type"] = "backpack_block"
            data = be.value.get("backpackData")
            if is_item_stack(data):
                self.handle_stack(data, origin, f"{base_path}.backpackData", [])
            return

        # AE2 驱动器：inv.itemN
        inv = cget(be, "inv")
        if inv is not None and inv.tid == TAG_COMPOUND:
            origin["type"] = "ae2_drive"
            for k, stack in inv.value.items():
                if is_item_stack(stack):
                    self.handle_stack(stack, origin, f"{base_path}.inv.{k}", [])
            return

        # 通用容器：Items[]（箱子/木桶/潜影盒/布袋/抽屉/变体箱…）
        items = be.value.get("Items")
        if items is not None and items.tid == TAG_LIST:
            self.handle_container_items(items, origin, f"{base_path}.Items", [])
            return
        # 少数模组用 Inventory
        inv2 = be.value.get("Inventory")
        if inv2 is not None and inv2.tid == TAG_LIST:
            self.handle_container_items(inv2, origin, f"{base_path}.Inventory", [])

    def scan_entity(self, ent: Tag, dim: str, region: str, cx: int, cz: int, idx: int):
        if ent.tid != TAG_COMPOUND:
            return
        ent_id = as_str(ent.value.get("id"))
        if not ent_id:
            return
        pos_tag = as_list(ent.value.get("Pos"))
        pos = [round(as_num(p), 3) for p in pos_tag[:3]] if len(pos_tag) >= 3 else None
        uuid_tag = as_list(ent.value.get("UUID"))
        uuid = None
        if len(uuid_tag) == 4:
            words = [int(as_num(w) or 0) & 0xFFFFFFFF for w in uuid_tag]
            h = (words[0] << 96) | (words[1] << 64) | (words[2] << 32) | words[3]
            uuid = str(__import__("uuid").UUID(int=h))

        origin = {"type": "entity_container", "dimension": dim, "pos": pos,
                  "blockEntity": ent_id, "player": None, "label": uuid}
        base_path = f"Entities[{idx}]"

        handled = False
        # 掉落物 / 展示框 / 物品架：单个 Item
        single = ent.value.get("Item")
        if is_item_stack(single):
            origin["type"] = "entity_item"
            self.handle_stack(single, origin, f"{base_path}.Item", [])
            handled = True
        # 箱船 / 箱矿车 / 驴背包：Items[]
        items = ent.value.get("Items")
        if items is not None and items.tid == TAG_LIST:
            self.handle_container_items(items, origin, f"{base_path}.Items", [])
            handled = True
        if not handled and ent_id in (
            "minecraft:chest_boat", "minecraft:chest_raft",
            "minecraft:chest_minecart", "minecraft:hopper_minecart",
            "minecraft:donkey", "minecraft:mule", "minecraft:llama",
        ):
            self.entries.append({
                "status": "note_empty",
                "remapTo": None,
                "origin": origin,
                "path": base_path,
                "slot": None,
                "chain": [],
                "item": {"id": ent_id, "count": 1, "snbt": None},
                "note": "该容器类实体存在但未发现物品字段",
            })

    # ---- 全局数据文件（精妙背包等）----

    def scan_data_files(self):
        ddir = self.save / "data"
        if not ddir.is_dir():
            return
        files = sorted(ddir.glob("*.dat"))
        bar = self._bar("扫描全局数据", len(files))
        for f in files:
            try:
                root = NBTReader(decompress_file(f)).read_root()
            except Exception:
                self.stats.errors += 1
                if bar:
                    bar.advance()
                continue
            self.stats.files += 1
            if f.name == "sophisticatedbackpacks.dat":
                self.scan_backpack_store(root)
            if bar:
                bar.advance()
        if bar:
            bar.close()

    def scan_backpack_store(self, root: Tag):
        data = cget(root, "data")
        contents = as_list(cget(data, "backpackContents"))
        for entry in contents:
            if entry.tid != TAG_COMPOUND:
                continue
            key = uuid_key(entry.value.get("uuid"))
            inner = cget(entry, "contents")
            inv = cget(inner, "inventory")
            items = cget(inv, "Items")
            if key is not None:
                self.backpack_store[key] = items if items is not None else Tag(TAG_LIST, [])

    def scan_orphan_backpacks(self):
        """全局文件中没有被任何物品引用的背包内容（背包本身已消失）。"""
        for key, items in self.backpack_store.items():
            if key in self.backpack_referenced:
                continue
            origin = {"type": "backpack_storage_file", "dimension": None, "pos": None,
                      "blockEntity": None, "player": None,
                      "label": "uuid=" + ",".join(map(str, key))}
            self.handle_container_items(items, origin, f"data.backpackContents[{key}].Items", [])

    # ---- 闸门模式 ----

    def gate(self) -> dict:
        """快速统计：不解 NBT，只按字节找命名空间标记。"""
        counts: dict[str, int] = {ns + ":": 0 for ns in self.namespaces}
        markers = [(ns + ":").encode() for ns in self.namespaces]
        nchunks = 0

        regions = [mca for folder, _ in self.world_roots()
                   for mca in sorted(folder.glob("*.mca"))]
        dats = sorted(self.save.rglob("*.dat"))
        bar = self._bar("闸门检查", len(regions) + len(dats))
        hits = 0

        for mca in regions:
            for _, _, raw in iter_region_chunks(mca):
                nchunks += 1
                for ns, m in zip(self.namespaces, markers):
                    n = raw.count(m)
                    counts[ns + ":"] += n
                    hits += n
            if bar:
                bar.advance(区块=nchunks, 命中=hits)

        for f in dats:
            try:
                raw = decompress_file(f)
            except Exception:
                if bar:
                    bar.advance()
                continue
            for ns, m in zip(self.namespaces, markers):
                n = raw.count(m)
                counts[ns + ":"] += n
                hits += n
            if bar:
                bar.advance(命中=hits)

        if bar:
            bar.close()
        return {"chunks": nchunks, "counts": counts}

    def world_roots(self) -> list[tuple[Path, str]]:
        """所有维度的 region / entities 目录 —— 扫描与闸门共用同一份清单。"""
        roots: list[tuple[Path, str]] = []
        for sub_name in ("region", "entities"):
            if (self.save / sub_name).is_dir():
                roots.append((self.save / sub_name, "minecraft:overworld"))
        for holder in ("DIM-1", "DIM1"):
            for sub_name in ("region", "entities"):
                p = self.save / holder / sub_name
                if p.is_dir():
                    roots.append((p, _DIM_BY_FOLDER[holder]))
        dims = self.save / "dimensions"
        if dims.is_dir():
            for ns_dir in sorted(p for p in dims.iterdir() if p.is_dir()):
                for path_dir in sorted(p for p in ns_dir.iterdir() if p.is_dir()):
                    for sub_name in ("region", "entities"):
                        p = path_dir / sub_name
                        if p.is_dir():
                            roots.append((p, f"{ns_dir.name}:{path_dir.name}"))
        # 附加维度目录（exposure 截图、world-comment 等），规则同上
        for extra, dim in (("world-comment", "tothesky:world_comment"),):
            for sub_name in ("region", "entities"):
                p = self.save / extra / sub_name
                if p.is_dir():
                    roots.append((p, dim))
        return roots

    def _plan_world(self) -> list[tuple[Path, str, list[Path]]]:
        """世界扫描计划：(目录, 维度, 该目录下的 .mca 列表)。"""
        plan = []
        for folder, dim in self.world_roots():
            files = sorted(folder.glob("*.mca"))
            if files:
                plan.append((folder, dim, files))
        return plan


def uuid_key(tag: Tag | None) -> tuple | None:
    """contentsUuid / uuid 是 4 个 int 的 IntArray，归一化成元组用于配对。"""
    if tag is None:
        return None
    if tag.tid == TAG_INT_ARRAY and len(tag.value) == 4:
        return tuple(tag.value)
    if tag.tid == TAG_LIST and len(tag.value) == 4:
        nums = [as_num(t) for t in tag.value]
        if all(n is not None for n in nums):
            return tuple(int(n) for n in nums)
    return None


# --------------------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------------------


def collect_player_names(save: Path) -> dict[str, str]:
    """从 usercache.json 补齐 uuid->名字。

    服务端在存档根目录旁；客户端在 saves 的上一级（版本目录）。
    两侧 uuid 一律去掉连字符再比对，否则永远匹配不上。
    """
    names: dict[str, str] = {}
    candidates = [
        save.parent / "usercache.json",          # 服务端：存档根旁
        save.parent.parent / "usercache.json",   # 客户端：saves 的上一级
        save / "usercache.json",
    ]
    for cand in candidates:
        if not cand.is_file():
            continue
        try:
            for rec in json.loads(cand.read_text(encoding="utf-8")):
                u = str(rec.get("uuid", "")).replace("-", "").lower()
                if u:
                    names[u] = rec.get("name")
        except Exception:
            pass
    return names


def build_summary(entries: list[dict]) -> dict:
    by_item: dict[str, int] = {}
    by_container: dict[str, int] = {}
    by_status: dict[str, int] = {}
    for e in entries:
        st = e["status"]
        by_status[st] = by_status.get(st, 0) + 1
        if st in ("missing", "no_target"):
            by_item[e["item"]["id"]] = by_item.get(e["item"]["id"], 0) + 1
            ct = e["origin"].get("type") or "?"
            by_container[ct] = by_container.get(ct, 0) + 1
    return {
        "entries": len(entries),
        "byStatus": by_status,
        # 需要补偿的总数；其中 hasTarget 的有现成替代品，noTarget 的需要人工定夺
        "needsCompensation": by_status.get("missing", 0) + by_status.get("no_target", 0),
        "hasTarget": by_status.get("missing", 0),
        "noTarget": by_status.get("no_target", 0),
        "alreadyRemapped": by_status.get("already_remapped", 0),
        "unknown": by_status.get("unknown", 0),
        "byItem": dict(sorted(by_item.items(), key=lambda kv: -kv[1])),
        "byContainer": dict(sorted(by_container.items(), key=lambda kv: -kv[1])),
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="扫描 Minecraft 存档，提取需要回填的 kubejs 物品（ToTheSky 迁移修复用）")
    ap.add_argument("--save", required=True, type=Path, help="存档目录（含 level.dat）")
    ap.add_argument("--out", type=Path, help="输出 JSON 路径")
    ap.add_argument("--namespaces", default="kubejs", help="关注命名空间，逗号分隔（默认 kubejs）")
    ap.add_argument("--baseline", type=Path,
                    help="迁移基线 JSON（默认 migration-baseline.json）；判定是否被销毁的依据")
    ap.add_argument("--remap-source", type=Path,
                    help="MissingMappingEvents.java 路径（默认自动查找）；仅作基线之外的兜底")
    ap.add_argument("--target-world", type=Path,
                    help="迁移后的现世界存档；其别名表里已有的 remap 视为已幸存，不重复补偿")
    ap.add_argument("--missing-only", action="store_true",
                    help="输出中只保留需要补偿的条目（missing / no_target）")
    ap.add_argument("--pretty", action="store_true",
                    help="缩进输出（默认紧凑 JSON，条目多时体积差数倍）")
    ap.add_argument("--gate", action="store_true",
                    help="闸门模式：只统计命名空间出现次数，不解 NBT")
    ap.add_argument("--no-progress", action="store_true",
                    help="关闭进度条（默认 TTY 单行刷新；输出重定向时按 15s 间隔写行）")
    args = ap.parse_args(argv)

    save: Path = args.save
    if not (save / "level.dat").is_file():
        print(f"错误：{save} 不是有效的存档目录（缺少 level.dat）", file=sys.stderr)
        return 2

    namespaces = {s.strip() for s in args.namespaces.split(",") if s.strip()}

    def make_progress(label: str, total: int) -> Progress:
        return Progress(label, total, enabled=not args.no_progress)

    baseline_path = args.baseline or default_baseline()
    if not baseline_path or not baseline_path.is_file():
        print("错误：缺少迁移基线（migration-baseline.json）。\n"
              "      该文件列出迁移时被销毁的物品，是判定是否需补偿的依据。\n"
              "      用 --baseline 指定，或先由 tools/save-scanner 生成。", file=sys.stderr)
        return 2
    baseline = load_baseline(baseline_path)
    d_destroyed = baseline.get("destroyed", {})
    print(f"迁移基线：{baseline_path.name}"
          f"（漏配被销毁 {len(d_destroyed.get('withTarget', [])) + len(d_destroyed.get('withoutTarget', []))} 项"
          f"，迁移时清单 {len(baseline.get('legacyRemap', []))} 条）")

    remap_path = args.remap_source or default_remap_source()
    if remap_path and remap_path.is_file():
        current_remap = load_remap_set(remap_path)
    else:
        current_remap = set()
        print("提示：未找到当前源码清单，基线之外的物品将标记为 unknown", file=sys.stderr)

    # 别名表 = "已经换名存活"的实证。被扫存档自身若是迁移后的世界，其别名同样有效。
    survived = load_survived_aliases(save)
    if args.target_world:
        survived |= load_survived_aliases(args.target_world)
    if survived:
        print(f"别名表显示 {len(survived)} 个旧名已成功 remap（不参与补偿）")

    scanner = Scanner(save, namespaces, baseline, current_remap, survived,
                      progress=make_progress)
    # 进度条写 stderr，保持 stdout 干净（脚本化调用只关心结果行）
    if args.no_progress:
        print("进度条已关闭")

    if args.gate:
        t0 = time.time()
        result = scanner.gate()
        total = sum(result["counts"].values())
        print(f"闸门检查完成：区块 {result['chunks']}，耗时 {time.time() - t0:.1f}s")
        for ns, c in result["counts"].items():
            print(f"  {ns} 出现 {c} 次")
        print()
        if total == 0:
            print("结论：该存档不含任何待恢复物品 —— 备份晚于 KubeJS 卸载，方案终止。")
        else:
            print(f"结论：发现 {total} 处引用，值得继续完整提取。")
        return 0

    t0 = time.time()
    scanner.player_names = collect_player_names(save)

    scanner.scan_data_files()
    scanner.scan_playerdata()
    scanner.scan_world()
    scanner.scan_orphan_backpacks()

    entries = scanner.entries
    if args.missing_only:
        entries = [e for e in entries if e["status"] in ("missing", "no_target")]

    summary = build_summary(entries)
    doc = {
        "formatVersion": FORMAT_VERSION,
        "generator": f"tothesky-save-scanner/{VERSION}",
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "source": {
            "save": str(save),
            "regions": scanner.stats.regions,
            "chunks": scanner.stats.chunks,
            "dataFiles": scanner.stats.files,
            "parseErrors": scanner.stats.errors,
            "namespaces": sorted(namespaces),
            "baseline": str(baseline_path),
            "currentRemapSource": str(remap_path) if remap_path else None,
            "targetWorld": str(args.target_world) if args.target_world else None,
            "survivedAliases": len(survived),
        },
        "summary": summary,
        "entries": entries,
    }

    out = args.out or (save / "tothesky_restore.json")
    if args.pretty:
        text = json.dumps(doc, ensure_ascii=False, indent=1)
    else:
        text = json.dumps(doc, ensure_ascii=False, separators=(",", ":"))
    out.write_text(text, encoding="utf-8")

    dt = time.time() - t0
    print()
    print(f"完成：{dt:.1f}s，区块 {scanner.stats.chunks}，"
          f"解析错误 {scanner.stats.errors}")
    print(f"条目总数 {summary['entries']}")
    print(f"  需补偿 {summary['needsCompensation']}"
          f"（有替代品 {summary['hasTarget']}，无替代品需人工定夺 {summary['noTarget']}）")
    print(f"  已 remap 幸存（跳过）{summary['alreadyRemapped']}")
    if summary["byItem"]:
        print("待补偿物品：")
        for i, c in summary["byItem"].items():
            print(f"   {c:>4}  {i}")
    if summary["byContainer"]:
        print("来源分布：")
        for t, c in summary["byContainer"].items():
            print(f"   {c:>4}  {t}")
    print(f"\n输出：{out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
