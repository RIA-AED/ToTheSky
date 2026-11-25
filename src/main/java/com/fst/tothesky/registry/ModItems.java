package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.item.SpecialFoodItems;
import com.fst.tothesky.item.TooltipBlockItem;
import com.fst.tothesky.item.TooltipItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册：特色食品与其食材。
 * 数值移植自 kubejs/startup_scripts/registry.js（KubeJS 未写 saturation 时默认 0.5）。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ToTheSky.MODID);

    /** 农夫乐事的滋养效果；FD 未安装时为 null，食物效果被跳过 */
    private static net.minecraft.world.effect.MobEffect nourishment() {
        return ForgeRegistries.MOB_EFFECTS.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("farmersdelight", "nourishment"));
    }

    // ---------------- 方块物品 ----------------
    public static final RegistryObject<TooltipBlockItem> RAMEN = ITEMS.register("ramen",
            () -> new TooltipBlockItem(ModBlocks.RAMEN.get(), new Item.Properties(), "ramen", 0));
    public static final RegistryObject<TooltipBlockItem> PIZZA_MARGARITA = ITEMS.register("pizza_margarita",
            () -> new TooltipBlockItem(ModBlocks.PIZZA_MARGARITA.get(), new Item.Properties(), "pizza_margarita", 3));
    public static final RegistryObject<TooltipBlockItem> PORK_PIZZA = ITEMS.register("pork_pizza",
            () -> new TooltipBlockItem(ModBlocks.PORK_PIZZA.get(), new Item.Properties(), "pork_pizza", 2));
    public static final RegistryObject<TooltipBlockItem> APPLE_PIZZA = ITEMS.register("apple_pizza",
            () -> new TooltipBlockItem(ModBlocks.APPLE_PIZZA.get(), new Item.Properties(), "apple_pizza", 4));

    // ---------------- 特色食品 ----------------
    // 巧克力意面
    public static final RegistryObject<Item> PASTA_WITH_CHOCOLATE = ITEMS.register("pasta_with_chocolate",
            () -> new TooltipItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(12).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.BAD_OMEN, 600, 1), 0.02f)
                    .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200, 2), 0.75f)
                    .build()), "pasta_with_chocolate", 3));
    // 焦糖鳕鱼羹
    public static final RegistryObject<Item> CARAMEL_COD_SOUP = ITEMS.register("caramel_cod_soup",
            () -> new SpecialFoodItems.CaramelCodSoup(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(10).saturationMod(0.5f)
                    .build())));
    // 健胃消食片
    public static final RegistryObject<Item> DIGESTION_PELLOW = ITEMS.register("digestion_pellow",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(0).saturationMod(0.5f)
                    .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 300, 79), 1.0f)
                    .alwaysEat()
                    .build()), "digestion_pellow", 7));

    // 鱿鱼狂欢节（可放置方块形态；吃完返碗）
    public static final RegistryObject<Item> SQUID_FESTIVAL = ITEMS.register("squid_festival",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.SQUID_FESTIVAL_BLOCK.get(),
                    new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                            .nutrition(12).saturationMod(1.0f)
                            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200, 5), 1.0f)
                            .build()),
                    SpecialFoodItems::squidFestivalEaten));
    // 深海鳕鱼堡
    public static final RegistryObject<Item> COD_BURGER = ITEMS.register("cod_burger",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(12).saturationMod(1.0f)
                    .build()), "cod_burger", 6));
    // 油炸鳕鱼
    public static final RegistryObject<Item> FRIED_COD = ITEMS.register("fried_cod",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(8).saturationMod(1.0f)
                    .build()), "fried_cod", 5));
    // 切制奶酪
    public static final RegistryObject<Item> CUT_CHEESE = ITEMS.register("cut_cheese",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(1.0f)
                    .build()), "cut_cheese", 2));
    // 切片玛格丽特披萨
    public static final RegistryObject<Item> SLICED_PIZZA_MARGARITA = ITEMS.register("sliced_pizza_margarita",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(nourishment(), 1200, 1), 1.0f)
                    .build()), "sliced_pizza_margarita", 3));
    // 切片猪肉碎披萨
    public static final RegistryObject<Item> SLICED_PORK_PIZZA = ITEMS.register("sliced_pork_pizza",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5).saturationMod(1.2f)
                    .effect(() -> new MobEffectInstance(nourishment(), 1800, 1), 1.0f)
                    .build()), "sliced_pork_pizza", 3));
    // 切片苹果披萨
    public static final RegistryObject<Item> SLICED_APPLE_PIZZA = ITEMS.register("sliced_apple_pizza",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(nourishment(), 1300, 1), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.BAD_OMEN, 600, 1), 0.02f)
                    .build()), "sliced_apple_pizza", 3));
    // 幻翼虾仁（可放置方块形态；发光+礼花）
    public static final RegistryObject<Item> PHANTOM_SHRIMP = ITEMS.register("phantom_shrimp",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.PHANTOM_SHRIMP_BLOCK.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(7).saturationMod(1.5f)
                            .build()),
                    SpecialFoodItems::phantomShrimpEaten));

    // 三角粥
    public static final RegistryObject<Item> DELTA_PORRIDGE = ITEMS.register("delta_porridge",
            () -> new SpecialFoodItems.DeltaPorridge(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(9).saturationMod(0.8f)
                    .build())));
    // 饮品659（可放置方块形态；记录回溯点+rewind）

    // 饮品659（可放置方块形态；记录回溯点+rewind）
    public static final RegistryObject<Item> DRINK_659 = ITEMS.register("drink659",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.DRINK_659_BLOCK.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(2).saturationMod(1.5f)
                            .alwaysEat()
                            .build()),
                    SpecialFoodItems::drink659Eaten));
    // 晴天鳕鱼（可放置方块形态；雨过天晴）
    public static final RegistryObject<Item> SUNSHINE_COD = ITEMS.register("sunshine_cod",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.SUNSHINE_COD_BLOCK.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(5).saturationMod(1.5f)
                            .alwaysEat()
                            .build()),
                    SpecialFoodItems::sunshineCodEaten));
    // 温泉蛋牛肉盖饭（可放置方块形态）
    public static final RegistryObject<Item> BEEF_OVER_RICE = ITEMS.register("beef_over_rice",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.BEEF_OVER_RICE_BLOCK.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(10).saturationMod(0.6f)
                            .alwaysEat()
                            .build()),
                    null));
    /** 劲爆鳕鱼堡：入口即爆（kjs foodEaten 爆炸行为由 BombCodBurger 实现） */
    public static final RegistryObject<Item> BOMB_COD_BURGER = ITEMS.register("bomb_cod_burger",
            () -> new SpecialFoodItems.BombCodBurger(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(14).saturationMod(14.0f).build())));
    // 秘封洋葱绿叶肥虫汤
    public static final RegistryObject<Item> BUG_SOUP = ITEMS.register("bug_soup",
            () -> new SpecialFoodItems.BugSoup(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(0.2f)
                    .effect(() -> new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0), 1.0f)
                    .alwaysEat()
                    .build())));
    // ---------------- 饺子（仅注册；玩法逻辑仍由脚本提供） ----------------
    /** 饺子皮：脚本侧长按包制 */
    public static final RegistryObject<Item> DUMPLING_WRAPPER = simple("dumpling_wrapper", 0);
    /** 生饺子：带 filling/author NBT，8 个加碗可合成一盘生饺子 */
    public static final RegistryObject<Item> RAW_DUMPLING = simple("raw_dumpling", 0);
    /** 一盘生饺子（不可堆叠） */
    public static final RegistryObject<Item> RAW_DUMPLING_PLATE = ITEMS.register("raw_dumpling_plate",
            () -> new Item(new Item.Properties().stacksTo(1)));
    /** 饺子：数值对齐旧 kjs（4 饥饿 / 1.0 饱和度 / 快速进食）；吃下后按馅料生效 */
    public static final RegistryObject<Item> COOKED_DUMPLING = ITEMS.register("cooked_dumpling",
            () -> new com.fst.tothesky.item.CookedDumplingItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(1.0f)
                    .fast()
                    .build())));
    /** 一盘熟饺子（方块物品，不可堆叠；放置时把内容物写入方块实体） */
    public static final RegistryObject<com.fst.tothesky.item.CookedDumplingPlateItem> COOKED_DUMPLING_PLATE_ITEM =
            ITEMS.register("cooked_dumpling_plate",
                    () -> new com.fst.tothesky.item.CookedDumplingPlateItem(ModBlocks.COOKED_DUMPLING_PLATE.get(),
                            new Item.Properties().stacksTo(1)));

    // ---------------- 食材 ----------------
    public static final RegistryObject<Item> CHEESE = simple("cheese", 2);
    public static final RegistryObject<Item> PIZZA_BASE = simple("pizza_base", 3);
    public static final RegistryObject<Item> RAW_PIZZA_MARGARITA = simple("raw_pizza_margarita", 2);
    public static final RegistryObject<Item> RAW_PORK_PIZZA = simple("raw_pork_pizza", 2);
    public static final RegistryObject<Item> RAW_APPLE_PIZZA = simple("raw_apple_pizza", 2);
    public static final RegistryObject<Item> RAW_SUNSHINE_COD = simple("raw_sunshine_cod", 0);
    // ---------------- 下界合金产线材料 ----------------
    /** 血瓶：下界溶液原料（移植自 kjs，maxStackSize 1，用完返还玻璃瓶） */
    public static final RegistryObject<Item> BLOOD_BOTTLE = ITEMS.register("blood_bottle",
            () -> new Item(new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE)));
    /** 不纯合金坯：钻石+铁+金 超高温混合 */
    public static final RegistryObject<Item> IMPURE_ALLOY_BASE = simple("impure_alloy_base", 0);
    /** 粗制合金坯：不纯合金坯+岩浆 压实 */
    public static final RegistryObject<Item> RAW_ALLOY_BASE = simple("raw_alloy_base", 0);
    /** 半成品合金：下界合金序列组装的中间品 */
    public static final RegistryObject<Item> INCOMPLETE_NETHERITE_INGOT = simple("incomplete_netherite_ingot", 0);
    /** 魔女因子：AE2 奇点 + 下界合金碎片 + 灵魂珠 + 下界溶液 超高温压实 */
    public static final RegistryObject<Item> WITCH_FACTOR = simple("witch_factor", 0);
    /** 活化的魔女因子：魔女因子经 createaddition 充电活化（饮品659 原料） */
    public static final RegistryObject<Item> ACTIVATED_WITCH_FACTOR = simple("activated_witch_factor", 0);
    /** 三角尘：三角币掰碎的粉末（饮品659 原料；上游来自三角币经济系统） */
    public static final RegistryObject<Item> DELTA_DUST = simple("delta_dust", 0);
    /** 三角币：经济系统货币（生晴天鳕鱼原料；上游来自 sell&roll 经济系统） */
    public static final RegistryObject<Item> DELTA_COIN = simple("delta_coin", 0);
    /** 三角片：三角币掰碎（三角粥原料；上游来自经济系统） */
    public static final RegistryObject<Item> DELTA_COIN_CHIP = simple("delta_coin_chip", 0);

    // ---------------- 售货机 / 扭蛋机 ----------------
    /** 抽奖券：右键扭蛋机绑定 key，消费时需手持匹配的券 */
    public static final RegistryObject<Item> ROLLER_TICKET = simple("roller_ticket", 0);
    /** 售货机方块物品 */
    public static final RegistryObject<BlockItem> SELLER_ITEM =
            ITEMS.register("seller", () -> new BlockItem(ModBlocks.SELLER.get(), new Item.Properties()));
    /** 扭蛋机方块物品 */
    public static final RegistryObject<BlockItem> ROLLER_ITEM =
            ITEMS.register("roller", () -> new BlockItem(ModBlocks.ROLLER.get(), new Item.Properties()));

    // ---------------- 医疗/工具 ----------------
    /** 采血套装plus：右键抽血换血瓶（耐久 13） */
    public static final RegistryObject<Item> HEMOSTIX_PLUS = ITEMS.register("hemostix_plus",
            () -> new com.fst.tothesky.item.HemostixPlusItem(new Item.Properties().durability(13)));
    /** 收割黑夜：右键肃清 150 格内普通幻翼（耐久 200，发光） */
    public static final RegistryObject<Item> HARVEST_THE_NIGHT = ITEMS.register("harvest_the_night",
            () -> new com.fst.tothesky.item.HarvestTheNightItem(new Item.Properties().stacksTo(1).durability(200)));
    /** 竹蜻蜓：右键获得漂浮 III（耐久 20） */
    public static final RegistryObject<Item> COPTER = ITEMS.register("copter",
            () -> new com.fst.tothesky.item.CopterItem(new Item.Properties().stacksTo(1).durability(20)));
    /** 机械手润滑剂：右键机械手注入公共 Owner（耐久 100） */
    public static final RegistryObject<Item> DEPLOYER_LUBRICANT = ITEMS.register("deployer_lubricant",
            () -> new Item(new Item.Properties().stacksTo(1).durability(100)));

    // ---------------- 图腾/钻石/石墨产线 ----------------
    public static final RegistryObject<Item> EMERALD_NUGGET = simple("emerald_nugget", 0);
    public static final RegistryObject<Item> RAW_TOTEM = simple("raw_totem", 0);
    public static final RegistryObject<Item> INCOMPLETE_TOTEM = simple("incomplete_totem", 0);
    public static final RegistryObject<Item> FIBER_MIXTURE = simple("fiber_mixture", 0);
    public static final RegistryObject<Item> FROTHER_MIXTURE = simple("frother_mixture", 0);
    /**
     * 刻痕玉米饼：旧 KubeJS 玉米饼产线的过渡件，同时作为唱片——右键唱片机播放
     * Never gonna give you up（旧脚本 registry.js 注释掉的 {@code event.create('tortilla', "music_disc")} 段）。
     * 唱片必须落在 {@code minecraft:music_discs} 物品标签里，否则 {@code JukeboxBlockEntity} 的 setItem 会拒收
     * （见 data/minecraft/tags/items/music_discs.json）；comparator 输出沿用旧脚本的 index + 1 = 1。
     * 音轨 70.80 s（ogg 末页 granule 3122176 / 44100 Hz），唱片机按 tick 数判定结束，故取 1416 tick。
     */
    public static final RegistryObject<Item> INCOMPLETE_TORTILLA = ITEMS.register("incomplete_tortilla",
            () -> new RecordItem(1, ModSounds.MUSIC_NEVER_GONNA_GIVE_YOU_UP,
                    new Item.Properties().stacksTo(1), 1416));
    public static final RegistryObject<Item> HE_GRAPHITE = simple("he_graphite", 0);
    public static final RegistryObject<Item> SMALL_CRYSTAL = simple("small_crystal", 0);
    public static final RegistryObject<Item> FADED_SMALL_CRYSTAL = simple("faded_small_crystal", 0);
    /** 钻石核心 / 未完成的钻石：钻石产线中间品（Create 序列装配链） */
    public static final RegistryObject<Item> DIAMOND_CORE = simple("diamond_core", 0);
    public static final RegistryObject<Item> UNCOMPLETE_DIAMOND = simple("uncomplete_diamond", 0);
    public static final RegistryObject<Item> SWEET_BEAN_CURD = ITEMS.register("sweet_bean_curd",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.SWEET_BEAN_CURD_BLOCK.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(8).saturationMod(1f).build()),
                    com.fst.tothesky.item.BeanCurdItem::bowlReturn));
    public static final RegistryObject<Item> BEAN_CURD = simple("bean_curd", 0);
    public static final RegistryObject<Item> CUT_BEAN_CURD = ITEMS.register("cut_bean_curd",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3).saturationMod(0f).build())));
    public static final RegistryObject<Item> SALTY_BEAN_CURD = ITEMS.register("salty_bean_curd",
            () -> new com.fst.tothesky.item.PlaceableFoodBlockItem(
                    ModBlocks.SALTY_BEAN_CURD_BLOCK.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(8).saturationMod(1f).build()),
                    com.fst.tothesky.item.BeanCurdItem::bowlReturn));
    public static final RegistryObject<Item> SPICY_BEAN_CURD = ITEMS.register("spicy_bean_curd",
            () -> new com.fst.tothesky.item.BeanCurdItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(8).saturationMod(1f)
                    .effect(() -> new MobEffectInstance(nourishment(), 600, 0), 1.0f).build()), false));
    public static final RegistryObject<Item> BERRY_BEAN_CURD = ITEMS.register("berry_bean_curd",
            () -> new com.fst.tothesky.item.BeanCurdItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(10).saturationMod(1f)
                    .effect(() -> new MobEffectInstance(nourishment(), 600, 0), 1.0f).build()), true));
    /** 酱油瓶 */
    public static final RegistryObject<Item> SOY_SAUSE_BOTTLE = ITEMS.register("soy_sause_bottle",
            () -> new Item(new Item.Properties().stacksTo(16)));
    /** 大豆油（瓶装，可作烹饪油） */
    public static final RegistryObject<Item> SOY_BEAN_OIL = ITEMS.register("soy_bean_oil",
            () -> new Item(new Item.Properties().stacksTo(16)));

    // ---------------- 乐器 ----------------
    public static final RegistryObject<Item> GUITAR = ITEMS.register("guitar",
            () -> new com.fst.tothesky.item.InstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PIANO = ITEMS.register("piano",
            () -> new com.fst.tothesky.item.InstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DRUM_808 = ITEMS.register("drum_808",
            () -> new com.fst.tothesky.item.InstrumentItem(new Item.Properties().stacksTo(1)));
    /** 空白乐谱：右键 + 副手成书 → 转为乐谱 */
    public static final RegistryObject<Item> EMPTY_MUSIC_SHEET = ITEMS.register("empty_music_sheet",
            () -> new com.fst.tothesky.item.EmptyMusicSheetItem(new Item.Properties().stacksTo(16)));
    /** 乐谱：右键学习（写入 config/musicSheets/<玩家>/） */
    public static final RegistryObject<Item> MUSIC_SHEET = ITEMS.register("music_sheet",
            () -> new com.fst.tothesky.item.MusicSheetItem(new Item.Properties().stacksTo(1)));

    // ---------------- 魔法照片 ----------------
    public static final RegistryObject<Item> BLUE_MAGIC_STONE = simple("blue_magic_stone", 0);
    public static final RegistryObject<Item> RED_MAGIC_STONE = simple("red_magic_stone", 0);
    public static final RegistryObject<Item> YELLOW_MAGIC_STONE = simple("yellow_magic_stone", 0);
    public static final RegistryObject<Item> GREEN_MAGIC_STONE = simple("green_magic_stone", 0);

    // ---------------- 春节 ----------------
    public static final RegistryObject<Item> FIRECRACKER = simple("firecracker", 0);
    public static final RegistryObject<Item> SPARKLER = ITEMS.register("sparkler",
            () -> new com.fst.tothesky.item.SparklerItem(new Item.Properties()));

    // ---------------- 索拉里斯勋章 ----------------
    public static final RegistryObject<Item> SOLARIS0 = ITEMS.register("solaris0",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SOLARIS1 = ITEMS.register("solaris1",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SOLARIS2 = ITEMS.register("solaris2",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // ---------------- 酿酒方块物品（仅方块，逻辑不迁移） ----------------
    public static final RegistryObject<BlockItem> DISTILLER_ITEM =
            ITEMS.register("distiller", () -> new BlockItem(ModBlocks.DISTILLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> FERMENT_CONTAINER_ITEM =
            ITEMS.register("ferment_container", () -> new BlockItem(ModBlocks.FERMENT_CONTAINER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> AGING_CONTAINER_ITEM =
            ITEMS.register("aging_container", () -> new BlockItem(ModBlocks.AGING_CONTAINER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> WINE_CRAFTING_TABLE_ITEM =
            ITEMS.register("wine_crafting_table", () -> new BlockItem(ModBlocks.WINE_CRAFTING_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<com.fst.tothesky.item.RollingStoneRecordItem> ROLLING_STONE =
            ITEMS.register("rolling_stone", com.fst.tothesky.item.RollingStoneRecordItem::new);

    public static final RegistryObject<TooltipBlockItem> LABLE_PRINTER_ITEM =
            ITEMS.register("lable_printer", () -> new TooltipBlockItem(ModBlocks.LABLE_PRINTER.get(), new Item.Properties(), "lable_printer", 4));
    /** 你不该拿到的酒（酿酒逻辑未迁移，仅占位） */
    public static final RegistryObject<Item> WINE_BOTTLE = ITEMS.register("wine_bottle",
            () -> new Item(new Item.Properties().stacksTo(1).food(new FoodProperties.Builder()
                    .nutrition(0).alwaysEat().build())));
    public static final RegistryObject<Item> INCOMPLETE_WINE_BOTTLE = ITEMS.register("incomplete_wine_bottle",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<TooltipBlockItem> STREETLIGHT_ITEM =
            ITEMS.register("streetlight", () -> new TooltipBlockItem(ModBlocks.STREETLIGHT.get(),
                    new Item.Properties(), "streetlight", 2));

    // ---------------- 装饰方块物品 ----------------
    public static final RegistryObject<BlockItem> CANDLE_STICK_ITEM =
            ITEMS.register("candle_stick", () -> new BlockItem(ModBlocks.CANDLE_STICK.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> BURGER_ITEM =
            ITEMS.register("burger", () -> new BlockItem(ModBlocks.BURGER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> NANAKO_SCULPTURE_ITEM =
            ITEMS.register("nanako_sculpture", () -> new BlockItem(ModBlocks.NANAKO_SCULPTURE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> GOLDEN_COOKING_POT_ITEM =
            ITEMS.register("golden_cooking_pot", () -> new BlockItem(ModBlocks.GOLDEN_COOKING_POT.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> GOLDEN_SKILLET_ITEM =
            ITEMS.register("golden_skillet", () -> new BlockItem(ModBlocks.GOLDEN_SKILLET.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> SILVER_COOKING_POT_ITEM =
            ITEMS.register("silver_cooking_pot", () -> new BlockItem(ModBlocks.SILVER_COOKING_POT.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> COPPER_COOKING_POT_ITEM =
            ITEMS.register("copper_cooking_pot", () -> new BlockItem(ModBlocks.COPPER_COOKING_POT.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> EVENT_BLOCK_1_ITEM =
            ITEMS.register("event_block_1", () -> new BlockItem(ModBlocks.EVENT_BLOCK_1.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> EVENT_BLOCK_2_ITEM =
            ITEMS.register("event_block_2", () -> new BlockItem(ModBlocks.EVENT_BLOCK_2.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> EVENT_BLOCK_3_ITEM =
            ITEMS.register("event_block_3", () -> new BlockItem(ModBlocks.EVENT_BLOCK_3.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HE_GRAPHITE_BLOCK_ITEM =
            ITEMS.register("he_graphite_block", () -> new BlockItem(ModBlocks.HE_GRAPHITE_BLOCK.get(), new Item.Properties()));
    /** 检查站方块物品 */
    public static final RegistryObject<BlockItem> CHECKER_ITEM =
            ITEMS.register("checker", () -> new BlockItem(ModBlocks.CHECKER.get(), new Item.Properties()));

    // ---------------- 春节方块物品 ----------------
    public static final RegistryObject<BlockItem> MULTIPLE_FIREWORKS_ITEM =
            ITEMS.register("multiple_fireworks", () -> new BlockItem(ModBlocks.MULTIPLE_FIREWORKS.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> FIREWORKS_BOX_ITEM =
            ITEMS.register("fireworks_box", () -> new BlockItem(ModBlocks.FIREWORKS_BOX.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> MULTIPLE_FIRECRACKERS_ITEM =
            ITEMS.register("multiple_firecrackers", () -> new BlockItem(ModBlocks.MULTIPLE_FIRECRACKERS.get(), new Item.Properties()));

    // ---------------- 日历 ----------------
    /** 日历方块物品 */
    public static final RegistryObject<BlockItem> CALENDAR_ITEM =
            ITEMS.register("calendar", () -> new BlockItem(ModBlocks.CALENDAR.get(), new Item.Properties()));

    // ---------------- PR#59 动力雕刻台 / 包装颜料 ----------------
    public static final RegistryObject<BlockItem> MECHANICAL_CHISEL_TABLE_ITEM =
            ITEMS.register("mechanical_chisel_table", () -> new BlockItem(ModBlocks.MECHANICAL_CHISEL_TABLE.get(), new Item.Properties()));
    /** 包装颜料：潜行右键拆包；NBT StoredColors 记录内含染料 */
    public static final RegistryObject<Item> PACKED_COLORS = ITEMS.register("packed_colors",
            () -> new com.fst.tothesky.item.PackedColorsItem(new Item.Properties()));

    // ---------------- PR#56 遗忘之露 ----------------
    /** 遗忘之露：对驯服生物使用，解除其主人绑定 */
    public static final RegistryObject<Item> DEW_OF_OBLIVION = ITEMS.register("dew_of_oblivion",
            () -> new com.fst.tothesky.item.DewOfOblivionItem(new Item.Properties()));

    private static RegistryObject<Item> simple(String name, int tooltipLines) {
        return ITEMS.register(name, () -> tooltipLines > 0
                ? new TooltipItem(new Item.Properties(), name, tooltipLines)
                : new Item(new Item.Properties()));
    }

    private ModItems() {
    }
}
