# 服务器测试清单（ToTheSky 1.20.1 全量注册回归）

> 生成日期：2026-09-09。逐项核对 `src/main/java/com/fst/tothesky/` 当前源码（ModItems / ModBlocks / ModFluids / ModEffects / 全部事件与方块实体），覆盖模组新增/注册的**全部物品、方块、状态效果、流体与逻辑**。
> **入口指令**：`gradlew runClient`（或把 `build/libs/tothesky-1.0.0.jar` 放进 RiaFST 4 实例 `mods/` → 启动）。Forge 1.20.1 / Java 17 / Create 6.0.8。
> 游戏内测试由用户手动进行——按下列分组逐项打勾。**本清单为验收参考，阈值/文案以源码注释为准。**

---

## 0. 存档迁移与 kubejs 兼容（MissingMappings）

- [ ] **启动日志无 FAIL 级 MissingMappings**：搜日志 `映射` / `Missing mapping`，kubejs:* 条目应显示 `映射 kubejs:xxx -> tothesky:xxx`（INFO 级）。覆盖方块/物品/流体/流体桶/状态效果/音效（`MissingMappingEvents`）；方块实体类型不走该事件，走 `RegistryAliasEvents` 的注册表别名
- [ ] **饺子（世界 + 物品栏 NBT 不丢）**：
  - 物品栏/箱子里的 `kubejs:raw_dumpling`（带 filling/author）、`cooked_dumpling`、`dumpling_wrapper`、`raw_dumpling_plate`、`cooked_dumpling_plate` 变 `tothesky:` 同名物品，馅料/厨师/自定义名/lore 原样保留
  - 世界里已放置的一盘熟饺子（`kubejs:cooked_dumpling_plate`）显示为 tothesky 方块，`bite`/`facing` 保持
  - 通过 厨锅/森罗汤锅 的脚本流程（`dumpling_making.js`）走一遍包制→煮→放置→取食，确认注册可用
- [ ] **一盘熟饺子取食（`CookedDumplingPlateBlock.use`）**：右键逐个取出，每次 1 只且 `bite` +1，模型随 {bite} 显示剩余数量；第 8 只取完后 `bite=8`，再右键返还 1 个碗并移除方块；手持另一盘饺子右键时不取食、正常放置；副手右键不触发
- [ ] **吃熟饺子按馅料生效（`CookedDumplingItem`）**：
  - 可食用馅料（苹果/面包/金苹果/甜菜汤等）：玩家真的吃下该馅料——食物值按馅料增加，金苹果给吸收+再生、腐肉给饥饿，甜菜汤返还碗，紫颂果等自定义 `finishUsingItem` 逻辑同样生效
  - 不可食用馅料（钻石等）：原样给到手中（背包满则掉落）
  - 带馅的饺子吃下后仍按旧 kjs 的 `dumpling_making.js` 包制流程得到的 NBT 生效
- [ ] **已放置方块保留**：旧存档里的售货机/扭蛋机/披萨/拉面/酒坊机器/鸡尾酒杯，外观与位置完好（同名 remap 生效）
- [ ] **披萨阶段方块**：存档里 `pizza_margarita2/3/4`、`pork_pizza2/3/4`、`apple_pizza2/3/4`（9 个阶段方块）显示为对应缺角模型；右键给切片并进入下一阶段（末阶段变空气）
- [ ] **售货机/扭蛋机内容物**：打开旧存档的售货机，商品栏位、owner、价格（price1.price2）与已售数据还在
- [ ] **物品栏/箱子里的 kubejs 物品**：全部变成 tothesky 同名物品（豆腐系列/鳕鱼堡/切片披萨/乐器/金币/三角片等）
- [ ] **流体桶**：旧存档的下界溶液/不稳定下界溶液/豆浆/酱油/大豆油/恶魂之泪桶是否保留
- [ ] **药水效果残留**：受击鼓传花/死亡回溯/绿玩/谵妄影响的玩家重新登录，效果图标不报错
- [ ] **未迁移条目 FAIL 提示**：存档含未迁移注册（`seat`/`player_seat` 实体、`event_item_1~5` FPS 物品、`rocket` 实体、部分酿酒/服务器运营条目）时 Forge 打 FAIL——确认属预期（见 `NOT_MIGRATED.md`）
- [ ] **新世界无回归**：新建世界，确认以上所有注册物仍能正常创造模式获取与使用

## 1. 注册内容回归（新世界即可测）

- [ ] 创造标签页 `tothesky:main`：图标为晴天鳕鱼；所有 `ModItems` 条目 + 6 个流体桶均可获取（**不含** KC 镰刀 / `firing_multiple_fireworks` 无物品方块）
- [ ] 全部物品/方块图标与本地化名正确（中文 `zh_cn.json`，无 `translation key` 字面量）
- [ ] 六个流体桶：桶装取放、薄层渲染正确（见 §9 色值）
- [ ] 物品栏 tooltip 行数正确（各 TooltipItem / TooltipBlockItem 的灰色说明）
- [ ] 索拉里斯勋章 `solaris0/1/2`、四色魔石 `blue/red/yellow/green_magic_stone`、`roller_ticket` 可获取

## 2. 食物物品行为（食用触发）

- [ ] **巧克力意面** `pasta_with_chocolate`：+12 饥/+1.0 饱；2% 厄运 30 秒(600t)；75% 恶心 10 秒(200t, II 级)
- [ ] **焦糖鳕鱼羹** `caramel_cod_soup`：碗装食物吃完返碗；吃后自伤 2 点；若残血 ≤2 直接致命并全服广播「摄入焦糖鳕鱼羹过多而死」；否则 actionbar「是错觉吗？似乎胃里有什么蹦跳了一下」
- [ ] **健胃消食片** `digestion_pellow`：0 饥饿可随时吃；饥饿 300t amp79
- [ ] **鱿鱼狂欢节** `squid_festival`：+12 饥/+1.0 饱；恶心 200t amp5；吃完返碗；非潜行吃/潜行放切换
- [ ] **深海鳕鱼堡** `cod_burger`：+12 饥/+1.0 饱
- [ ] **油炸鳕鱼** `fried_cod`：+8 饥/+1.0 饱
- [ ] **切制奶酪** `cut_cheese`：+4 饥/+1.0 饱
- [ ] **切片玛格丽特披萨** `sliced_pizza_margarita`：+4 饥/+1.0 饱；农夫乐事「滋养」1200t（FD 缺失时无效果）
- [ ] **切片猪肉碎披萨** `sliced_pork_pizza`：+5 饥/+1.2 饱；滋养 1800t
- [ ] **切片苹果披萨** `sliced_apple_pizza`：+5 饥/+1.0 饱；滋养 1300t；2% 厄运 600t
- [ ] **幻翼虾仁** `phantom_shrimp`：+7 饥/+1.5 饱；发光 60 秒(1200t)；每隔 1 秒头顶放一发礼花共 4 发（红/橙/黄/青绿）
- [ ] **三角粥** `delta_porridge`：饮用动画；+9 饥/+0.8 饱；返碗；物品冷却 60 秒；给绿玩+移速 II+力量+抗性各 1200t
- [ ] **饮品659** `drink659`：饮用/可放置；+2 饥/+1.5 饱；记录当前坐标为死亡回溯点；给予死亡回溯 6000t（可叠加时长）
- [ ] **晴天鳕鱼** `sunshine_cod`：+5 饥/+1.5 饱；在有天空维度的世界食用后雨过天晴（12000~180000t），全服广播「…食用了晴天鳕鱼，善哉，天公作美！」
- [ ] **温泉蛋牛肉盖饭** `beef_over_rice`：+10 饥/+0.6 饱；无特殊效果
- [ ] **劲爆鳕鱼堡** `bomb_cod_burger`：+14 饥/+14 饱；吃下后在头顶一格生成强度 4.0 真实爆炸（不破坏方块，但有声音/粒子/击退；**吃堡玩家不受伤**）
- [ ] **秘封洋葱绿叶肥虫汤** `bug_soup`：+4 饥/+0.2 饱；挖掘疲劳 600t、移速 1200t；谵妄 2400t（可叠加）
- [ ] **麻婆豆腐** `spicy_bean_curd`：+8 饥/+1.0 饱；滋养 600t；返碗
- [ ] **浆果麻婆豆腐** `berry_bean_curd`：+10 饥/+1.0 饱；滋养 600t；返碗；**20% 概率招来 5 道雷电劈中玩家**
- [ ] **甜豆花** `sweet_bean_curd`：+8 饥/+1.0 饱；返碗；可放置
- [ ] **咸豆腐脑** `salty_bean_curd`：+8 饥/+1.0 饱；返碗；可放置
- [ ] **豆腐** `bean_curd`：0 功能（食材/合成中间品）
- [ ] **小块豆腐** `cut_bean_curd`：+3 饥/+0.0 饱

## 3. 食材/产线中间品（无交互，仅合成参与）

- [ ] 下界合金产线：`impure_alloy_base` / `raw_alloy_base` / `incomplete_netherite_ingot` / `witch_factor` / `activated_witch_factor`（可放置/可合成，数值与 kjs 一致）
- [ ] 钻石产线：`diamond_core` / `uncomplete_diamond`
- [ ] 图腾/石墨产线：`emerald_nugget` / `raw_totem` / `incomplete_totem` / `fiber_mixture` / `frother_mixture` / `he_graphite` / `small_crystal` / `faded_small_crystal`（`incomplete_tortilla` 已改为唱片，见第 4 节）
- [ ] 经济系统：`delta_coin`（10Δ）/ `delta_coin_chip`（1Δ）/ `delta_dust` / `delta_porridge` 关联
- [ ] 披萨食材：`cheese` / `pizza_base` / `raw_pizza_margarita` / `raw_pork_pizza` / `raw_apple_pizza` / `raw_sunshine_cod`
- [ ] 其他：`soy_sause_bottle` / `soy_bean_oil`（酱油瓶/大豆油瓶，作烹饪油/食材）
- [ ] 酿酒占位：`wine_bottle`（0 饥饿可吃，无逻辑）/ `incomplete_wine_bottle`（无逻辑占位）

## 4. 工具/医疗/功能道具

- [ ] **采血套装** `hemostix`：无功能占位（普通物品，堆叠 16）
- [ ] **采血套装plus** `hemostix_plus`：主手右键；血量 >5 → 给 1 血瓶、自伤 5、actionbar「§c你感到血正在流出...」、冷却 30t、耐久 -1（耐久 13）；血量 ≤5 仅提示「§c你不能再抽血了」
- [ ] **收割黑夜** `harvest_the_night`：主手右键；end_rod 粒子 400 个 + trident.return 音效；肃清 150 格内 Size:0 的普通幻翼（传送到玩家上方 4 格后击杀）；存在非 0 Size 幻翼时提示「无法下手」；冷却 300t；耐久 200；恒有附魔光效
- [ ] **竹蜻蜓** `copter`：主手右键；漂浮 III 3 秒（60t, amp4）+ 云粒子 200 个；冷却 300t；耐久 20；望远镜使用动画
- [ ] **机械手润滑剂** `deployer_lubricant`：主手右键 `create:deployer`（无耐久有逻辑）；写公共 Owner NBT、播放 `create:slime_added`、冷却 10t、耐久 -1（耐久 100）
- [ ] **血瓶** `blood_bottle`：堆叠 1、合成余物为玻璃瓶；下界溶液原料
- [ ] **包装颜料** `packed_colors`：NBT StoredColors 记录染料列表；潜行右键拆包返还全部染料（播放拾取音）；tooltip 列出内含颜料
- [ ] **遗忘之露** `dew_of_oblivion`：对驯服生物右键；是主人 → 解绑（setOwnerUUID null、取消驯服、16 点附魔粒子 + 附魔桌音效 + actionbar「它忘记了一切……」、消耗 1）；非主人红字「它的主人好像不是你呢……」
- [ ] **刻痕玉米饼（唱片）** `incomplete_tortilla`：右键唱片机 → 播放 "Never gonna give you up"（stream 音轨，约 70.8 s 后自动停止）；物品提示第二行为灰色 `Never gonna give you up - Rick Astley`；比较器输出 1；漏斗可插入（物品已加入 `minecraft:music_discs` 标签，标签缺失时唱片机会拒收）

## 5. 乐器系统

- [ ] **吉他 / 电子琴 / 电子鼓机** `guitar`/`piano`/`drum_808`：主手右键/左键切换 5 种模式（独奏/合奏/合唱），演奏时长与音效正常（吉他音 `tothesky:guitar_sound` 有声音，非静音）
- [ ] **乐器拨弦音效**：`guitar_sound` 生效；鼓机 3-7 音回退到吉他音
- [ ] **空白乐谱** `empty_music_sheet`：主手空白乐谱 + 副手成书右键 → 生成乐谱物品（NBT allNodes）
- [ ] **乐谱** `music_sheet`：主手右键 → 学习歌曲写入 `config/musicSheets/<玩家>/`，actionbar 确认；物品恒有附魔光效

## 6. 方块行为

### 6.1 食品方块
- [ ] **拉面** `ramen`：放置时朝向玩家（新加 FACING）；右键吃一口（+4 饥 +4 饱 + 打嗝声），前四口吃食、第五口返还 1 碗并移除方块；破坏不掉落
- [ ] **整张披萨** `pizza_margarita`/`pork_pizza`/`apple_pizza`：非潜行右键给 1 片切片，SLICES 0→3；取完第 4 片消失；只有未动过的整张掉自身；潜行右键 PASS
- [ ] **披萨阶段方块** `pizza_*_2/3/4`（9 个）：非潜行右键给切片并进入下一阶段，末阶段变空气（存档兼容，无物品不掉落）
- [ ] **可放置食物方块** `salty_bean_curd` / `sweet_bean_curd` / `phantom_shrimp` / `drink659` / `sunshine_cod` / `beef_over_rice` / `squid_festival`：非潜行右键吃（32t EAT）、潜行右键放方块；方块需要下方支撑，无支撑自动弹出（`canSurvive`）

### 6.2 鸡尾酒杯方块（空杯 + 12 种酒）
- [ ] **三空杯** `martini_glass` / `hurricane_glass` / `old_fashioned_glass`：纯装饰，无物品形态，破坏不掉落；置于对应酒杯下方配对
- [ ] **12 种鸡尾酒放置方块**（马天尼杯：七月二十一/傲娇女主/甜莓马天尼/桦树伏特加；飓风杯：红蜥蜴/二次猜想/萤火/流星；古典杯：暮色/杰克故事/上海海滩/节肢克星）
- [ ] 放置：潜行 + 手持对应鸡尾酒右键坚实方块**顶面** → 放置（朝向玩家），消耗 1 瓶
- [ ] 叠杯：手持同种鸡尾酒右键已放酒杯 → 叠一份至杯型上限（马天尼 2 / 飓风 3 / 古典 4）
- [ ] 取回：潜行空手右键 → 取回 1 瓶；取完最后一份整杯消失（不留空杯）
- [ ] 喝掉：空手右键最后一份（stacks=1）→ 直接喝掉（触发 kk 鸡尾酒 buff/效果）+ 打嗝声，留下对应空杯
- [ ] 破坏掉落：非创造手动破坏 → 按当前份数掉落鸡尾酒

### 6.3 售货机/扭蛋机方块结构
- [ ] 放置朝向：机器放置时朝向玩家（FACING）
- [ ] **售货机** `seller`：见 §10.1
- [ ] **扭蛋机** `roller`：见 §10.2

### 6.4 酿酒方块（仅形态，逻辑不迁移）
- [ ] **蒸馏器/发酵罐/陈酿罐/标签机** `distiller`/`ferment_container`/`aging_container`/`lable_printer`：水平朝向装饰，无交互逻辑
- [ ] **酿酒工作台** `wine_crafting_table`：带 AGE(0-2) 属性水平朝向；无交互逻辑
- [ ] **你不该拿到的酒** `wine_bottle`：0 饥饿可吃（占位）

### 6.5 装饰方块
- [ ] **烛台** `candle_stick`：发光等级 14，常规放置
- [ ] **汉堡彩蛋** `burger`：非潜行右键 actionbar「§a你的嘴巴似乎被什么粘住了」+ 全服广播「• §8<§7名字§8>: §f唔唔唔，呜呜！」；潜行右键不触发
- [ ] **纳奈子雕像** `nanako_sculpture`：发光 8，水平朝向装饰
- [ ] **四种烹饪锅** `golden_cooking_pot` / `silver_cooking_pot` / `copper_cooking_pot` / `golden_skillet`：无交互
- [ ] **活动方块** `event_block_1/2/3`：无交互占位
- [ ] **高热石墨块** `he_graphite_block`：需正确工具才掉落（`requiresCorrectToolForDrops`）

### 6.6 春节方块
- [ ] **礼炮** `multiple_fireworks`：主手打火石右键 → 播 tnt.primed；5t 后清除 3×3×3 火焰并把本格换成 `firing_multiple_fireworks`；此后 20/30/…/100t 共发 9 发彩色烟花；最后一发同时换 `fireworks_box`
- [ ] **燃放的礼炮** `firing_multiple_fireworks`：临时态，无物品不掉落
- [ ] **礼炮纸壳** `fireworks_box`：燃尽残骸，不掉落
- [ ] **鞭炮** `multiple_firecrackers`：主手打火石右键 → 玩家原点音效 tnt.primed；40t 后召唤小盔甲架标记；3/6/…/24t 共 8 次脉冲（机枪声 createbigcannons:fire_machine_gun + 岩浆/营火/火焰粒子）；24t 后移除标记、变空气并**连锁引爆周围 8 格未点燃鞭炮**

### 6.7 动力雕刻台
- [ ] `mechanical_chisel_table` 方块形态与配方合成（青砖+安山外壳+合金+齿轮）见 §11

## 7. 鸡尾酒系统（kk 协作）

- [ ] **kitchenkarrot 鸡尾酒识别**：`CocktailHelper` 读 NBT `cocktail` id，不引用 kk 类；kk 缺失时相关功能禁用不崩溃
- [ ] **酒保/调酒**：kk 鸡尾酒物品可正常饮用，buff 由配方 JSON `content.effect` 提供（1.20.1 数据驱动）
- [ ] **`fstwines:call_of_tahiti`**：喝完全服广播「• 名字>: 我一定会上工的，嗝～」
- [ ] **`fstwines:free_nightingale`**：随机赠送 1 件礼物（4 选 1：mynethersdelight 子弹椒 / culturaldelights 腌菜 / create_confectionery 焦糖棉花糖 / crabbersdelight 珍珠，各 4 个）
- [ ] **`fstwines:dangerous_party`**：全服广播「…饮下了危险派对！」；饮用者获得击鼓传花 1200t + 发光 1200t
- [ ] **`fstwines:shoal_in_dream`**：脚下方为空气 → 在四方向找空位放一张蓝色床（脚/头两段）；否则给 1 张蓝色床
- [ ] 其他鸡尾酒（david / silent_midnight / lago_de_texcoco）：无自定义效果，仅配方 buff

## 8. 状态效果

- [ ] **绿玩** `fair_play`：效果存续期间每 200t 让 1~20 格内其他实体发光 7 秒（140t持）
- [ ] **死亡回溯** `rewind`：受到致命伤（剩余血量≤1）时取消伤害、血量置 1、传送到记录点（rewind_pos，登录时刷新），播放图腾动画(35)与音效
- [ ] **谵妄** `madness`：效果期间玩家无法发言（`ServerChatEvent` 取消）；平均约 250t 一次全服「说胡话」（随机语录）
- [ ] **击鼓传花** `hot_potato`：duration 编码本场剩余时长；每 10s 广播剩余秒数；剩余 1t 时对持有者结算 200 点伤害（`generic`，走 `hurt()` 管线：持不死图腾触发免死并清场、有死亡回溯则回溯；护甲不减免，抗性/保护附魔可削减）并广播「没能及时把好运传给下一个人」；持有者攻击别人时把效果+发光+致盲+迟缓传给受害者（见 §13.1）

## 9. 流体（6 种，全部 `noBlock`，仅供 Create 配方/桶搬运）

- [ ] **下界溶液** `netherite_liquar`（桶 `netherite_liquar_bucket`）
- [ ] **不稳定下界溶液** `unstable_netherite_liquar`（桶 `unstable_netherite_liquar_bucket`）
- [ ] **豆浆** `bean_sause`（桶 `bean_sause_bucket`，乳白薄层）
- [ ] **大豆油** `bean_oil`（桶 `bean_oil_bucket`，淡黄薄层）
- [ ] **酱油** `soy_sause`（桶 `soy_sause_bucket`，深棕薄层）
- [ ] **恶魂之泪** `ghast_tear`（桶 `ghast_tear_bucket`，淡青薄层）
- [ ] 共性：桶空放返还桶；流体不自然流动（noBlock）；纹理由 `textures/block/<name>_still|flow.png` 绑定；mcmeta 流动动画正常

## 10. 售货机/扭蛋机（VendingEvents + BE）

### 10.1 售货机 `seller`
- [ ] **放置记录 owner**：放置时 BE 记录 owner UUID/名字
- [ ] **店主加价**：右键 +1（price1）、潜行右键 +0.1（price2，满 10 进位）；左键减价（潜行减小数）；actionbar「当前价格：x.xΔ」
- [ ] **顾客看信息**：右键（非店主）显示「此售货机属于 <owner>，每个 <商品> x.xΔ」；无上方容器提示「售货机未正确设置！」；空提示「售货机已空！」
- [ ] **顾客购买**：左键购买最后一个非空槽商品；按三角币(10Δ)/三角片(1Δ)计算余额、扣款、给商品、找零；余额不足提示；扣款/提取失败会退款
- [ ] **经济打款**：购买后执行 `money give <owner> <price>`（经济系统命令占位，日志记录）
- [ ] **容器保护**：非 owner 不能破坏机器；破坏时上方容器有物品 → 阻止（提示先清空）；非 owner 不能打开紧贴机器上方的容器

### 10.2 扭蛋机 `roller`
- [ ] **店主持抽奖券绑定**：owner 手持抽奖券右键 → 写入本机 key 到券 NBT + 附魔光效（提示「抽奖券已绑定！」）；已绑定的券不可覆盖
- [ ] **抽奖（无下方容器）**：需手持匹配 key 的抽奖券；随机取上方容器 1 件奖品；消耗 1 张券、给玩家；券不匹配/空提示相应文案
- [ ] **抽奖（有下方容器）**：直接输出到下方容器（机械接入模式，无需券）；插入剩余丢地上（不吞）
- [ ] **容器保护**：同上（owner、上方容器非空阻止破坏、非 owner 禁开上方容器）
- [ ] 空/未配置提示：「扭蛋机已空！」/「扭蛋机未正确配置！」/「你需要手持抽奖券！」/「抽奖券与扭蛋机不匹配」

## 11. 动力雕刻台（PR#59）

- [ ] **配方**：青砖+安山外壳+合金+齿轮合成 `mechanical_chisel_table`
- [ ] **FE 供能**：接收 FE（createaddition 电动马达带动），上限 10000，每 tick 输入 1000；每执行一次凿刻消耗 100 FE；最多 10 次/tick
- [ ] **7 栏位**：0 材料 / 1 模板 / 2-5 染料 / 6 输出；ultramarine 凿刻配方匹配
- [ ] **过滤器**：可设 filterId；ultramarine 模板识别（软依赖，缺失时仅 WARN 不可设模板）
- [ ] **交互**：空手潜行右键清空所有槽位；空手右键清空过滤器；持模板设置模板；`ultramarine:chisel_dye` tag 含 16 原版染料+17 ultramarine 染料粉
- [ ] **扳手拆除**：潜行 + `forge:tools/wrench` 或 `create:wrench` 右键 → 播放 `create:wrench_remove`、返还方块物品、移除方块（不破坏地形）；非创造才返还
- [ ] 中文 GUI/提示正常

## 12. 检查点系统

- [ ] **放置/破坏维护索引**：`checker` 放置/破坏时读写 `config/CheckerData/posList.txt`（服务端）
- [ ] **玩家经过判定**：每 tick 对每个索引点泛洪填充相连 `supplementaries:checker_block`（XZ 3×3）；玩家站在 xz∈[-0.5,+1.5]、y∈[y,y+4) 且 5s 冷却已过、非持扳手 → 记录时间戳并播放粒子/音效/烟花
- [ ] **扳手交互**：主手 `create:wrench` 右键检查点 → 非潜行打印通过记录；潜行清空记录

## 13. 其他交互逻辑

- [ ] **击鼓传花传递**（`LivingHurtEvent`）：持有 hot_potato 的玩家攻击别人 → 把效果传给受害者（附发光/致盲 20t/迟缓254 20t），广播「恭喜！你把好运传给了…/哦不！…把好运传给了你」「…眼前一黑」
- [ ] **箱子防潜影盒**：潜行 + 主手/副手潜影壳右键 `forge:chests` 标签方块 → 事件取消
- [ ] **行商召唤**：主手 `ultramarine:copper_cash_coin` 右键钟 → 复制行商（重置交易次数）放到钟上方、消耗 1 硬币、提示「召唤了行商」、48000t 后 discard；ultramarine 为软依赖
- [ ] **魔法照片** `MagicPhotoEvents`：潜行右键 `exposure:photograph` 且 NBT `XXXXXAllowteleport="true"` → 传送粒子爆发、50t 后传送到照片 `Pos`、再播 end_rod 粒子
- [ ] **镰刀范围收获** `SickleEvents`：主手 `kaleidoscope_cookery:*_sickle` 右键 → 以射线目标为中心 5×2×5 收割成熟作物+灌木（自动补种）；SWEEP 音效/动画；每次收获耐久 -count；冷却 10t
- [ ] **钻石镰刀** `kaleidoscope_cookery:diamond_sickle`：挖掘 4、耐久 3000、速度 9、攻击 3、附魔 10
- [ ] **下界合金镰刀** `kaleidoscope_cookery:netherite_sickle`：耐久 4000、攻击 5、附魔 15、防火

## 14. 配方抽测

- [ ] `data/tothesky/recipes` 共 110 条（统计于 `src/main/resources/data/tothesky`）：抽测 10 条（压实豆腐、切割豆腐、Create 序列钻石、下界合金产线、魔女因子、奶酪、饮品659、晴天鳕鱼、牛肉盖饭、动力雕刻台）；**日历**：无序配方金粒+黄绿色染料+纸（`calendar_from_shapeless.json`）
- [ ] **刻痕玉米饼序列组装**（`incomplete_tortilla_from_sequenced_assembly.json`）：`culturaldelights:tortilla` 上带（输入=过渡产物=玉米饼），机械手使用刀（`#farmersdelight:tools/knives`，`keepHeldItem` 不消耗）压 50 次 → `tothesky:incomplete_tortilla`；JEI 显示 1 步 × 50 循环；刀耐久不掉
- [ ] `fstwines` 配方 7 条（酒类数据驱动）
- [ ] 镰刀合成（`kaleidoscope_cookery` 命名空间：钻石镰刀 shaped + 下界合金 smithing）
- [ ] 遗忘之露（金锭+恶魂之泪十字）
- [ ] 包装颜料（`ultramarine:xuan_paper` + 1~4 `ultramarine:chisel_dye` → `packed_colors` 带 StoredColors NBT，`PackedColorsRecipe`）
- [ ] 邮筒配方（红/绿邮筒，contact 模组；`forge:cheese`/`ultramarine:chisel_dye` tag 已迁移）

## 15. 资源渲染 & 本地化

- [ ] 所有方块模型/贴图加载无紫黑棋盘（F3 检查 missing texture 日志）
- [ ] 拉面 `bites=0~4 × facing` 变体渲染正确（新加朝向）
- [ ] 阶段披萨 9 方块模型正确
- [ ] 流体动画（mcmeta 流动贴图）
- [ ] 可放置食物方块多段碰撞箱正确（豆腐脑碗形/幻翼虾仁扁盘/饮品659杯形/晴天鳕鱼扁盘/牛肉盖饭三层/鱿鱼狂欢节矮盘）
- [ ] lang（`zh_cn.json`/`en_us.json`）无 `translation key` 字面量残留

## 16. 与其他模组的兼容

- [ ] **Create: Crystal Clear 移植（32 方块 + 旧存档 remap）**：
  - 旧存档（`crystal_clear:` / `create_crystal_clear:` 命名空间）打开后，已放置的玻璃机壳/包裹传动杆/齿轮/脚手架原地显示为 tothesky 方块，朝向与状态不丢；启动日志逐条 `方块映射 crystal_clear:xxx -> tothesky:xxx`、`物品映射 ...`（INFO 级）；`create_crystal_clear:steel_*` 三条（上游 2.1 已删）仍按 Forge 默认策略报缺失，属预期
  - 包裹传动杆/齿轮所在方块的**方块实体**解析为 `tothesky:glass_encased_shaft` / `glass_encased_cog` / `glass_encased_large_cog`（`RegistryAliasEvents` 注册表别名；不产生 MissingMappings 日志），旋转速度/网络不断
  - 创造栏出现 8 种玻璃机壳 + 6 种玻璃脚手架（包裹传动杆/齿轮无物品形态，与上游一致）
  - 玻璃机壳对传动杆/齿轮右键装壳 → 得到包裹方块；扳手右键脱壳 → 返还传动杆/齿轮 + 机壳
  - 连接纹理：相邻同种玻璃机壳之间纹理相连；包裹方块与相邻的玻璃相连；旋转中的传动杆/齿轮用 Flywheel 渲染（F3 无重复轴）
  - 玻璃脚手架可攀爬（`climbable`）、可被扳手旋转；`create:casing` 方块/物品 tag 含 8 种玻璃机壳（装壳机制依赖）
- [ ] **kitchenkarrot**：鸡尾酒系统（酒保/调酒/饮后效果）正常
- [ ] **farmersdelight**：滋养效果/切割/烹饪配方（豆腐切割、麻婆豆腐锅）——FD 缺失时滋养效果被跳过，不崩溃
- [ ] **Create 6.0.8**：部署器润滑、混合器、序列装配全部 kjs 配方等价物；扳手/检查点子方块协作
- [ ] **createaddition**：FE 供能雕刻台
- [ ] **ultramarine**：凿刻模板识别（反射软依赖，缺失时仅 WARN）；行商硬币；齐宣纸/凿刻染料
- [ ] **exposure**：照片传送
- [ ] **kaleidoscope_cookery**：镰刀（仅在 mod 加载时注册；userdev 不加载时跳过）
- [ ] **create_confectionery**：缺失时（EffectStack.get）仅 WARN 不崩
- [ ] **软依赖总检**：依次去掉 kk/fd/create/ultramarine/exposure/kaleidoscope_cookery 各 mod，确认相关功能禁用但不崩溃

## 17. 日历系统（CalendarBlock + GUI + REST API）

- [ ] **方块注册与贴墙放置**：`calendar`（日历）可创造获取；**只能贴墙放**——右键墙面落下（点地面/天花板不放置、不消耗物品），朝向 = 所点的那面墙（四向）；背后方块被拆掉/替换后自动掉落；破坏掉落自身
- [ ] **模型与碰撞箱**：模型为 12×13×3px 挂墙挂牌（非完整立方体，不再遮挡邻面/挡光）；四个 `facing` 下背板都紧贴墙面、不悬空不穿墙；碰撞箱/轮廓与模型外框重合（`blockstates/calendar.json` 的 `y` 旋转与 `getShape` 的四条一致）
- [ ] **打开 GUI**：右键日历方块弹出月视图；新底图（256×256，自带标题/星期标签/格子边框），日期格区 6 行×7 列、第一格左上 (42,88)、步进 25px、格主体 22×22、内容区 18×18；「今天」白色高亮恰好覆盖内容区不压边框；日期数字在内容区左上角（格左上+3）且**始终压在图标上层**（数字在全部图标之后以 z=200 绘制）
- [ ] **翻月与标题**：左右为贴图箭头按钮（`calendar_prev.png` / `calendar_next.png`，32×32 原尺寸，距左右边缘 6px、距顶 36px，**不绘制任何文本**，仅无障碍朗读；悬停略暗）；月份标题「xxxx·x」**水平居中**，文字下缘在日期格第一行上方 13px（y=67）；跨年正确（12 月→1 月年份 +1）
- [ ] **图标渲染**：图标贴内容区右下再整体左上移 1px；`iconType=item` 物品图标 16px、`iconType=block` 方块物品形态、`iconType=player` 玩家头 14px（比物品小一圈）；头像皮肤经 `SkullBlockEntity.updateGameprofile` + `SkinManager.registerSkins` 异步解析（AW 模特同款流程），未就绪时默认皮肤占位、约 0.5s 内刷新为真皮肤；离线玩家解析后也显示正确皮肤
- [ ] **多活动轮播**：同一日期 ≥2 个事件时图标每 2 秒（40t）轮换
- [ ] **Tooltip**：悬停有事件的格子显示全部活动；**生日显示「xxx的生日」**（🎉 节日用原名 / 🎂 生日加后缀），描述在名称下方
- [ ] **管理页**（`http://127.0.0.1:39000/`，mod 自带，上半页是日历、下半页是信件编辑器见 20b）：表格名称列生日显示「xxx的生日」、页头今日活动同规则；编辑表单回填原始 name；增删改与游戏 GUI 即时同步
- [ ] **REST API**（服务器侧已自动化验证，GUI 联动需手测）：`GET/POST/PUT/DELETE http://127.0.0.1:39000/api/calendar/events`、`GET /today`；**网页管理端打开 GUI 时增删事件，GUI 无重开即时刷新**
- [ ] **崩溃恢复**：服务器强杀后重启，启动日志出现「[日历] 从镜像合并 N 条 SavedData 缺失的事件」（镜像 `<世界>/calendar_events.json`，与 SavedData 按 id 并集合并，两侧新增都不丢）
- [ ] **配置**：`config/tothesky-common.toml` 的 `calendar.port`（默认 39000）/ `calendar.bindAddress`（默认 127.0.0.1）改后重启服务器生效；端口占用时仅 WARN 不崩服（**同机开客户端+专用服务器时只有一个进程能绑 39000**——后启动的 HTTP 禁用，属预期，可改端口或把其中一个改绑其它端口）

| | | 17. 日历系统 | | |

## 18. 锁链物流策略（蛙港/包裹，服务器性能）

> 行为来源：`FrogportChainTargetMixin` + `ChainConveyorPackageBanMixin`（硬编码，无配置开关）。
> 目标状态：**锁链传动轮网络上永远不存在包裹**——蛙港与锁链网络彻底解耦，玩家也无法手动挂包。
> 已用临时探针在整合服务器实测 `accept=false exportReal=false exportSim=false loopPorts=0`（探针已删）。

- [ ] **蛙港不向锁链投递**：蛙港设地址 + 链上无匹配端口时，相邻容器/打包机里的包裹**不会被抽走**（无动画/无声音/物品留在原处，不掉落）
- [ ] **蛙港不接锁链包裹**：链上（若有残留包裹）的地址匹配蛙港时，蛙港仍可正常截取存入自身/下方容器（**「收」保留**）
- [ ] **蛙港不被锁链识别**：蛙港贴锁链放置后仍可正常设地址/开关收发/开 GUI；锁链不显示该蛙港端口
- [ ] **玩家手动挂包被拒**：手持包裹左键/右键锁链传动轮 → 包裹**不被消耗**、留在手上（不消失、不掉落）
- [ ] **车站蛙港不受影响**：贴列车站的蛙港照常收发（走 `TrainStationFrogportTarget`，不经本策略）
- [ ] **Ponder 教程正常**：Create 内置「货物蛙港」「锁链传动轮」Ponder 动画照常播放包裹（仅客户端虚拟世界，不拦）
- [ ] **无回归**：链上原有包裹（新档应为 0）不再新增；蛙港/锁链的旋转动力传递、乘坐滑行（`ChainConveyorRidingHandler`）正常

| | | 18. 锁链物流策略 | | |

---

## 19. 往来邮件转接（ContactMail 定时投递）

> 实现：`contact/ContactMail`（三个入口，收件人只收**昵称**）+ `contact/ContactMailBridge`（唯一 Contact 类引用点）+ `contact/ScheduledMailData`（主世界 SavedData `tothesky_contact_mail`，含昵称格式校验）+ `contact/ContactMailScheduler`（每 20t 检查 + 昵称→UUID 解析）。
> 投递条件：投递日 `epochDay ≤ 今天` → 当天 00:00 后的首次检查（≤1s）即投递；今天/过去日期在排期后 1s 内投递。
> 昵称解析在**投递时刻**做：在线玩家 → 服务器 usercache（`GameProfileCache`，含离线模式）；都查不到则条目保留待以后重试（玩家首次进服后即可解析）。
> 调用示例（临时命令/JShell/测试方块的任意服务端线程）：
> `ContactMail.sendPostcard("Notch", LocalDate.of(2027,1,1), new ResourceLocation("contact","new_year_2023"), "新年快乐")`
> `ContactMail.sendParcel("Notch", LocalDate.now(), List.of(new ItemStack(Items.CAKE, 3)))`
> `ContactMail.sendRedPacket("Notch", LocalDate.now(), List.of(new ItemStack(Items.DIAMOND, 8)), "恭喜发财")`

- [ ] **当天投递**：`date = LocalDate.now()` 排期 → 1s 内收件人邮箱出现该邮件；日志有「已排期定时邮件」与「定时邮件已投递」
- [ ] **未来投递（定时）**：`date = 明天` 排期 → 当天不出现；把服务器系统时间（或等）到该日 00:00 后 1s 内投递
- [ ] **停机补投**：`date = 明天` 排期后**关服**；改系统日期到该日之后再开服 → 首次检查即补投（不会因错过 00:00 窗口而滞留）
- [ ] **在线昵称解析**：给当前在线的玩家昵称排期当天投递 → 正常到手（走在线玩家分支）
- [ ] **离线昵称解析**：给一个**曾进过服、当前离线**的玩家昵称排期 → 照样投递成功（走 usercache 分支）
- [ ] **陌生昵称延迟解析**：给一个**从未进过服**的昵称排期当天投递 → 日志告警「收件人「X」暂不可解析」、条目保留；该玩家首次进服后 1s 内自动投递
- [ ] **昵称大小写**：用 `notch` 排期、实际账号为 `Notch` → 仍能解析投递（在线与 usercache 均大小写不敏感）
- [ ] **非法昵称被拒**：空串 / 含空格 / 中文 / 带连字符 / 17 字符 / 传 UUID 文本 → 日志告警，不排期
- [ ] **明信片内容**：收件人开邮箱取出的明信片款式与 `style` 一致、正文与 `text` 一致、提示行显示「寄件人：服务器」
- [ ] **包裹内容**：包裹内物品与种类/数量与入参一致（≤4 件）；开包后物品进背包
- [ ] **红包内容与祝福语**：红包内物品一致（≤1 件）、提示行显示祝福语；开包后物品进背包
- [ ] **未知款式被拒**：`style = contact:不存在的款式` → 日志告警「未知明信片款式」，不产生邮件
- [ ] **超量被拒**：包裹传 5 件 / 红包传 2 件 / 内容物含空气 → 日志告警，不产生邮件
- [ ] **存档持久化**：排期未来日期 → 正常 `stop` 关服 → 重启后 `data/tothesky_contact_mail.dat` 里条目仍在（含昵称原样），到点照常投递
- [ ] **离线收件人**：收件人离线时到点投递 → 上线后收到「有新邮件」提示、开邮箱可取件
- [ ] **邮箱满**：收件人邮箱 24 格塞满时投递 → 邮件留在 Contact 挂号队列等待，清空后可收到
- [ ] **Contact 缺失**：临时移走 `Contact-forge-1.2.3.jar` 启动 → 三个入口返回 `false` 且只告警一次、不崩服；已排期条目保留在 SavedData（装回后照常投递）

| | | 19. 往来邮件转接 | | |

---

## 20. 信件（config/tothesky/letters）

> 实现：`contact/LetterLibrary`（目录重读 + 首启生成示例 + 按内容缓存 + 网页读写 `list/find/save/delete`）、`contact/FestivalLetter`（JSON 解析 / 占位符）、`contact/LetterStateData`（主世界 SavedData `tothesky_letters`，记每条排期的下次投递日）、`contact/LetterScheduler`（每 60s 检查到期的信，转发给 `ContactMail` 三个入口）、`command/ToTheSkyCommands`（`/tothesky reloadletters`）、`contact/http/LetterApiHandler`（REST，管理页的信件编辑器用）。
> **信件只描述「送什么」**——文件里没有 `trigger`/`player`/`date` 这类字段，写给谁、什么时候发一律由日历决定（两条路径）：
> **① 节日绑定**：节日活动的 `letter` 填信件文件名去 `.json`（见第 22 节）→ 节日当天发给**全服每位玩家**；
> **② 生日**：日历里 `type=birthday` 的活动当天，收件人（活动名 = 玩家昵称）收到 `birthday.json`——**文件名固定**，一封生日信服务全服（见第 21 节）。
> 没有任何节日绑定、又不是 `birthday.json` 的信当前不会投递，日志每封信只提醒一次（「没有任何节日绑定它，也不会被生日调用」）。
> **配置何时生效**：目录在**服务器启动后的首轮检查**、**`/tothesky reloadletters`**、以及**网页保存/删除信件之后**（只重读定义，不重发今天的信）读取；平时每 60 秒只检查「到期没」、不碰磁盘。所以手写改完 json 要跑一次 `/tothesky reloadletters`（权限等级 2），该命令顺带会立刻投一轮——当天该发的信不用再等下一次检查。**日历里的节日绑定改动后同样要重载才生效。**
> 格式（一个 `*.json` = 一封信；未列出的字段一律忽略，手写的 `_comment` 之类会保留）：
> `{ "enabled": true, "type": "postcard|parcel|red_packet", "style": "contact:xxx"（明信片必填）, "items": [{"item": "minecraft:cake", "count": 3}]（包裹/红包必填）, "text": ["祝${player}生日快乐！", "今天是${date}。"]（明信片/红包可选） }`
> **`text` 是字符串数组，每项一行**：投递时按顺序用换行拼成一个字符串交给 Contact（NBT 里始终是单个 `Text` 字符串标签，与原版明信片完全一致）。单个字符串仍然接受（旧写法 = 只有一行的数组）。空数组 / 缺省 = 没有正文；行里写 `\n` 也能换行（双重换行会显示成空行）。
> 占位符（只在 `text` 里）：`${player}` 收件人、`${date}` 投递当天日期（yyyy-MM-dd）、`${item}` 内容物清单（**仅红包有效**，明信片/包裹里原样保留）；`${day}` 已取消，写它会原样出现在信里。
> 投递时机：由日历活动的月-日决定，逐年循环（农历日期按农历年换算），都在当天 00:00 后 1 分钟内投递。

- [ ] **首启生成**：删掉 `config/tothesky/letters` 后启动 → 目录、三份示例（`example_postcard/parcel/red_packet.json`）与 `birthday.json` 出现；三份示例均 `enabled: false`，不会误发任何人
- [ ] **不重复生成**：目录已存在时不重建；单删某个示例文件重启后不会复活（`birthday.json` 例外——它是启用中的配置，删了会自动补回，见第 21 节）
- [ ] **命令可用**：`/tothesky reloadletters` 正常输出「重载完成：启用 N 封」；权限 <2 的玩家执行会看到「未知命令」（与 `/reload` 同级）；`/tothesky` 单独执行提示不完整命令而非报错
- [ ] **重载才生效**：服务器运行中改一封信的内容（或丢入新 `*.json`）→ **不跑命令时投递行为不变**；跑一次 `/tothesky reloadletters` 后立刻按新配置生效（新增文件被识别）
- [ ] **重载即投**：某封信今天该发但还没到下一轮检查 → 跑命令后立刻投出（输出「已投递 N 封」），不必等 60 秒
- [ ] **重载重发今天的信**：某封今天该发的信已投过（`next_due` 已推进到明年）→ 再跑一次 `/tothesky reloadletters` → **收件人再收到一份**；连跑两次就有两份（命令专属行为）。对照：不跑命令、纯等 60 秒检查 → 不会重发
- [ ] **只有今天的重发**：不是今天该发的（节日不在今天、不是今天的寿星）→ 跑命令**不会**把它翻出来重发
- [ ] **无调用者不投递**：目录里放一封新信但日历里没有任何节日绑定它 → 日志 WARN「没有任何节日绑定它，也不会被生日调用，当前不会投递」（每封信只报一次），不会发出去
- [ ] **删文件清状态**：删掉某封信 + 跑一次重载 → `data/tothesky_letters.dat` 里该 id 的排期消失；同名放回再重载则从头算（下次该发时才发）
- [ ] **旧格式自动迁移**：手写一份带 `trigger`/`player`/`date` 的旧格式信 → 在网页里编辑并保存 → 盘上文件里这三个字段**被清掉**，`_comment` 与内容保留；列表不再提示「已废弃」
- [ ] **旧字段被忽略**：手写一份带 `trigger`/`player`/`date` 的旧格式信 → 内容照常载入、能投递，日志对每个字段提醒一次「已废弃…已忽略」，不会因为不认识就跳过整封
- [ ] **占位符**：`${player}` → 收件人昵称（「祝${player}生日快乐！」→ 明信片上「祝liziluyu生日快乐！」）；`${date}` → 投递当天日期；`${item}` → 红包里替换成 `物品显示名×数量`（多项用「、」连接），明信片里原样保留
- [ ] **`${day}` 不再替换**：信里写 `${day}` 会原样出现在收到的明信片上
- [ ] **`${item}` 用物品显示名**：红包内容物 `minecraft:diamond` → 祝福语里是该项的名字（客户端整合服为「钻石」，专用服务器无语言包时为「Diamond」）；带自定义名的物品显示其自定义名
- [ ] **多行正文**：`birthday.json` 的 `text` 写成两行（默认就是两行）→ 收到的明信片上是两行（「祝<昵称>生日快乐！」/「今天是<日期>。」），不是一行挤着；**老客户端（未装新版模组）看到的效果完全相同**——NBT 里仍是单个 `Text` 字符串，只是含换行
- [ ] **单字符串正文仍可用**：把某封信的 `text` 写成 `"祝${player}节日快乐！"`（旧写法，不是数组）→ 重载后照常投递、明信片一行正文
- [ ] **空行与空正文**：`text: ["第一行", "", "第三行"]` → 明信片第二行是空行；`text: []` 或删掉 `text` → 明信片没有正文，不报错
- [ ] **text 写错类型被拒**：`"text": 123` / `"text": ["a", 2]` → 该文件被跳过并 WARN（「text 必须是字符串数组」/「text[1] 必须是字符串」），其余信件照常
- [ ] **包裹无正文**：`type: parcel` 里写 `text` → 日志告警「包裹没有正文，text 已忽略」，包裹仍正常投递
- [ ] **坏配置不崩服**：坏 JSON / 未知 `type` / 未知物品 / 包裹超 4 件 / 红包超 1 件 / 明信片缺 style → 该文件被跳过并 WARN，其余信件照常投递；**同内容反复重载只报一次**（改文件后重载才再报）
- [ ] **Contact 缺失**：移走 `Contact-forge-1.2.3.jar` → 目录、示例与 `birthday.json` 照常生成；命令输出「未安装 Contact，本次未投递（排期保留）」，信件不投递也不消费排期（装回后补投）；**绑定写错、无人调用的告警仍会出现**（配置诊断与 Contact 无关）

| | | 20. 节日信 | | |

---

### 20b. 网页信件编辑器（`http://127.0.0.1:39000/` 下半页）

> 实现：`web/WebApiServer`（本机 HTTP 宿主，挂 `/api/calendar`、`/api/letters`、管理页三块）、`web/WebHttp`（JSON/CORS/服务器线程提交等公共管道）、`contact/http/LetterApiHandler`（信件 REST）、`contact/LetterLibrary.list/find/save/delete`（读写 `config/tothesky/letters`）、`web/calendar_admin.html` 的「信件」区。
> REST：`GET /api/letters`（列表 + 编辑器元数据 types/styles/容量/Contact 在场与否/生日信 id）、`GET /api/letters/{id}`、`PUT /api/letters/{id}`（body = 信件 JSON 本体，创建或整体替换）、`DELETE /api/letters/{id}`。id = 文件名去掉 `.json`（中文名可用）。
> 语义：**保存即生效**——写盘后立刻只重读定义（不重发今天已投过的信；要重发在游戏里跑 `/tothesky reloadletters`）。编辑器以**文件原文**为准（`raw`），所以手写的 `_comment` 等字段不会被网页抹掉。

- [ ] **列表**：页面下半页列出目录里全部 `*.json`，每行有 id、状态（启用/停用/无效）、类型、内容摘要、**谁调用它**（生日 / 绑定的节日名 / 「没有节日绑定，不会投递」）；示例与 `birthday.json` 都在，表单里**没有**触发方式/收件人/日期字段
- [ ] **新建**：填 id + 类型（明信片/包裹/红包）+ 款式或内容物（+ 正文）→ 保存后 `config/tothesky/letters/<id>.json` 出现，内容为美化后的规范 JSON（中文不转义），其中 `text` 是**字符串数组**
- [ ] **正文按行存**：正文框（多行文本框）里敲三行 → 保存后盘上是 `"text": ["第一行", "第二行", "第三行"]`；空行保留（末尾残留的空行不写进文件）；整框清空 → 文件里没有 `text` 字段
- [ ] **正文按行回填**：点「编辑」→ 正文框按行回显（不是把数组挤成一行、也不是显示 `["…"]` 原始 JSON）；改一行保存后只有那一行变化
- [ ] **旧字符串正文回填**：手写 `"text": "一行"` 的信 → 网页里回填成一行文本，保存后落盘为数组（旧写法被规整）
- [ ] **text 写坏有提示**：手写 `"text": 123` → 列表显示「无效」+ 错因（「text 必须是字符串数组…」），点编辑切原文模式改好保存
- [ ] **编辑回填**：点「编辑」→ 各字段按文件回填；改款式/正文/内容物后保存 → 盘上文件同步变化
- [ ] **保留手写字段**：手工往 json 里加 `"_comment": "..."` → 在网页里改别的字段并保存 → 注释仍在（保存是**合并**而非重写）
- [ ] **切换类型**：明信片 → 包裹时款式输入框收起、出现内容物行；反向切换时内容物收起、出现款式与正文；包裹**不动**文件里原有的 `text`（留着只多一条「text 已忽略」提醒，不无声丢内容）
- [ ] **调用者提示**：表单下方随 id 变化——`birthday` 显示「这是生日信（文件名固定）…」；被节日绑定的显示「被节日绑定：<节日名>」；谁都没绑定的显示「还没有任何节日绑定它，当前不会投递」；空 id 时说明「在日历里给某个节日填「绑定信件」= 它的文件名」
- [ ] **生日信**：`birthday` 行显示「生日信」徽标、调用者列显示「生日 · 当天每人一份」；其余行不显示
- [ ] **停用**：不勾「启用」保存 → 列表显示「停用」，`enabled: false` 落盘；**停用的信也能被编辑**（表单仍回填其内容），且停用状态下保存**仍做完整校验**（缺 style / 未知物品照样 400，避免存进「启用就投不出去」的配置）
- [ ] **校验拒绝**：id 为空 / 以 `.json` 结尾 / 含 `/`、`\`、`|`、控制字符 → 400 且不落盘；未知物品 id → 400「未知物品」；物品超容量（包裹 4 / 红包 1）→ 400；明信片 style 为空 → 400
- [ ] **坏文件兜底**：手工写坏一个 json（缺逗号 / 根本不是 JSON）→ 列表显示「无效」并给出错因，点「编辑」切到「原始 JSON」文本框，改好保存后自动转回结构化表单
- [ ] **原文模式的前端检查**：在「原始 JSON」里故意写坏语法 → 保存时浏览器先报「JSON 语法错：…」，不发请求
- [ ] **删除**：点「删除」有确认框；**被节日绑定的信**额外提示「它正被这些节日绑定：…」；删除后该文件消失、日历里指向它的节日不再投递（日志有 WARN）
- [ ] **联动跳转**：日历表格里点「绑定信件」列的信件 id → 滚到信件区并打开编辑；id 在目录里不存在 → 预填该 id 到「新建」表单并提示「还没有「xxx」这封信」
- [ ] **保存即生效**：改完保存（不跑任何命令）→ 1 分钟内到期的信按新内容投递；**今天的信不会被重发**（对照：跑 `/tothesky reloadletters` 才重发）
- [ ] **元数据随数据包**：明信片款式输入框的候选来自 Contact 的 `data/*/postcards/*.json`；未装 Contact 时页面顶部提示「信件会照常保存，但暂时投递不出去」
- [ ] **`/api/letters` 与 `/api/letters/{id}` 都可达**：裸路径返回列表（不是 404）；`/api/lettersX` 之类形近路径不落到该 handler

| | | 20b. 网页信件编辑器 | | |

---

## 21. 生日信（birthday.json + 日历生日）

> 实现：`contact/LetterLibrary.ensureDefaultFiles`（首启生成 `config/tothesky/letters/birthday.json`，缺失即补回；id 固定为 `LetterLibrary.BIRTHDAY_ID`）、`contact/FestivalLetter`（信件只描述内容；${player} 按投递时的收件人渲染）、`contact/LetterScheduler`（每轮检查读日历 `type=birthday` 的活动，按「信件|收件人|生日」逐人独立排期）、`command/ToTheSkyCommands`（`/tothesky reloadletters`）。
> **文件名固定**：`birthday.json` 是唯一「按文件名认领」的信件——日历里每条 `type=birthday` 的活动当天都调用它；其余信件只能由节日活动的 `letter` 绑定（见第 22 节）。
> 数据源：日历活动（`/api/calendar/events` 或游戏内日历 GUI 里 `type=birthday` 的条目，其 `name` 就是玩家昵称）。
> 语义：**一个生日信文件服务全服**——当天过生日的每位玩家各收一份，逐年循环；改 `birthday.json` 即换生日礼物，不必碰日历。

- [ ] **首启生成**：新实例首次启动 → `config/tothesky/letters/birthday.json` 出现，内容为默认明信片（`style: contact:spring_day`、`enabled: true`，**无任何触发字段**），日志有「已生成默认生日信」
- [ ] **删掉会补回**：删掉 `birthday.json` → 跑一次 `/tothesky reloadletters`（或重启服务器）后补回默认内容；**停用要改 `enabled: false`**（改完再重载，不再投递，文件保留）
- [ ] **改文件不覆盖**：把 `style` 改成别的款式 → 重载后仍是改过的款式（不会被打回默认）
- [ ] **改动需重载**：改 `style`/`enabled` 后不跑命令 → 投递行为不变；跑一次重载后按新内容投递
- [ ] **当天生日投递**：日历里加一条 `type=birthday`、`name=<某玩家昵称>`、月/日 = 今天 → 1 分钟内（或跑一次重载）该玩家邮箱出现明信片，正文**两行**：「祝<昵称>生日快乐！」/「今天是<今天>。」；日志有「[节日信] 生日信 birthday 已投递」
- [ ] **非当天不投**：生日设成明天 → 今天不投（重载输出「当前没有到期的信」）；改系统日期到那天后 1 分钟内投递
- [ ] **多人同一天**：日历里放两条同月同日的生日（不同昵称）→ 两人各收一份，互不串（`tothesky_letters.dat` 里两条键 `birthday|<A>|MM-DD`、`birthday|<B>|MM-DD`）
- [ ] **逐年循环**：投递一次后该玩家 `next_due` 推进到明年同一天；改系统日期到明年该日再次投递
- [ ] **停机补投**：生日当天服务器没开（或当晚停机、次日才开）→ 次日开机后 1 分钟内补投；反之，**首次**见到一个今年已过的生日不会补发（直接排到明年）——与节日绑定信同一套规则
- [ ] **改生日即重排**：把某人的生日从今天改到下月 → 该玩家当天不再投；键变成 `birthday|<昵称>|MM-DD`（新日期），旧键在下次检查时从 `.dat` 里消失
- [ ] **删生日即清状态**：日历里删掉该生日 → 下次检查后 `.dat` 里对应键消失
- [ ] **换礼物**：把 `birthday.json` 的 `type` 改成 `parcel` + `items`（≤4 件）→ 重载后生日当天收到包裹；改成 `red_packet` + `items`（≤1 件）+ `text` 含 `${item}` → 收到红包且祝福语里是内容物清单
- [ ] **占位符按人渲染**：同一天过生日的两人收到的 `${player}` 各自是自己的昵称（不是文件里写死的）
- [ ] **旧字段不影响生日信**：`birthday.json` 里手写 `trigger`/`player`/`date`（旧版本遗留）→ 日志 WARN「…已废弃…已忽略」，但**信照常按日历投递**（不是跳过）
- [ ] **名字非法**：日历里放一条 `type=birthday`、`name=小明的生日`（含非法字符）→ 日志 WARN「名字不是合法玩家昵称，无法投递」，其余生日不受影响，不崩服
- [ ] **日期非法**：手改 `calendar_events.json` 造出 `month=13`/`day=45` 的生日条目 → 启动后日志 WARN「月 13 / 日 45 不是合法日期」，跳过该条，不崩服
- [ ] **闰日生日**：2 月 29 日的生日 → 平年在 2 月 28 日投递（不隔三年才发），闰年在 2 月 29 日
- [ ] **收件人未进过服**：给一个还没进过服的昵称设生日（今天）→ 当天投递失败并 WARN「收件人…暂不可解析」，该玩家首次进服后自动补投（排期不被消费）
- [ ] **与节日绑定信共存**：目录里同时放 `birthday.json` 与一封被节日绑定的信 → 各按各的规则投递，互不影响；日志与 `.dat` 里两类键并存
- [ ] **同一封信两条路径**：把某个节日也绑定到 `birthday.json` → 生日当天寿星收一份（生日路径），节日当天全服各收一份（节日路径），互不干扰
- [ ] **日历实时生效**：服务器运行中通过 REST/GUI 加一条今天是生日的活动 → 1 分钟内投递（无需重启）

### 21b. 农历生日（`/tothesky setbirthday`）

> 实现：`calendar/LunarCalendar`（1900-2100 查表换算）、`CalendarEvent.lunar` + `occurrenceIn/nextOccurrenceOn`、`command/ToTheSkyCommands.setBirthday`、`client/gui/calendar/CalendarScreen.monthIndex`。
> 语义：农历生日存「农历月-日」，逐年换算成公历日子展示与投递（农历腊月/正月可能落在次年 1-2 月）；**农历月份号与公历月份号无关**（农历八月初六 2026 年落在 9-16）。
> 闰月不参与：只认非闰月的那个月（闰六月出生的人按六月过）。

- [ ] **指令注册与权限**：`/tothesky setbirthday <month> <day> [isnongli]`；**普通玩家（权限 0）能用**，`isnongli` 可省略（缺省 = 公历）；`/tothesky reloadletters` 仍需权限 2
- [ ] **只能改自己**：没有目标玩家参数，登记的是执行者自己的昵称（控制台执行 → 提示「这条命令只能由玩家执行」）
- [ ] **当天登记当天投**：农历八月初六 = 今天 → 跑命令后 1 分钟内收到生日信；回显「下一次是 <今天>（也就是今天，一分钟内会收到生日信）」
- [ ] **重复登记 = 覆盖**：同一玩家跑两次不同日期 → 日历里仍只有一条（不会冒出第二个生日）；已有手工配的头像/描述保留
- [ ] **日期校验**：`/tothesky setbirthday 2 30`（公历 2 月只有 29 天）、`13 1`、`8 31 true`（Brigadier 挡参数）都报错不写入；`2 29`（闰日）与农历 `8 30` 放行
- [ ] **日历上按当年摆放**：农历生日在 GUI 里落在**换算后的公历格子**（农历八月初六 2026 年 → 9-16 格），翻到 2027 年改落 9-06 格；tooltip 名字后带「（农历八月初六）」
- [ ] **跨年农历**：农历腊月二十 → 落在公历次年 1-2 月（2026-02-07 / 2027-01-27），不会出现在公历 12 月格
- [ ] **农历/公历不撞键**：同一天的公历生日与农历生日各发各的（`tothesky_letters.dat` 里 `birthday|<名>|08-06` 与 `birthday|<名>|农历08-06` 并存）
- [ ] **管理页**：编辑表单勾「日期按农历算」→ 日期上限自动变 30（取消勾选回到 31）；表格日期列显示「农历 8 月 6 日」；农历勾选对节日与生日都可用（见 22b）
- [ ] **旧存档兼容**：升级后已有的公历生日照旧（缺 `lunar` 字段按公历读），不误判成农历

| | | 21b. 农历生日 | | |

| | | 21. 生日信 | | |

---

## 22. 节日绑定信件（日历 letter 字段）

> 实现：`calendar/CalendarEvent.letter`（可选字段，仅 `type=festival` 有效；NBT 键 `letter`，镜像与 REST 同名）、`calendar/http/CalendarApiJson.validateLetter`（校验格式）、`contact/LetterScheduler.deliverFestivalLetter`（节日当天发给全服每位玩家）、`contact/LetterScheduler.recipientNames/readUsercache`（名单 = 当前在线 ∪ `usercache.json`）。
> 用法：在日历里给节日填 `letter` = 某封信的文件名去 `.json`；节日当天这封信发给**全服每位玩家**，`${player}` 逐人渲染。信件本身不写收件人与日期（见第 20 节）。
> 数据源：日历活动（REST `POST/PUT /api/calendar/events` 的 `letter` 字段，或管理页 `http://127.0.0.1:39000/` 的「绑定信件」输入框）。
> **改动要重载**：日历里的绑定改动后，跑一次 `/tothesky reloadletters` 才生效（与信件目录同一条规则）。
> 绑定的信一律**不按自身 trigger 投递**（避免发两遍）；节日当天第一次执行后按「信件\|收件人\|月-日」逐年循环。

- [ ] **REST 字段可用**：`POST /events` 带 `"letter":"chunjie"` → 201 且返回体含 `letter`；`GET /events` 里能看到该字段；不带 `letter` 的旧客户端请求仍正常（向后兼容）
- [ ] **校验**：生日活动带 `letter` → 400「letter is only for festival events」；`"letter":"chunjie.json"` → 400（提示去掉 .json）；含 `/`、`\`、`|` 等字符 → 400；中文文件名（如 `中秋礼包`）→ 通过；`PUT` 传 `"letter":""` → 解绑成功
- [ ] **管理页**：`http://127.0.0.1:39000/` 表单出现「绑定信件」输入框，表格新增「绑定信件」列；把类型切到「生日」时输入框被禁用并清空；编辑已有事件时正确回填
- [ ] **当天投递**：给某节日填 `letter` = 某封新信，月/日 = 今天 → 跑一次重载后，**每位玩家**（在线 + 曾进过服）各收到一份；日志逐人一行「[节日信] 节日信 <信件> 已投递：服务器 → <昵称>」
- [ ] **逐人渲染**：同一天两位玩家收到的 `${player}` 各自是自己的昵称；红包 `${item}` 是内容物清单
- [ ] **逐年循环**：投出后该玩家 `tothesky_letters.dat` 键为 `<信件>|<昵称>|MM-DD`、`next_due` 推进到明年同一天；改系统日期到明年同日再次投递
- [ ] **离线收件**：玩家离线时到点投递 → 邮件进其邮箱，上线后可取
- [ ] **未进过服的人**：只存在于 `usercache.json`（当前不在线）的昵称也会收到；名单里用户名不合法（含空格/中文/竖线等）的条目被跳过且不建排期
- [ ] **首次见到已过的节日**：节日日期设成今年已过去的某天 → 不补投，直接排到明年（与生日信规则一致）
- [ ] **同一天两个节日绑同一封信**：只发一次（排期键相同，天然去重）
- [ ] **多个节日绑同一封信**：各按自己的日子发（如春节 1-1 与元宵 1-15 共用一个红包模板）
- [ ] **旧格式信也能被绑定**：给一封带 `trigger`/`player`/`date` 的旧格式信填上 `letter` → 日志只提醒这些字段已废弃，信按节日发（不会按自身日期再发一遍）
- [ ] **无人绑定的信**：写一封新信、日历里没人绑它 → 日志 WARN「没有任何节日绑定它，也不会被生日调用，当前不会投递」，不崩服、不投递
- [ ] **绑定指向不存在的信**：给节日填 `letter` = 目录里没有的名字 → 日志 WARN「绑定的信件 xxx 不存在…该绑定不会投递」，其余信件照常；同一绑定反复重载只报一次
- [ ] **崩溃恢复**：加回绑定后强杀服务器 → `world/calendar_events.json` 镜像里含 `letter`；重启后 `GET /events` 仍能看到该绑定
- [ ] **旧存档兼容**：用没有 `letter` 字段的旧 `tothesky_calendar.dat` / `calendar_events.json` 启动 → 事件正常载入，`letter` 为空（未绑定），不报错

| | | 22. 节日绑定信件 | | |

---

### 22b. 农历节日（`lunar` 对节日解禁）

> 实现：`calendar/CalendarEvent`（`lunar` 与类型解耦，构造函数不再按 `type` 清零）、`calendar/http/CalendarApiJson`（去掉 POST/PUT 的「lunar 仅生日」校验）、`calendar/CalendarData.on(LocalDate)`（按公历日命中，REST `/today` 也走它）、`web/calendar_admin.html`（农历勾选常驻，只切日期上限）。
> 语义：节日与生日共用同一套农历换算（`LunarCalendar`）——标了农历的节日存「农历月-日」，逐年换算成公历日展示与投递；春节/中秋/端午这类节日从此不必每年手改日期。
> 展示与排期同源：GUI 的 `occurrenceIn` 与投递的 `nextOccurrenceOn` 都走农历分支，不会各算各的。

- [ ] **REST 接受农历节日**：`POST /events` 带 `{"type":"festival","lunar":true,"month":8,"day":15,"letter":"zhongqiu"}` → 201 且返回体 `"lunar":true`（旧行为是 400「lunar is only for birthday events」）
- [ ] **PUT 可切换**：对已有节日 `PUT {"lunar":true}` → 200 且 `"lunar":true`（**不再静默吞掉**）；`PUT {"day":31}` 在 `lunar` 已为真时 → 400「day must be 1-30 for a lunar date」
- [ ] **合并后校验**：把公历节日的 `day` 改成 30 再把 `lunar` 置 true → 按农历 30 天规则放行；`month=13`/`day=45` 仍 400
- [ ] **管理页**：表单农历勾选在「节日」类型下**可选**（不再禁用/清空），勾选后日期上限变 30，标签为「日期按农历算（春节/中秋等）」；新增与编辑回填都正确；取消勾选上限回到 31
- [ ] **游戏内 GUI 摆放**：给一条农历节日（如农历八月十五）→ GUI 里落在**换算后的公历格子**（2026 年 → 9-25 格），tooltip 图标为 🎉、名字后带「（农历八月十五）」；翻到 2027 年改落 9-15 格
- [ ] **当页「今日活动」**：`GET /today` 在农历节日换算出的公历当天命中该节日（管理页页头显示其名称）；非当天不显示
- [ ] **节日绑定信按农历投递**：把某封信绑到农历节日（`letter`），农历日期换算出的公历日当天 → 重载后全服各收一份；`tothesky_letters.dat` 键为 `<信件>|<昵称>|农历08-15`（与公历节日的 `<信件>|<昵称>|08-15` 键并存，互补顶掉）
- [ ] **逐年跟随**：农历节日投出一次后 `next_due` 推进到**下一个农历年**对应的公历日（逐年漂移，不是固定公历日）；改系统日期到次年该公历日再次投递
- [ ] **旧存档兼容**：升级前存下的节日（无 `lunar` 字段）照旧按公历读；节日带 `"lunar":true` 的旧脏数据（曾被构造器清零）不会突然变农历——镜像/SavedData 里的值就是最终值

| | | 22b. 农历节日 | | |

---

### 测试结果记录

| 日期 | 测试人 | 分组 | 结果 | 备注 |
|------|--------|------|------|------|
| | | 0. 存档迁移 | | |
| | | 1. 注册回归 | | |
| | | 2. 食物物品行为 | | |
| | | 3. 食材/产线 | | |
| | | 4. 工具/道具 | | |
| | | 5. 乐器系统 | | |
| | | 6. 方块行为 | | |
| | | 7. 鸡尾酒系统 | | |
| | | 8. 状态效果 | | |
| | | 9. 流体 | | |
| | | 10. 售货机/扭蛋机 | | |
| | | 11. 动力雕刻台 | | |
| | | 12. 检查点 | | |
| | | 13. 其他逻辑 | | |
| | | 14. 配方抽测 | | |
| | | 15. 资源渲染 | | |
| | | 16. 模组兼容 | | |
| | | 16b. Crystal Clear 移植 | | |
| | | 18. 锁链物流策略 | | |
| | | 19. 往来邮件转接 | | |
| | | 20. 节日信 | | |
| | | 20b. 网页信件编辑器 | | |
| | | 21. 生日信 | | |
| | | 21b. 农历生日 | | |
| | | 22. 节日绑定信件 | | |
| | | 22b. 农历节日 | | |
