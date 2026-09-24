package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.List;

/**
 * 旧存档无缝迁移（kubejs: / crystal_clear: / create_crystal_clear: → tothesky:）。
 * <p>
 * 两批来源：
 * <ul>
 *   <li><b>KubeJS</b>：KubeJS 卸载后，存档中的 kubejs:* 条目在注册表里消失；映射表
 *       （{@link #MIGRATED_BLOCKS} 等）就是一张“迁移过的 kjs 注册名”清单——
 *       迁移时刻意保持同名，所以名字两侧一致。</li>
 *   <li><b>Create: Crystal Clear</b>：该 mod 已不再兼容当前 Create 版本
 *       （合并在本 mod 里的 32 个方块与上游逐字同名），因此凡是在
 *       {@code crystal_clear} / {@code create_crystal_clear} 命名空间下、且本 mod
 *       确实注册了同名条目的缺失映射，一律 remap 过来。用“本 mod 是否存在同名条目”
 *       而不是硬编码清单，是因为这两个命名空间里的东西全都是我们的。</li>
 * </ul>
 * 本处理器在 {@link MissingMappingsEvent}（Forge 总线，注册表注入快照阶段触发）中完成上述映射。
 * <p>
 * 覆盖注册表：方块、物品（含方块自带 BlockItem 与流体桶）、流体、状态效果、音效。
 * <p>
 * 方块实体类型<b>不</b>在此处理：该注册表在 Forge 侧 {@code disableSaving()}，从不写进存档快照，
 * 永远不会产生缺失映射事件；它的映射走 {@link RegistryAliasEvents} 的注册表别名。
 * <p>
 * 未迁移的条目（kjs 的 seat、fps 活动物品、delta 产线中间品；Crystal Clear 已删掉的 steel_* 三类）
 * 不在映射内，会按 Forge 默认策略 FAIL 提示——存档若包含这些条目，玩家需自行处理。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class MissingMappingEvents {
    /** 已迁移到 tothesky 的 kjs 方块注册名（kjs block registry 自动带同名 BlockItem） */
    private static final List<String> MIGRATED_BLOCKS = List.of(
            // 酒类方块（酒坊产线）
            "aging_container", "distiller", "ferment_container", "lable_printer", "wine_crafting_table",
            // 比萨 / 汉堡 / 蜡烛 / 纳奈子雕像 / 烹饪锅
            "pizza_margarita", "pork_pizza", "apple_pizza", "burger", "candle_stick", "nanako_sculpture",
            "golden_cooking_pot", "golden_skillet", "silver_cooking_pot", "copper_cooking_pot",
            // 活动方块 / 石墨块
            "event_block_1", "event_block_2", "event_block_3", "he_graphite_block",
            // 售货机 / 压面机 / 检查站 / 饺子盘 / 拉面
            "seller", "roller", "checker", "cooked_dumpling_plate", "ramen",
            // 春节方块
            "fireworks_box", "multiple_fireworks", "multiple_firecrackers", "firing_multiple_fireworks",
            // 鸡尾酒玻璃杯方块（drink_block_registry）
            "martini_glass", "hurricane_glass", "old_fashioned_glass",
            // 鸡尾酒放置方块（COCKTAIL_MAP，无物品形态）
            "july_21_block", "tsundere_heroine_block", "sweet_berry_martini_block", "birch_sap_vodka_block",
            "red_lizard_block", "second_guess_block", "light_yellow_firefly_block", "shooting_star_block",
            "twilight_forest_block", "jacks_story_block", "shanghai_beach_block", "bane_of_arthropods_block",
            // PR#58/59 新增
            "mechanical_chisel_table",
            // 旧披萨阶段方块（存档兼容）
            "pizza_margarita2", "pizza_margarita3", "pizza_margarita4",
            "pork_pizza2", "pork_pizza3", "pork_pizza4",
            "apple_pizza2", "apple_pizza3", "apple_pizza4"
    );

    /**
     * 已迁移的 kjs 物品注册名（纯物品，无方块；方块的 BlockItem 由 {@link #MIGRATED_BLOCKS} 顺带覆盖）。
     * <p>
     * 必须逐个列全：kjs 侧注册过、本 mod 也同名注册了的物品一旦漏配，旧存档里的那件物品
     * 就会被 Forge 当缺失条目直接抹掉，而不是落到替代品上。
     * 未迁移的 kjs 物品（{@code hemostix}、{@code event_item_1~5}）刻意<b>不</b>列在这里。
     */
    private static final List<String> MIGRATED_ITEMS = List.of(
            // 酒类物品
            "wine_bottle", "incomplete_wine_bottle",
            // 比萨切片 / 生坯
            "sliced_pizza_margarita", "sliced_pork_pizza", "sliced_apple_pizza",
            "cheese", "pizza_base", "raw_pizza_margarita", "raw_pork_pizza", "raw_apple_pizza",
            // 食物
            "bug_soup", "caramel_cod_soup", "pasta_with_chocolate", "digestion_pellow",
            "cod_burger", "fried_cod", "cut_cheese", "delta_porridge",
            // 饺子（raw_dumpling 此前一直漏配，旧存档的带馅生饺子会直接消失）
            "cooked_dumpling", "raw_dumpling", "dumpling_wrapper", "raw_dumpling_plate",
            // 豆制品链
            "bean_curd", "cut_bean_curd", "spicy_bean_curd", "berry_bean_curd", "soy_sause_bottle", "soy_bean_oil",
            // 采血链
            "blood_bottle", "hemostix_plus",
            // 事件/杂项物品（he_graphite 是物品，与同名的 he_graphite_block 方块是两件注册项）
            "deployer_lubricant", "harvest_the_night", "copter", "he_graphite_block", "he_graphite",
            "roller_ticket", "firecracker",
            // 鳕鱼堡 / 钻石产线
            "bomb_cod_burger", "diamond_core", "uncomplete_diamond",
            // 下界合金 / 魔女因子链
            "impure_alloy_base", "raw_alloy_base", "incomplete_netherite_ingot",
            "witch_factor", "activated_witch_factor",
            // 三角币经济链
            "delta_coin", "delta_coin_chip", "delta_dust",
            // 图腾 / 石墨 / 晶体产线
            "emerald_nugget", "raw_totem", "incomplete_totem", "fiber_mixture", "frother_mixture",
            "small_crystal", "faded_small_crystal", "incomplete_tortilla",
            // 可放置食物的物品形态（kjs 侧只注册了物品，无对应方块）
            "salty_bean_curd", "sweet_bean_curd", "squid_festival", "phantom_shrimp",
            "sunshine_cod", "raw_sunshine_cod", "drink659", "beef_over_rice",
            // 魔法照片
            "blue_magic_stone", "red_magic_stone", "yellow_magic_stone", "green_magic_stone",
            // 乐器
            "guitar", "piano", "drum_808", "empty_music_sheet", "music_sheet",
            // celestia 三连物品
            "solaris0", "solaris1", "solaris2",
            // 礼花棒
            "sparkler",
            // PR#56/59
            "dew_of_oblivion", "packed_colors"
    );

    /** kjs 流体桶物品（kjs fluid registry 自动注册 <name>_bucket） */
    private static final List<String> MIGRATED_BUCKETS = List.of(
            "netherite_liquar_bucket", "unstable_netherite_liquar_bucket",
            "bean_sause_bucket", "bean_oil_bucket", "soy_sause_bucket", "ghast_tear_bucket"
    );

    /** kjs 流体注册名（still 形态；kjs 不注册独立 flowing id） */
    private static final List<String> MIGRATED_FLUIDS = List.of(
            "netherite_liquar", "unstable_netherite_liquar",
            "bean_sause", "bean_oil", "soy_sause", "ghast_tear"
    );

    /** kjs 状态效果注册名 */
    private static final List<String> MIGRATED_EFFECTS = List.of(
            "fair_play", "rewind", "hot_potato", "madness"
    );

    /** kjs 音效注册名（instruments.js + registry.js 的唱片音轨） */
    private static final List<String> MIGRATED_SOUNDS = List.of(
            "guitar_sound",
            "music.never_gonna_give_you_up"
    );

    /** kubejs: 命名空间 */
    private static final String KJS = "kubejs";

    /** Create: Crystal Clear 的历史命名空间（0.5.1 时代为 create_crystal_clear，2.x 改名 crystal_clear） */
    private static final List<String> CRYSTAL_CLEAR_NAMESPACES = List.of("crystal_clear", "create_crystal_clear");

    private MissingMappingEvents() {
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        remapBlocks(event);
        remapItems(event);
        remapFluids(event);
        remapEffects(event);
        remapSounds(event);
        remapCrystalClear(event);
    }

    /**
     * Create: Crystal Clear 的方块与物品 → 本 mod 同名条目。
     * <p>
     * 这里不列清单：该 mod 的内容整体并入本 mod 且逐字同名，凡是本 mod 注册了的同名条目就应当接住，
     * 剩下没接住的（steel_* 那三类，上游自己也在 2.1 删掉了）按 Forge 默认策略报警。
     * 用 {@code containsKey} 而不是判空：Forge 的方块/物品注册表有默认值（空气），
     * {@code getValue} 对不存在的名字返回的是空气而不是 {@code null}。
     */
    private static void remapCrystalClear(MissingMappingsEvent event) {
        for (String namespace : CRYSTAL_CLEAR_NAMESPACES) {
            for (MissingMappingsEvent.Mapping<Block> mapping : event.getMappings(Registries.BLOCK, namespace)) {
                ResourceLocation target = ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, mapping.getKey().getPath());
                if (ForgeRegistries.BLOCKS.containsKey(target)) {
                    mapping.remap(ForgeRegistries.BLOCKS.getValue(target));
                    ToTheSky.LOGGER.info("方块映射 {}:{} -> {}", namespace, mapping.getKey().getPath(), target);
                }
            }
            for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(Registries.ITEM, namespace)) {
                ResourceLocation target = ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, mapping.getKey().getPath());
                if (ForgeRegistries.ITEMS.containsKey(target)) {
                    mapping.remap(ForgeRegistries.ITEMS.getValue(target));
                    ToTheSky.LOGGER.info("物品映射 {}:{} -> {}", namespace, mapping.getKey().getPath(), target);
                }
            }
        }
    }

    private static void remapBlocks(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Block> mapping : event.getMappings(Registries.BLOCK, KJS)) {
            String path = mapping.getKey().getPath();
            if (MIGRATED_BLOCKS.contains(path)) {
                Block target = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, path));
                if (target != null) {
                    mapping.remap(target);
                    ToTheSky.LOGGER.info("方块映射 kubejs:{} -> tothesky:{}", path, path);
                }
            }
        }
    }

    private static void remapItems(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(Registries.ITEM, KJS)) {
            String path = mapping.getKey().getPath();
            boolean migrated = MIGRATED_BLOCKS.contains(path) // kjs 方块的 BlockItem 同名
                    || MIGRATED_ITEMS.contains(path)
                    || MIGRATED_BUCKETS.contains(path);
            if (migrated) {
                Item target = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, path));
                if (target != null) {
                    mapping.remap(target);
                    ToTheSky.LOGGER.info("物品映射 kubejs:{} -> tothesky:{}", path, path);
                }
            }
        }
    }

    private static void remapFluids(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<net.minecraft.world.level.material.Fluid> mapping
                : event.getMappings(Registries.FLUID, KJS)) {
            String path = mapping.getKey().getPath();
            if (MIGRATED_FLUIDS.contains(path)) {
                net.minecraft.world.level.material.Fluid target = ForgeRegistries.FLUIDS
                        .getValue(ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, path));
                if (target != null) {
                    mapping.remap(target);
                    ToTheSky.LOGGER.info("流体映射 kubejs:{} -> tothesky:{}", path, path);
                }
            }
        }
    }

    private static void remapSounds(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<net.minecraft.sounds.SoundEvent> mapping
                : event.getMappings(Registries.SOUND_EVENT, KJS)) {
            String path = mapping.getKey().getPath();
            if (MIGRATED_SOUNDS.contains(path)) {
                net.minecraft.sounds.SoundEvent target = ForgeRegistries.SOUND_EVENTS
                        .getValue(ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, path));
                if (target != null) {
                    mapping.remap(target);
                    ToTheSky.LOGGER.info("音效映射 kubejs:{} -> tothesky:{}", path, path);
                }
            }
        }
    }

    private static void remapEffects(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<net.minecraft.world.effect.MobEffect> mapping
                : event.getMappings(Registries.MOB_EFFECT, KJS)) {
            String path = mapping.getKey().getPath();
            if (MIGRATED_EFFECTS.contains(path)) {
                net.minecraft.world.effect.MobEffect target = ForgeRegistries.MOB_EFFECTS
                        .getValue(ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, path));
                if (target != null) {
                    mapping.remap(target);
                    ToTheSky.LOGGER.info("效果映射 kubejs:{} -> tothesky:{}", path, path);
                }
            }
        }
    }
}