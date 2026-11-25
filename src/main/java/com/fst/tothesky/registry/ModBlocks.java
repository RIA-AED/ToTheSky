package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.CookedDumplingPlateBlock;
import com.fst.tothesky.block.PlaceableFoodBlock;
import com.fst.tothesky.block.DrinkGlassBlock;
import com.fst.tothesky.block.EmptyGlassBlock;
import com.fst.tothesky.block.PizzaBlock;
import com.fst.tothesky.block.RollerBlock;
import com.fst.tothesky.block.SellerBlock;
import com.fst.tothesky.block.RamenBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 方块注册：拉面、三种披萨、十二种可放置鸡尾酒与三种空杯。
 * 移植自 kubejs/startup_scripts/registry.js 与 drink_block_registry.js。
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ToTheSky.MODID);

    public static final RegistryObject<com.fst.tothesky.rollingstone.RollingStoneBlock> ROLLING_STONE =
            BLOCKS.register("rolling_stone", com.fst.tothesky.rollingstone.RollingStoneBlock::new);

    private static final BlockBehaviour.Properties FOOD_BLOCK_PROPS =
            BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(1.0f).noOcclusion();

    private static BlockBehaviour.Properties glassProps() {
        return BlockBehaviour.Properties.of().sound(SoundType.GLASS).strength(0.1f).noOcclusion();
    }

    // 拉面：右键分五口吃掉，吃完返碗
    public static final RegistryObject<RamenBlock> RAMEN =
            BLOCKS.register("ramen", () -> new RamenBlock(FOOD_BLOCK_PROPS));

    // 一盘熟饺子：bite 0-9 + facing 与旧 KubeJS 方块一致，内容物存在方块实体的 data 标签里（无掉落）
    public static final RegistryObject<CookedDumplingPlateBlock> COOKED_DUMPLING_PLATE =
            BLOCKS.register("cooked_dumpling_plate",
                    () -> new CookedDumplingPlateBlock(FOOD_BLOCK_PROPS.noLootTable()));

    // 披萨：右键取片，取完四片消失
    public static final RegistryObject<PizzaBlock> PIZZA_MARGARITA =
            BLOCKS.register("pizza_margarita", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_PIZZA_MARGARITA));
    public static final RegistryObject<PizzaBlock> PORK_PIZZA =
            BLOCKS.register("pork_pizza", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_PORK_PIZZA));
    public static final RegistryObject<PizzaBlock> APPLE_PIZZA =
            BLOCKS.register("apple_pizza", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_APPLE_PIZZA));

    // ---------------- 旧披萨阶段方块（存档兼容，无物品无掉落） ----------------
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> PIZZA_MARGARITA_4 =
            BLOCKS.register("pizza_margarita4", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> null, ModItems.SLICED_PIZZA_MARGARITA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> PORK_PIZZA_4 =
            BLOCKS.register("pork_pizza4", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> null, ModItems.SLICED_PORK_PIZZA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> APPLE_PIZZA_4 =
            BLOCKS.register("apple_pizza4", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> null, ModItems.SLICED_APPLE_PIZZA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> PIZZA_MARGARITA_3 =
            BLOCKS.register("pizza_margarita3", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> PIZZA_MARGARITA_4.get(), ModItems.SLICED_PIZZA_MARGARITA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> PORK_PIZZA_3 =
            BLOCKS.register("pork_pizza3", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> PORK_PIZZA_4.get(), ModItems.SLICED_PORK_PIZZA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> APPLE_PIZZA_3 =
            BLOCKS.register("apple_pizza3", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> APPLE_PIZZA_4.get(), ModItems.SLICED_APPLE_PIZZA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> PIZZA_MARGARITA_2 =
            BLOCKS.register("pizza_margarita2", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> PIZZA_MARGARITA_3.get(), ModItems.SLICED_PIZZA_MARGARITA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> PORK_PIZZA_2 =
            BLOCKS.register("pork_pizza2", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> PORK_PIZZA_3.get(), ModItems.SLICED_PORK_PIZZA));
    public static final RegistryObject<com.fst.tothesky.block.PizzaStageBlock> APPLE_PIZZA_2 =
            BLOCKS.register("apple_pizza2", () -> new com.fst.tothesky.block.PizzaStageBlock(
                    FOOD_BLOCK_PROPS.noLootTable(), () -> APPLE_PIZZA_3.get(), ModItems.SLICED_APPLE_PIZZA));

    // 空杯
    public static final RegistryObject<EmptyGlassBlock> MARTINI_GLASS =
            BLOCKS.register("martini_glass", () -> new EmptyGlassBlock(glassProps(), 10));
    public static final RegistryObject<EmptyGlassBlock> HURRICANE_GLASS =
            BLOCKS.register("hurricane_glass", () -> new EmptyGlassBlock(glassProps(), 11));
    public static final RegistryObject<EmptyGlassBlock> OLD_FASHIONED_GLASS =
            BLOCKS.register("old_fashioned_glass", () -> new EmptyGlassBlock(glassProps(), 7));

    // 鸡尾酒方块（无物品形态），对应关系移植自 place_drink_block.js 的 COCKTAIL_MAP
    public static final RegistryObject<DrinkGlassBlock> JULY_21_BLOCK =
            martini("july_21_block", "july_21");
    public static final RegistryObject<DrinkGlassBlock> TSUNDERE_HEROINE_BLOCK =
            martini("tsundere_heroine_block", "tsundere_heroine");
    public static final RegistryObject<DrinkGlassBlock> SWEET_BERRY_MARTINI_BLOCK =
            martini("sweet_berry_martini_block", "sweet_berry_martini");
    public static final RegistryObject<DrinkGlassBlock> BIRCH_SAP_VODKA_BLOCK =
            martini("birch_sap_vodka_block", "birch_sap_vodka");
    public static final RegistryObject<DrinkGlassBlock> RED_LIZARD_BLOCK =
            hurricane("red_lizard_block", "red_lizard");
    public static final RegistryObject<DrinkGlassBlock> SECOND_GUESS_BLOCK =
            hurricane("second_guess_block", "second_guess");
    public static final RegistryObject<DrinkGlassBlock> LIGHT_YELLOW_FIREFLY_BLOCK =
            hurricane("light_yellow_firefly_block", "light_yellow_firefly");
    public static final RegistryObject<DrinkGlassBlock> SHOOTING_STAR_BLOCK =
            hurricane("shooting_star_block", "shooting_star");
    public static final RegistryObject<DrinkGlassBlock> TWILIGHT_FOREST_BLOCK =
            oldFashioned("twilight_forest_block", "twilight_forest");
    public static final RegistryObject<DrinkGlassBlock> JACKS_STORY_BLOCK =
            oldFashioned("jacks_story_block", "jacks_story");
    public static final RegistryObject<DrinkGlassBlock> SHANGHAI_BEACH_BLOCK =
            oldFashioned("shanghai_beach_block", "shanghai_beach");
    public static final RegistryObject<DrinkGlassBlock> BANE_OF_ARTHROPODS_BLOCK =
            oldFashioned("bane_of_arthropods_block", "bane_of_arthropods");

    /** 所有鸡尾酒方块，供放置事件按鸡尾酒 id 反查 */
    public static final List<RegistryObject<DrinkGlassBlock>> COCKTAIL_BLOCKS = List.of(
            JULY_21_BLOCK, TSUNDERE_HEROINE_BLOCK, SWEET_BERRY_MARTINI_BLOCK, BIRCH_SAP_VODKA_BLOCK,
            RED_LIZARD_BLOCK, SECOND_GUESS_BLOCK, LIGHT_YELLOW_FIREFLY_BLOCK, SHOOTING_STAR_BLOCK,
            TWILIGHT_FOREST_BLOCK, JACKS_STORY_BLOCK, SHANGHAI_BEACH_BLOCK, BANE_OF_ARTHROPODS_BLOCK);

    // ---------------- 售货机 / 扭蛋机 ----------------
    public static final RegistryObject<SellerBlock> SELLER =
            BLOCKS.register("seller", SellerBlock::new);
    public static final RegistryObject<RollerBlock> ROLLER =
            BLOCKS.register("roller", RollerBlock::new);

    // ---------------- 酿酒（仅方块，逻辑不迁移） ----------------
    public static final RegistryObject<Block> DISTILLER =
            BLOCKS.register("distiller", () -> new com.fst.tothesky.block.FacingBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL).strength(2.0f).noOcclusion()));
    public static final RegistryObject<Block> FERMENT_CONTAINER =
            BLOCKS.register("ferment_container", () -> new com.fst.tothesky.block.FacingBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL).strength(2.0f).noOcclusion()));
    public static final RegistryObject<Block> AGING_CONTAINER =
            BLOCKS.register("aging_container", () -> new com.fst.tothesky.block.FacingBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL).strength(2.0f).noOcclusion()));
    public static final RegistryObject<Block> WINE_CRAFTING_TABLE =
            BLOCKS.register("wine_crafting_table", () -> new com.fst.tothesky.block.WineCraftingTableBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL).strength(2.0f).noOcclusion()));
    public static final RegistryObject<Block> LABLE_PRINTER =
            BLOCKS.register("lable_printer", () -> new com.fst.tothesky.block.LabelPrinterBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL).strength(2.0f).noOcclusion()));

    public static final RegistryObject<Block> STREETLIGHT =
            BLOCKS.register("streetlight", () -> new com.fst.tothesky.block.StreetlightBlock(
                    BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(2.0f)
                            .requiresCorrectToolForDrops().noOcclusion()
                            .lightLevel(state -> state.getValue(com.fst.tothesky.block.StreetlightBlock.LIGHT_LEVEL))));

    // ---------------- 装饰 ----------------
    public static final RegistryObject<Block> CANDLE_STICK =
            BLOCKS.register("candle_stick", () -> new com.fst.tothesky.block.SimpleShapeBlock(
                    Block.box(6, 0, 6, 10, 12, 10),
                    BlockBehaviour.Properties.of().strength(1.0f).noOcclusion().lightLevel(s -> 14)));
    /** 汉堡模型：右键彩蛋（你的嘴巴似乎被什么粘住了） */
    public static final RegistryObject<Block> BURGER =
            BLOCKS.register("burger", () -> new com.fst.tothesky.block.BurgerBlock(
                    Block.box(3, 0, 3, 13, 8, 13),
                    BlockBehaviour.Properties.of().strength(0f).noOcclusion()));
    public static final RegistryObject<Block> NANAKO_SCULPTURE =
            BLOCKS.register("nanako_sculpture", () -> new com.fst.tothesky.block.FacingShapeBlock(
                    Block.box(2, 0, 2, 14, 14, 14),
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion().lightLevel(s -> 8)));
    public static final RegistryObject<Block> GOLDEN_COOKING_POT =
            BLOCKS.register("golden_cooking_pot", () -> new com.fst.tothesky.block.SimpleShapeBlock(
                    Block.box(2, 0, 2, 14, 10, 14),
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> GOLDEN_SKILLET =
            BLOCKS.register("golden_skillet", () -> new com.fst.tothesky.block.SimpleShapeBlock(
                    Block.box(2, 0, 2, 14, 4, 14),
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> SILVER_COOKING_POT =
            BLOCKS.register("silver_cooking_pot", () -> new com.fst.tothesky.block.SimpleShapeBlock(
                    Block.box(2, 0, 2, 14, 10, 14),
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> COPPER_COOKING_POT =
            BLOCKS.register("copper_cooking_pot", () -> new com.fst.tothesky.block.SimpleShapeBlock(
                    Block.box(2, 0, 2, 14, 10, 14),
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> EVENT_BLOCK_1 =
            BLOCKS.register("event_block_1", () -> new Block(
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> EVENT_BLOCK_2 =
            BLOCKS.register("event_block_2", () -> new Block(
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> EVENT_BLOCK_3 =
            BLOCKS.register("event_block_3", () -> new Block(
                    BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(0f).noOcclusion()));
    public static final RegistryObject<Block> HE_GRAPHITE_BLOCK =
            BLOCKS.register("he_graphite_block", () -> new Block(
                    BlockBehaviour.Properties.of().strength(1.0f).requiresCorrectToolForDrops()));

    // ---------------- 检查点 ----------------
    public static final RegistryObject<Block> CHECKER =
            BLOCKS.register("checker", () -> new com.fst.tothesky.block.CheckerBlock(
                    BlockBehaviour.Properties.of().strength(1.0f)));

    // ---------------- 春节 ----------------
    /** 礼炮：打火石右键点燃，依次发射 9 发烟花后变回纸壳 */
    public static final RegistryObject<Block> MULTIPLE_FIREWORKS =
            BLOCKS.register("multiple_fireworks", () -> new com.fst.tothesky.block.MultipleFireworksBlock(
                    BlockBehaviour.Properties.of().strength(1.0f).noOcclusion()));
    /** 燃放的礼炮：临时态，无物品不掉落 */
    public static final RegistryObject<Block> FIRING_MULTIPLE_FIREWORKS =
            BLOCKS.register("firing_multiple_fireworks", () -> new Block(
                    BlockBehaviour.Properties.of().strength(60.0f).noOcclusion().noLootTable()));
    /** 礼炮纸壳：礼炮燃尽后的残骸，不掉落 */
    public static final RegistryObject<Block> FIREWORKS_BOX =
            BLOCKS.register("fireworks_box", () -> new Block(
                    BlockBehaviour.Properties.of().strength(0.3f).noOcclusion().noLootTable()));
    /** 鞭炮：打火石右键点燃，爆响后消失并连锁引爆周围鞭炮 */
    public static final RegistryObject<Block> MULTIPLE_FIRECRACKERS =
            BLOCKS.register("multiple_firecrackers", () -> new com.fst.tothesky.block.MultipleFirecrackersBlock(
                    Block.box(2, 0, 2, 14, 2, 14),
                    BlockBehaviour.Properties.of().strength(1.0f).noOcclusion()));

    // ---------------- 日历 ----------------
    /** 日历：贴墙悬挂的四向朝向方块，右键打开月视图 GUI（活动数据经 REST API 管理） */
    public static final RegistryObject<Block> CALENDAR =
            BLOCKS.register("calendar", () -> new com.fst.tothesky.block.CalendarBlock(
                    BlockBehaviour.Properties.of().sound(SoundType.WOOD).strength(1.0f).noOcclusion()));

    // ---------------- PR#58 可放置食物方块 ----------------
    private static java.util.List<double[]> boxes(double[]... b) {
        return java.util.Arrays.asList(b);
    }

    private static BlockBehaviour.Properties woodFood() {
        return BlockBehaviour.Properties.of().sound(SoundType.WOOD).noOcclusion();
    }

    /** 咸豆腐脑（碗形六面箱） */
    public static final RegistryObject<PlaceableFoodBlock> SALTY_BEAN_CURD_BLOCK =
            BLOCKS.register("salty_bean_curd", () -> new PlaceableFoodBlock(
                    boxes(new double[]{5, 2.5, 5, 11, 3.5, 11},
                            new double[]{5, 1, 11, 11, 4, 12},
                            new double[]{4, 1, 5, 5, 4, 11},
                            new double[]{5, 1, 4, 11, 4, 5},
                            new double[]{11, 1, 5, 12, 4, 11},
                            new double[]{5, 0, 5, 11, 1, 11}),
                    woodFood()));
    /** 幻翼虾仁（扁盘） */
    public static final RegistryObject<PlaceableFoodBlock> PHANTOM_SHRIMP_BLOCK =
            BLOCKS.register("phantom_shrimp", () -> new PlaceableFoodBlock(
                    boxes(new double[]{1, 0, 1, 15, 2, 15}),
                    woodFood()));
    /** 甜豆花（碗形六面箱） */
    public static final RegistryObject<PlaceableFoodBlock> SWEET_BEAN_CURD_BLOCK =
            BLOCKS.register("sweet_bean_curd", () -> new PlaceableFoodBlock(
                    boxes(new double[]{5, 2.5, 5, 11, 3.5, 11},
                            new double[]{5, 1, 11, 11, 4, 12},
                            new double[]{4, 1, 5, 5, 4, 11},
                            new double[]{5, 1, 4, 11, 4, 5},
                            new double[]{11, 1, 5, 12, 4, 11},
                            new double[]{5, 0, 5, 11, 1, 11}),
                    woodFood()));
    /** 饮品659（杯形） */
    public static final RegistryObject<PlaceableFoodBlock> DRINK_659_BLOCK =
            BLOCKS.register("drink659", () -> new PlaceableFoodBlock(
                    boxes(new double[]{5.25, 0, 5.25, 10.75, 10.5, 10.75}),
                    BlockBehaviour.Properties.of().sound(SoundType.GLASS).noOcclusion()));
    /** 晴天鳕鱼（扁盘） */
    public static final RegistryObject<PlaceableFoodBlock> SUNSHINE_COD_BLOCK =
            BLOCKS.register("sunshine_cod", () -> new PlaceableFoodBlock(
                    boxes(new double[]{1, 0, 1, 15, 1, 15}),
                    BlockBehaviour.Properties.of().sound(SoundType.GLASS).noOcclusion()));
    /** 温泉蛋牛肉盖饭（碗形三层） */
    public static final RegistryObject<PlaceableFoodBlock> BEEF_OVER_RICE_BLOCK =
            BLOCKS.register("beef_over_rice", () -> new PlaceableFoodBlock(
                    boxes(new double[]{2, 0, 2, 14, 1, 14},
                            new double[]{2, 1, 2, 14, 6, 14},
                            new double[]{3, 6, 3, 13, 8, 13}),
                    woodFood()));
    /** 鱿鱼狂欢节（矮盘） */
    public static final RegistryObject<PlaceableFoodBlock> SQUID_FESTIVAL_BLOCK =
            BLOCKS.register("squid_festival", () -> new PlaceableFoodBlock(
                    boxes(new double[]{2.85, 0, 2.85, 13.15, 3.45, 13.15}),
                    woodFood()));

    // ---------------- PR#59 动力雕刻台 ----------------
    /** 动力雕刻台：通电后按 ultramarine 凿刻配方自动雕刻木材 */
    public static final RegistryObject<Block> MECHANICAL_CHISEL_TABLE =
            BLOCKS.register("mechanical_chisel_table", () -> new com.fst.tothesky.block.MechanicalChiselTableBlock(
                    Block.box(0, 0, 0, 16, 4, 16),
                    BlockBehaviour.Properties.of().sound(SoundType.COPPER).strength(2.0f).noOcclusion()));

    @Nullable
    public static DrinkGlassBlock drinkBlockFor(ResourceLocation cocktailId) {
        for (RegistryObject<DrinkGlassBlock> holder : COCKTAIL_BLOCKS) {
            if (holder.get().cocktailId().equals(cocktailId)) {
                return holder.get();
            }
        }
        return null;
    }

    private static RegistryObject<DrinkGlassBlock> martini(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.Martini(kk(cocktail), MARTINI_GLASS, glassProps()));
    }

    private static RegistryObject<DrinkGlassBlock> hurricane(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.Hurricane(kk(cocktail), HURRICANE_GLASS, glassProps()));
    }

    private static RegistryObject<DrinkGlassBlock> oldFashioned(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.OldFashioned(kk(cocktail), OLD_FASHIONED_GLASS, glassProps()));
    }

    private static ResourceLocation kk(String path) {
        return ResourceLocation.fromNamespaceAndPath("kitchenkarrot", "cocktails/" + path);
    }

    private ModBlocks() {
    }
}
