package com.fst.tothesky.registry;

import com.github.ysbbbbbb.kaleidoscopecookery.item.SickleItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * kaleidoscope_cookery 命名空间的两把镰刀（PR#39）。
 * kjs 用 event.createCustom 直接实例化 kk 的 SickleItem；mod 侧在 kk 命名空间注册，
 * 使镰刀的 id 与 tag 与 kjs 时代完全一致（存档/配方无需映射）。
 */
public final class ModKcItems {
    public static final DeferredRegister<Item> KC_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "kaleidoscope_cookery");

    /** 钻石镰刀：挖掘等级 4、耐久 3000、速度 9.0、攻击 3.0、附魔 10 */
    public static final RegistryObject<Item> DIAMOND_SICKLE = KC_ITEMS.register("diamond_sickle",
            () -> new SickleItem(diamondTier(), 0, -2.4f, new Item.Properties()));

    /** 下界合金镰刀：耐久 4000、攻击 5.0、附魔 15、防火 */
    public static final RegistryObject<Item> NETHERITE_SICKLE = KC_ITEMS.register("netherite_sickle",
            () -> new SickleItem(netheriteTier(), 0, -2.4f, new Item.Properties().fireResistant()));

    private static Tier diamondTier() {
        return unsortedTier(4, 3000, 9.0f, 3.0f, 10);
    }

    private static Tier netheriteTier() {
        return unsortedTier(4, 4000, 9.0f, 5.0f, 15);
    }

    /**
     * 建造镰刀的 Tier：只 new 一个 ForgeTier，<b>不</b>注册进 {@link TierSortingRegistry}（与 kjs 时代一致）。
     * <p>
     * 曾在这里顺手注册过一次（想让挖掘等级参与排序），结果<b>原版镐挖不动铁/铜矿石</b>：
     * {@code TierSortingRegistry.isCorrectTierForDrops} 的判定是「遍历所有<b>高于</b>本工具的 Tier，
     * 方块只要落在它的 {@link Tier#getTag()} 里就判为挖不动」——{@code getTag()} 的语义是
     * 「需要本 Tier 的方块」，不是「本 Tier 能挖的方块」。镰刀 Tier 填的是
     * {@code minecraft:needs_stone_tool}，注册后又被排在钻石之后（下界合金之上），于是石/铁/钻石/下界合金镐
     * 的判定都会撞上它——{@code needs_stone_tool} 里的铁矿石、铜矿石（含深板岩变种、铁块/铜块等）
     * 对除镰刀外的所有镐都变成“挖不动、无掉落”。
     * <p>
     * 不注册时该 tag 无人读取，镰刀按原版 {@code isCorrectTierVanilla}（等级 4）判定，
     * 挖掘能力与下界合金镐同级；一旦要注册，必须同时把 tag 换成与该 Tier 排序位置相符的门槛。
     */
    private static Tier unsortedTier(int level, int uses, float speed, float attack, int enchantment) {
        return new ForgeTier(level, uses, speed, attack, enchantment,
                BlockTags.NEEDS_STONE_TOOL, () -> Ingredient.of(Items.DIAMOND));
    }

    private ModKcItems() {
    }
}
