# 服务器测试问题清单（ToTheSky 1.20.1）

> 记录日期：2026-09-09。来源：`docs/serverTest_todo.md` 全量分组测完后的**仅记录**问题汇总。
> 原则：**只记录，不修复**。修复前问题保留，修复后逐项在此勾销。

---

## A. 待修复问题（仅记录）

### A1. 配方/数据缺失
| # | 条目 | 现象 |
|---|------|------|
| D1 | `activated_witch_factor` 活化的魔女因子 | 无配方 |
| D2 | `bomb_cod_burger` 劲爆鳕鱼堡 | 无配方 |
| D3 | `fried_cod` 油炸鳕鱼 | 缺 tag，现仅 `forge:faw_fishes/cod` |
| D4 | `bug_soup` 秘封洋葱绿叶肥虫汤 | 无配方 |
| D7 | `packed_colors` 包装颜料 | 无法合成（按 宣纸 + 1~4 凿刻染料 配方） |
| D14 | `lago_de_texcoco` 特斯科科湖 | 辣椒 tag 误用 `forge:vegetables/pepper`，应 `chilipepper` |
| D15 | `dangerous_party` 危险派对 | 无配方 |

### A2. 本地化
| # | 条目 | 现象 |
|---|------|------|
| D5 | 四种流体 `bean_sause` / `bean_oil` / `soy_sause` / `ghast_tear` | 翻译为空键名（两个下界溶液正常） |

### A3. 逻辑/注册异常
| # | 条目 | 现象 |
|---|------|------|
| D8 | `silent_midnight` 寂静午夜 | 悬停 tooltip 崩溃：recipe 引用缺失 `create_confectionery:rest`，kk `EffectStack.get()` NPE。加回依赖后可跑，但「缺失时」保护疑未覆盖 tooltip 路径 |
| D9 | 鸡尾酒方块 | 潜行 + 手持鸡尾酒右键坚实方块顶面**无法放置** |
| D10 | `burger` 汉堡 | 渲染模式有问题（细节待补） |
| D11 | `nanako_sculpture` 纳奈子雕像 | 无四向旋转 |
| D13 | `mechanical_chisel_table` 动力雕刻台 | 无功能、无 GUI |
| D18 | 行商召唤 | 召唤后标记位待删除 |
| D19 | `diamond_sickle` / `netherite_sickle` 镰刀 | 游戏中不存在（kaleidoscope_cookery 已加载仍未注册） |
| D20 | 原版镐的挖掘等级（玩家反馈） | 石/铁/钻石/下界合金镐挖铁矿石、铜矿石、青金石矿、铁块、铜块（`minecraft:needs_stone_tool` 全部 43 个方块）无掉落；镰刀 Tier 注册进 `TierSortingRegistry` 且 `getTag()` 填成 `needs_stone_tool`，污染了所有比它低的工具的挖掘判定 |

### A4. 待删/待改
| # | 条目 | 现象 |
|---|------|------|
| N1 | `hemostix` 采血套装 | 需删除 |
| N2 | `harvest_the_night` 收割黑夜 | 应以握工具方式手持（当前为物品姿态） |

---

## B. 已撤销（非缺陷）
- ~~D12 鞭炮无声~~ —— createbigcannons 依赖补齐后正常，非缺陷。

---

## C. 跳过 / 阻塞未测项（修复后补测）
- **§0 存档迁移** —— 用户明确跳过
- **§5 乐器** —— 用户明确跳过
- **§6.2.3–6.2.6**（叠杯 / 取回 / 喝掉 / 破坏掉落）—— 阻塞，依赖 **D9 修复**后重测
- **§10.1 / §10.2**（售货机 / 扭蛋机）—— 跳过（开发环境无法多人测试）
- **§6.7**（动力雕刻台完整功能）—— 因 D13 跳过
- **§13.4 魔法照片** —— 用户跳过测试

---

## 修复进度

| 日期 | 修复项 | 结果 | 备注 |
|------|--------|------|------|
| 2026-09-09 | D1 | 已修 | `activated_witch_factor_from_charging.json` 改为 CA 实际格式（`input`/`result`/`maxChargeRate`，对照 jar 内 `charging/channeling.json` 验证） |
| 2026-09-09 | D2 | 已修 | `bomb_cod_burger_from_shapeless.json` 辣椒 `mynethersdelight:bullet_pepper` → `nethersdelight:propelpearl`（与 kjs 一致） |
| 2026-09-09 | D3 | 已修 | `fried_cod_from_mixing.json` tag → `forge:raw_fishes/cod`（FarmersDelight jar 内置该 tag，含 `minecraft:cod`） |
| 2026-09-09 | D4 | 已修 | `bug_soup_from_shaped.json`：E → `#forge:crops/chilipepper`、O → `#forge:crops/onion`（FD 提供）；新建 `data/forge/tags/items/crops/chilipepper.json`（`chinjufumod:item_crop_chilipepper`） |
| 2026-09-09 | D5 | 已修 | 两 lang 的 `fluid.tothesky.*` → `fluid_type.tothesky.*`（Forge FluidType 描述键，与正常的下界溶液一致） |
| 2026-09-09 | D7 | 已修 | ① `chisel_dye.json` 误写 `#minecraft:dyes` → `minecraft:dyes`（tag 嵌套不带 #）；② `packed_colors` 序列化器从 `PackedColorsRecipe` 静态字段移到 `ModRecipes.PACKED_COLORS`——原写法类不加载则永不注册，配方类型未知被跳过 |
| 2026-09-09 | D8 | 已修 | `silent_midnight.json` 包 `forge:conditional`（`forge:mod_loaded create_confectionery`）：cc 缺失时配方不加载，kk tooltip 无效果可解析，从根上避免 `EffectStack.get()` NPE |
| 2026-09-09 | D9 | 已修 | `ModBlocks.kk()` 漏 `cocktails/` 前缀：kk 鸡尾酒真实 id 为 `kitchenkarrot:cocktails/<name>`，导致 `drinkBlockFor` 永不命中 |
| 2026-09-09 | D10 | 已修 | `models/block/burger.json` 加 `"render_type": "minecraft:cutout"`（kjs `defaultCutout()` 等价） |
| 2026-09-09 | D11 | 已修 | `blockstates/nanako_sculpture.json` 补 facing 四向变体；模型加 cutout |
| 2026-09-09 | D13 | 跳过（需决策） | 无 KubeJS 参考，§11 规格只定自动化交互（FE+管道投料）。逻辑核对：ultramarine `ChiselTableRecipe.matches` 槽位布局（0 材料/1 模板/2-5 染料）与 BE 的 `SimpleContainer(6)` 一致，自动雕刻链路本身成立；缺的是玩家侧操作面——GUI 需美术贴图，或改用「手持物品直接投料」交互。两种方案取向不同，待你拍板 |
| 2026-09-09 | D14 | 已修 | `lago_de_texcoco.json` tag → `forge:vegetables/chilipepper`；新建同名 tag 文件 |
| 2026-09-09 | D15 | 部分（待复测） | 静态审计未发现加载阻断点（JSON 有效、原料均存在、list.json/lang/模型齐全）。已补齐与 kjs 参考的差异：effect 加 `tothesky:hot_potato`。疑似当时受 D8 缺依赖环境牵连，复测确认 |
| 2026-09-09 | D18 | 跳过（待澄清） | Java 移植与 kjs 脚本逐行等价，双方均无「标记位」写入/清理；`ultramarine:travelling_merchant` 继承 `WanderingTrader`，`create()` 生成时 `despawnDelay=0` 不会自然消失，48000t 由 `DelayedTasks` 兜底。「标记位」具体所指不明，需你说明现象 |
| 2026-09-09 | D19 | 已修 | `mods.toml` 补 `kaleidoscope_cookery` 可选依赖 `ordering="AFTER"`：此前构造顺序不定，`ModList.isLoaded` 在 kc 先构造时为 false，`KC_ITEMS` 未挂总线 |
| 2026-09-09 | N1 | 已修 | 删除 `hemostix`：注册、`MissingMappingEvents` 映射项、两 lang 键、模型、贴图（plus 配方不引用它，已核） |
| 2026-09-09 | N2 | 已修 | `harvest_the_night.json` parent → `minecraft:item/handheld` |
| 2026-09-24 | D20 | 已修 | `ModKcItems` 去掉 `TierSortingRegistry.registerTier`（与 kjs 时代一致，只 new ForgeTier）。Forge 的判定是「方块落在**高于**本工具的 Tier 的 `getTag()` 里 → 挖不动」，而镰刀 Tier 被排在钻石之后、`getTag()` 又写成 `minecraft:needs_stone_tool`，于是石/铁/钻石/下界合金镐对那 43 个方块（铁/铜/青金石矿与铁块铜块等）全部判成无掉落。复现与验证：探针直接调用真实 `TierSortingRegistry.isCorrectTierForDrops`（tag 数据取自 1.20.1 官方服务端 jar），注册时 stone/iron/diamond 镐 iron_ore=false，去掉注册后恢复 true |
