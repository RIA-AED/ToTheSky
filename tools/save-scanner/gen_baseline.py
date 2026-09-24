#!/usr/bin/env python3
"""生成 migration-baseline.json —— 迁移基线的可复现来源。

基线回答一个问题：**KubeJS 迁移时，哪些物品因为 remap 清单漏配而被销毁？**

判据链（与会话初期分析逐字一致）：

    kjs 注册名（RiaFst 参考源，剥注释）
      − 迁移时清单（git HEAD 的 MissingMappingEvents）
      = 迁移时未被 remap 的物品  → 全部被销毁
          ├─ tothesky 已同名注册 → 有替代品 → 可自动补偿（missing）
          └─ tothesky 未注册     → 无替代品 → 需人工定夺（no_target）

**为什么必须用 git HEAD 的清单，而不是工作区当前版本**：
事后往 MIGRATED_ITEMS 里补条目，只能防未来的损失，救不回已销毁的物品。
若拿补过的清单当判据，这部分损失会被整片漏报成"无需处理"。

用法：
    python gen_baseline.py [--repo <模组仓库>] [--kjs <RiaFst kubejs 目录>] [--ref HEAD]

产物覆盖同目录的 migration-baseline.json。
仅在需要重建基线时运行；日常扫描不需要它。
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

DEFAULT_REPO = Path(__file__).resolve().parents[2]
DEFAULT_KJS = Path(
    r"D:\Minecraft\Client\.minecraft\versions\RiaFst\kubejs\startup_scripts")
# 只有这三张清单决定方块/物品是否被 remap（流体/效果/音效不涉及物品栏）
REMAP_LISTS = ("MIGRATED_BLOCKS", "MIGRATED_ITEMS", "MIGRATED_BUCKETS")

JAVA_LIST = re.compile(
    r"private static final List<String> (\w+)\s*=\s*List\.of\((.*?)\);", re.S)


def strip_js_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"(?m)^\s*//.*$", "", text)
    return re.sub(r"(?m)//.*$", "", text)


def kjs_registrations(kjs_dir: Path) -> set[str]:
    """从 RiaFst 的 startup_scripts 提取会进入物品栏的 kjs 物品名。

    kjs 方块默认自带 BlockItem（`.noItem()` 才没有），流体自带 `_bucket`。
    """
    if not kjs_dir.is_dir():
        raise SystemExit(f"KubeJS 参考源不存在：{kjs_dir}")
    items: set[str] = set()
    for f in sorted(kjs_dir.rglob("*.js")):
        text = strip_js_comments(f.read_text(encoding="utf-8", errors="replace"))
        parts = re.split(r"StartupEvents\.registry\(\s*['\"](\w+)['\"]", text)
        for i in range(1, len(parts), 2):
            rtype, body = parts[i], parts[i + 1]
            for chunk in re.split(r"(?=event\.create\()", body):
                m = re.match(r"event\.create\(\s*['\"]([^'\"]+)['\"]", chunk)
                if not m:
                    continue
                ident = m.group(1)
                if ":" in ident:
                    # 显式命名空间（kaleidoscope_cookery 等）不属本 mod 的迁移范围
                    continue
                if rtype == "item":
                    items.add(ident)
                elif rtype == "block" and ".noItem(" not in chunk:
                    items.add(ident)
                elif rtype == "fluid":
                    items.add(ident + "_bucket")
    return items


def tothesky_registrations(repo: Path) -> set[str]:
    """从注册类提取 tothesky 实际注册的物品/方块名（含工厂方法产生的）。"""
    reg = repo / "src/main/java/com/fst/tothesky/registry"
    names: set[str] = set()
    fluids = (reg / "ModFluids.java").read_text(encoding="utf-8")
    trio = set(re.findall(r'registerTrio\(\s*"([^"]+)"', fluids))
    sources = {
        reg / "ModItems.java": [
            r'(?:ITEMS|BUCKETS)\.register\(\s*"([^"]+)"',
            r'\bsimple\(\s*"([^"]+)"',
        ],
        reg / "ModBlocks.java": [
            r'BLOCKS\.register\(\s*"([^"]+)"',
            r'(?:martini|hurricane|oldFashioned)\(\s*"([^"]+)"',
        ],
        reg / "ModFluids.java": [
            r'(?:FLUIDS|BUCKETS)\.register\(\s*"([^"]+)"',
        ],
        reg / "ModKcItems.java": [
            r'KC_ITEMS\.register\(\s*"([^"]+)"',
        ],
    }
    for path, pats in sources.items():
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for p in pats:
            names.update(re.findall(p, text))
    names.update(t + "_bucket" for t in trio)
    names.update(re.findall(r'FLUIDS\.register\(\s*"([^"]+)"', fluids))
    return names


def legacy_remap(repo: Path, ref: str) -> tuple[set[str], str]:
    """取 `ref` 版本 MissingMappingEvents.java 的清单 —— 迁移时的清单状态。"""
    rel = "src/main/java/com/fst/tothesky/event/MissingMappingEvents.java"
    try:
        text = subprocess.run(
            ["git", "show", f"{ref}:{rel}"], cwd=repo,
            capture_output=True, text=True, encoding="utf-8", check=True).stdout
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        raise SystemExit(f"无法从 git 读取 {ref}:{rel} —— {exc}")
    names: set[str] = set()
    for name, body in JAVA_LIST.findall(text):
        if name in REMAP_LISTS:
            names.update(re.findall(r'"([^"]+)"', re.sub(r"//[^\n]*", "", body)))
    return names, f"git {ref}:{rel}"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", type=Path, default=DEFAULT_REPO, help="模组仓库根目录")
    ap.add_argument("--kjs", type=Path, default=DEFAULT_KJS, help="RiaFst kubejs 目录")
    ap.add_argument("--ref", default="HEAD", help="迁移时清单所在的 git 版本（默认 HEAD）")
    ap.add_argument("--out", type=Path, help="输出路径（默认同目录 migration-baseline.json）")
    args = ap.parse_args(argv)

    kjs = kjs_registrations(args.kjs)
    legacy, legacy_src = legacy_remap(args.repo, args.ref)
    registered = tothesky_registrations(args.repo)

    destroyed = sorted(kjs - legacy)
    with_target = sorted(i for i in destroyed if i in registered)
    without_target = sorted(i for i in destroyed if i not in registered)

    print(f"kjs 注册物品        {len(kjs)}")
    print(f"迁移时清单(去重)    {len(legacy)}")
    print(f"迁移时被销毁        {len(destroyed)}")
    print(f"  ├─ 有同名替代品   {len(with_target)}")
    print(f"  └─ 无同名替代品   {len(without_target)}")

    doc = {
        "_comment": "迁移基线：KubeJS 迁移时因 remap 清单漏配而被销毁的物品。此文件冻结，"
                    "不随源码演进——事后补清单救不回已销毁的物品。由 gen_baseline.py 生成。",
        "legacyRemapSource": legacy_src,
        "kjsReference": str(args.kjs),
        "legacyRemap": sorted(legacy),
        "destroyed": {
            "withTarget": with_target,
            "withoutTarget": without_target,
        },
    }
    out = args.out or (Path(__file__).resolve().parent / "migration-baseline.json")
    out.write_text(json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\n已写入 {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
