package com.fst.tothesky.mixin;

import com.fst.tothesky.ToTheSky;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 修复旧存档中 Copycats+ 伪装板的材质数据。
 *
 * <p>背景：Create: Crystal Clear 的方块/物品已通过 {@link com.fst.tothesky.event.MissingMappingEvents}
 * 从 {@code crystal_clear:} / {@code create_crystal_clear:} 重映射到 {@code tothesky:}。
 * Copycats+ 的伪装板在 NBT 里同时记录 {@code material}（渲染用的 BlockState）和
 * 被“吃掉”的物品。物品 ID 会被 Forge 的注册表重映射自然更新，但 {@code material}
 * 是普通的 BlockState 字符串，不会自动跟着变——结果就出现了 consumedItem 是
 * {@code tothesky:xxx_glass_casing} 而 material 仍是 {@code minecraft:air} 的情况，
 * 伪装板渲染成空气。
 *
 * <p>本 mixin 在 {@link ChunkSerializer#read} 把区块 NBT 解析成 {@link ProtoChunk} 之前
 * 拦截，直接改写原始 NBT 中的 {@code block_entities} 列表。Copycats+ 的伪装板 NBT
 * 有单状态和多状态两种写法，这里统一兼容：
 * <ul>
 *   <li>单状态：顶层 {@code Material} + {@code Item}</li>
 *   <li>多状态（properties 数组）：{@code material_data.properties[].material + item}</li>
 *   <li>多状态（命名键，如 bottom/top）：{@code material_data.<key>.material + consumedItem}</li>
 * </ul>
 *
 * <p>修复条件：material 为 {@code minecraft:air} 且对应物品是 {@code tothesky:} 注册项时，
 * 把 material 推导回该物品对应方块的默认 BlockState。修好后 material 不再是空气，
 * 后续加载不会重复触发。
 */
@Mixin(ChunkSerializer.class)
public class CopycatBlockEntityDataFixMixin {

    private static final String COPYCATS_NAMESPACE = "copycats";
    private static final String BLOCK_ENTITIES_KEY = "block_entities";
    private static final String MATERIAL_DATA_KEY = "material_data";
    private static final String PROPERTIES_KEY = "properties";
    private static final String ID_KEY = "id";
    private static final String NAME_KEY = "Name";
    private static final String AIR_BLOCK_NAME = "minecraft:air";
    private static final String AIR_ITEM_NAME = "minecraft:air";

    private static final Set<String> MATERIAL_KEYS = Set.of("Material", "material");
    private static final Set<String> ITEM_KEYS = Set.of("Item", "item", "consumedItem");

    @Inject(method = "read", at = @At("HEAD"))
    private static void tothesky$fixCopycatMaterialData(ServerLevel level, PoiManager poiManager, ChunkPos pos,
                                                        CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        if (!tag.contains(BLOCK_ENTITIES_KEY, Tag.TAG_LIST)) {
            return;
        }

        ListTag blockEntities = tag.getList(BLOCK_ENTITIES_KEY, Tag.TAG_COMPOUND);
        ToTheSky.LOGGER.info("[CopycatFix] Chunk {} loaded with {} block entities", pos, blockEntities.size());

        boolean anyChanged = false;

        for (int i = 0; i < blockEntities.size(); i++) {
            if (!(blockEntities.get(i) instanceof CompoundTag blockEntityTag)) {
                continue;
            }

            if (!blockEntityTag.contains(ID_KEY, Tag.TAG_STRING)) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(blockEntityTag.getString(ID_KEY));
            if (id == null || !COPYCATS_NAMESPACE.equals(id.getNamespace())) {
                continue;
            }

            ToTheSky.LOGGER.info("[CopycatFix] Found copycat block entity {} in chunk {}", id, pos);
            boolean changed;
            try {
                changed = patchCopycatBlockEntity(blockEntityTag);
            } catch (Exception e) {
                ToTheSky.LOGGER.error("[CopycatFix] Failed to patch copycat {} in chunk {}", id, pos, e);
                continue;
            }
            if (changed) {
                anyChanged = true;
                ToTheSky.LOGGER.info("[CopycatFix] Patched copycat material data in chunk {} at block entity {}", pos, id);
            }
        }

        if (anyChanged) {
            ToTheSky.LOGGER.info("[CopycatFix] Patched copycat material data entries in chunk {}", pos);
        }
    }



    /**
     * 修复单个 copycat 方块实体的 NBT，支持单状态与多状态两种结构。
     *
     * <p>多状态方块（material_data）里 Copycats+ 只会保存每种材质第一次出现时的
     * {@code consumedItem}，其余重复位置会写成双 air。本方法会先收集所有非 air 的
     * consumedItem，再按轮询方式把这些双 air 位置填回有效材质。</p>
     *
     * @return 是否有任何字段被修改
     */
    private static boolean patchCopycatBlockEntity(CompoundTag blockEntityTag) {
        boolean changed = false;

        // 1. 单状态：顶层 Material + Item
        changed |= patchPartTag(blockEntityTag, null);

        // 2. 多状态：material_data
        if (!blockEntityTag.contains(MATERIAL_DATA_KEY, Tag.TAG_COMPOUND)) {
            return changed;
        }
        CompoundTag materialData = blockEntityTag.getCompound(MATERIAL_DATA_KEY);

        // 2.1 先收集所有非 air 的 consumedItem ID（用于回填双 air）
        List<String> fallbackItemIds = collectNonAirConsumedItems(materialData);

        // 2a. properties 数组
        if (materialData.contains(PROPERTIES_KEY, Tag.TAG_LIST)) {
            ListTag properties = materialData.getList(PROPERTIES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < properties.size(); i++) {
                if (properties.get(i) instanceof CompoundTag propertyTag) {
                    String fallback = pickFallback(fallbackItemIds, i);
                    changed |= patchPartTag(propertyTag, fallback);
                }
            }
        }

        // 2b. 命名键（bottom/top 等）
        int index = 0;
        for (String key : materialData.getAllKeys()) {
            if (key.equals(PROPERTIES_KEY)) {
                continue;
            }
            if (materialData.get(key) instanceof CompoundTag partTag) {
                String fallback = pickFallback(fallbackItemIds, index);
                changed |= patchPartTag(partTag, fallback);
                index++;
            }
        }

        return changed;
    }

    /**
     * 在 material_data 中收集所有 material 与 consumedItem 都非 air 的 consumedItem ID。
     * 这些是双 air 子项的回填来源。
     */
    private static List<String> collectNonAirConsumedItems(CompoundTag materialData) {
        List<String> result = new ArrayList<>();

        // properties 数组
        if (materialData.contains(PROPERTIES_KEY, Tag.TAG_LIST)) {
            ListTag properties = materialData.getList(PROPERTIES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < properties.size(); i++) {
                if (properties.get(i) instanceof CompoundTag propertyTag) {
                    String id = getNonAirConsumedItemId(propertyTag);
                    if (id != null) {
                        result.add(id);
                    }
                }
            }
        }

        // 命名键
        for (String key : materialData.getAllKeys()) {
            if (key.equals(PROPERTIES_KEY)) {
                continue;
            }
            if (materialData.get(key) instanceof CompoundTag partTag) {
                String id = getNonAirConsumedItemId(partTag);
                if (id != null) {
                    result.add(id);
                }
            }
        }

        return result;
    }

    /**
     * 按索引轮询选择一个 fallback 物品 ID。
     */
    private static String pickFallback(List<String> fallbackItemIds, int index) {
        if (fallbackItemIds.isEmpty()) {
            return null;
        }
        return fallbackItemIds.get(index % fallbackItemIds.size());
    }

    /**
     * 若该部件的 material 与 consumedItem 都非 air，返回 consumedItem.id；否则返回 null。
     */
    private static String getNonAirConsumedItemId(CompoundTag partTag) {
        CompoundTag material = findCompound(partTag, MATERIAL_KEYS);
        if (material == null) {
            return null;
        }
        String materialName = material.getString(NAME_KEY);
        if (materialName.isEmpty() || AIR_BLOCK_NAME.equals(materialName)) {
            return null;
        }

        CompoundTag consumedItem = findCompound(partTag, ITEM_KEYS);
        if (consumedItem == null) {
            return null;
        }
        String itemId = consumedItem.getString(ID_KEY);
        if (itemId.isEmpty() || AIR_ITEM_NAME.equals(itemId)) {
            return null;
        }
        return itemId;
    }

    /**
     * 修复一个“部件”标签：
     * <ul>
     *   <li>若 material 为空气且 item 是 {@code tothesky:} 物品，把 material 设为对应方块。</li>
     *   <li>若 material 与 item 双空气且提供了 fallbackItemId，用 fallback 填回 material 与 item。</li>
     * </ul>
     *
     * @param fallbackItemId 双空气时的回填物品 ID，null 表示不回填
     * @return 是否发生修改
     */
    private static boolean patchPartTag(CompoundTag partTag, String fallbackItemId) {
        CompoundTag material = findCompound(partTag, MATERIAL_KEYS);
        if (material == null) {
            return false;
        }
        if (!AIR_BLOCK_NAME.equals(material.getString(NAME_KEY))) {
            return false;
        }

        CompoundTag consumedItem = findCompound(partTag, ITEM_KEYS);
        if (consumedItem == null) {
            return false;
        }
        String itemId = consumedItem.getString(ID_KEY);

        // 情况 A：material 为空气，consumedItem 是 tothesky 物品 → 从 consumedItem 推导材质
        if (!itemId.isEmpty() && itemId.startsWith(ToTheSky.MODID + ":")) {
            return applyMaterialFromItem(partTag, itemId);
        }

        // 情况 B：material 与 consumedItem 双空气 → 用本方块实体中已出现的非 air 物品回填
        if (AIR_ITEM_NAME.equals(itemId) && fallbackItemId != null) {
            boolean changed = applyMaterialFromItem(partTag, fallbackItemId);
            if (changed) {
                String itemKey = findKey(partTag, ITEM_KEYS);
                if (itemKey != null) {
                    CompoundTag newItem = consumedItem.copy();
                    newItem.putString(ID_KEY, fallbackItemId);
                    partTag.put(itemKey, newItem);
                }
            }
            return changed;
        }

        return false;
    }

    /**
     * 把 partTag 的 material 设为 itemId 对应方块的默认 BlockState。
     *
     * @return 是否成功修改
     */
    private static boolean applyMaterialFromItem(CompoundTag partTag, String itemId) {
        ResourceLocation itemRl = ResourceLocation.tryParse(itemId);
        if (itemRl == null) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemRl);
        if (item == null || item == Items.AIR) {
            return false;
        }

        Block block = Block.byItem(item);
        if (block == Blocks.AIR) {
            return false;
        }

        BlockState defaultState = block.defaultBlockState();
        String materialKey = findKey(partTag, MATERIAL_KEYS);
        if (materialKey == null) {
            return false;
        }
        partTag.put(materialKey, NbtUtils.writeBlockState(defaultState));
        return true;
    }

    /**
     * 在 compound 中查找给定候选键名中的任意一个，返回对应的 CompoundTag；不存在或类型不对返回 null。
     */
    private static CompoundTag findCompound(CompoundTag tag, Set<String> keys) {
        String key = findKey(tag, keys);
        if (key == null) {
            return null;
        }
        return tag.getCompound(key);
    }

    /**
     * 在 compound 中查找给定候选键名中的任意一个，返回实际存在的键名；不存在返回 null。
     */
    private static String findKey(CompoundTag tag, Set<String> keys) {
        for (String key : keys) {
            if (tag.contains(key, Tag.TAG_COMPOUND)) {
                return key;
            }
        }
        return null;
    }
}
