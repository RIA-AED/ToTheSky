package com.fst.tothesky.restore;

import com.fst.tothesky.ToTheSky;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SophisticatedBackpacks 类引用的唯一落点：只有本类 import {@code net.p3pp3rf1y.*}。
 *
 * <p>与 {@code Ae2CellBridge}、{@code contact.ContactMailBridge} 同一套意图：
 * 该模组是 {@code compileOnly + runtimeOnly}、mods.toml 里 {@code mandatory=false}，
 * 未安装时本类永不被加载，也就不会 {@code NoClassDefFoundError}。
 *
 * <p><b>为什么能"直接存入没有主人的背包"</b>
 * 精妙背包的内容**不在背包物品里**，而在全局 SavedData（{@code data/sophisticatedbackpacks.dat}）中，
 * 以内层内容为值、{@code contentsUuid} 为键。{@code BackpackStorage.get()} 公开了
 * {@code getOrCreateBackpackContents(UUID)} / {@code setBackpackContents(UUID, CompoundTag)}，
 * 所以只要能拿到 uuid，就能把物品直接写回那个背包的内容——**不需要背包物品本身存在，
 * 也不需要知道它属于谁**。这正好覆盖扫描器报出的
 * {@code backpack_storage_file}（无人引用的背包内容）。
 *
 * <p><b>写入格式</b>与扫描器读取的完全一致（已由真实存档验证）：
 * {@code <contents>.inventory.{Size, Items[]}}，每个 ItemStack 带 {@code Slot}。
 * 先尝试与同类物品合并，再占用空槽位；两者都放不下就如实报失败，不做部分写入。
 */
public final class BackpackStorageBridge {

    /** SophisticatedBackpacks 是否在场 */
    private static final boolean LOADED = ModList.get().isLoaded("sophisticatedbackpacks");

    /** 背包内容的默认格数（读不到 Size 时的兜底，与精妙背包默认一致） */
    private static final int DEFAULT_SLOTS = 27;

    private BackpackStorageBridge() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    /**
     * 把物品写进指定 uuid 的背包内容。
     *
     * @param uuid  扫描器从 {@code contentsUuid} 还原出的背包标识
     * @param stack 待写入物品（id 已是替代品）
     * @return 结果；成功时容器容量与合并情况见 {@link Result#detail()}
     */
    public static Result insert(UUID uuid, ItemStack stack) {
        if (!LOADED) {
            return new Result(false, "未安装 SophisticatedBackpacks，无法写回背包内容");
        }
        if (stack.isEmpty()) {
            return new Result(false, "待写入物品为空");
        }
        try {
            var storage = net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage.get();
            if (storage == null) {
                return new Result(false, "背包存档不可用（服务器未就绪？）");
            }
            CompoundTag contents = storage.getOrCreateBackpackContents(uuid);
            if (contents == null) {
                return new Result(false, "该 uuid 的背包内容读取失败");
            }
            // 正常情况下返回的就是「内容」本身（含 inventory）；若对方换了外层结构，下钻一层兜住
            CompoundTag inner = contents.getCompound("contents");
            CompoundTag target = inner.isEmpty() ? contents : inner;

            CompoundTag inventory = target.getCompound("inventory");
            if (inventory.isEmpty()) {
                // 内容为空或结构异常：不擅自构造，交给人工核对
                return new Result(false, "背包内容里没有 inventory 段，未改动");
            }
            int size = inventory.contains("Size") ? inventory.getInt("Size") : DEFAULT_SLOTS;
            ListTag items = inventory.getList("Items", Tag.TAG_COMPOUND);

            ItemStack remaining = stack.copy();
            // 1) 先与同类物品合并
            for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
                ItemStack existing = ItemStack.of(items.getCompound(i).copy());
                if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remaining)) {
                    continue;
                }
                int room = existing.getMaxStackSize() - existing.getCount();
                if (room <= 0) {
                    continue;
                }
                int move = Math.min(room, remaining.getCount());
                existing.setCount(existing.getCount() + move);
                CompoundTag saved = existing.save(new CompoundTag());
                saved.putByte("Slot", (byte) items.getCompound(i).getInt("Slot"));
                items.set(i, saved);
                remaining.shrink(move);
            }
            // 2) 再占用一个空槽位
            if (!remaining.isEmpty()) {
                Set<Integer> used = new HashSet<>();
                for (int i = 0; i < items.size(); i++) {
                    used.add(items.getCompound(i).getInt("Slot"));
                }
                int slot = 0;
                while (used.contains(slot)) {
                    slot++;
                }
                if (slot >= size) {
                    return new Result(false, "背包内容已满（" + items.size() + "/" + size + " 格），未改动");
                }
                CompoundTag saved = remaining.save(new CompoundTag());
                saved.putByte("Slot", (byte) slot);
                items.addTag(items.size(), saved);
            }

            inventory.putInt("Size", size);
            inventory.put("Items", items);
            target.put("inventory", inventory);
            // 对称写回：getOrCreateBackpackContents 给的是什么，就交回什么，并标脏落盘
            storage.setBackpackContents(uuid, contents);
            storage.setDirty();
            return new Result(true, "已写入背包内容（合计 " + items.size() + "/" + size + " 格）");
        } catch (RuntimeException | LinkageError e) {
            // LinkageError：版本不匹配导致的方法签名缺失，不该让整个补偿任务崩掉
            ToTheSky.LOGGER.warn("[补偿] 写回背包内容失败（uuid={}）：{}", uuid, e.toString());
            return new Result(false, "写回背包时抛出 " + e.getClass().getSimpleName());
        }
    }

    /** 写入结果 */
    public record Result(boolean ok, String detail) {
    }
}
