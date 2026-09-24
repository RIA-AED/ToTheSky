package com.fst.tothesky.restore;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import com.fst.tothesky.ToTheSky;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 类引用的唯一落点：只有本类 import {@code appeng.*}。
 *
 * <p>AE2 在 build.gradle 里是 {@code compileOnly + runtimeOnly}、mods.toml 里是
 * {@code mandatory=false}，所以这些引用必须与「调用前的 {@link #loaded()} 检查」成对出现——
 * 未安装时本类永不被加载，也就不会 {@code NoClassDefFoundError}。
 * 这与 {@code contact.ContactMailBridge} 对 Contact 的处理是同一套意图。
 *
 * <p><b>为什么用官方 API 而不是直接改 NBT</b>
 * <ul>
 *   <li>{@code keys}/{@code amts}/{@code ic} 是 AE2 的内部持久化格式，可能随版本变化；
 *       官方 API 把「插入 + 持久化」交给 AE2 自己，跨版本更稳。</li>
 *   <li><b>未注册的旧键由 AE2 自行丢弃</b>：{@code AEItemKey.fromTag} 对解析不到的 id
 *       走 {@code getOptional().orElseThrow()}，异常被捕获后返回 null，装载时该键被跳过，
 *       随后的 {@code persist()} 不会再写回。所以「旧键还在元件里」不会造成重复计数，
 *       也不需要我们在 NBT 层面做改名。</li>
 *   <li>直接改 NBT 还得自己处理「取出元件再放回以触发重新挂载」这类 AE2 内部时序；
 *       官方 API 下这仍是必要的（见 {@code RestoreService}），但写入语义由 AE2 保证。</li>
 * </ul>
 */
public final class Ae2CellBridge {

    /** AE2 是否在场 */
    private static final boolean AE2_LOADED =
            net.minecraftforge.fml.ModList.get().isLoaded("ae2");

    private Ae2CellBridge() {
    }

    /** AE2 是否可用（未安装时补偿会跳过 AE2 条目） */
    public static boolean loaded() {
        return AE2_LOADED;
    }

    /**
     * 往存储元件里插入物品。
     *
     * <p><b>调用方必须先 {@code extractItem} 把元件从驱动器取出</b>：元件挂在驱动器上时，
     * AE2 已按旧内容建立了内存镜像，只改 ItemStack 的 NBT 不会被读到。
     * 取出后元件是「离体」状态，用 {@code null} 作为 {@code ISaveProvider}
     * （无人可标脏，改动直接落在 ItemStack 的 NBT 上），写完 {@code persist()} 再放回槽位，
     * 放回时 AE2 会按新 NBT 重新挂载。
     *
     * <p><b>先模拟、后提交</b>：先用 {@link Actionable#SIMULATE} 问 AE2「能放下多少」，
     * 只有能<b>全部</b>放下才真正写入。否则容量不足时会写进一部分、其余凭空消失，
     * 而台账不记（算失败）、重跑又把已写的那部分重复一遍——两个方向都错。
     * 模拟为零改动，因此「放不下」时元件保持原样，可安全重跑。
     *
     * @param cell      离体的存储元件（方法内只读其 NBT，不改 ItemStack 本身）
     * @param prototype 要补偿的物品（用其 id + NBT 构造 AE2 键）
     * @param amount    数量
     * @return 结果描述
     */
    public static Result insert(ItemStack cell, ItemStack prototype, int amount) {
        AEItemKey key = AEItemKey.of(prototype);
        if (key == null) {
            return new Result(false, 0, "AE2 无法为该物品构造存储键（物品未注册？）");
        }
        StorageCell inventory = StorageCells.getCellInventory(cell, null);
        if (inventory == null) {
            return new Result(false, 0, "该物品不是 AE2 可识别的存储元件");
        }
        if (inventory.getStatus() == CellState.ABSENT) {
            return new Result(false, 0, "元件状态为 ABSENT（可能已损坏或类型不匹配）");
        }
        try {
            long feasible = inventory.insert(key, amount, Actionable.SIMULATE, IActionSource.empty());
            if (feasible < amount) {
                return new Result(false, (int) feasible,
                        "元件放不下全部 " + amount + " 件（最多 " + feasible + " 件），未改动");
            }
            long inserted = inventory.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
            inventory.persist();
            if (inserted < amount) {
                // 模拟说能放下、提交却少了：AE2 内部状态不一致，如实报出
                return new Result(false, (int) inserted,
                        "AE2 提交量与模拟不符（" + inserted + "/" + amount + "），请人工核对元件");
            }
            return new Result(true, (int) inserted, "已写入 " + inserted + " 件");
        } catch (RuntimeException e) {
            // AE2 内部状态异常：如实报出，交由调用方把元件放回原处
            ToTheSky.LOGGER.warn("[补偿] AE2 元件写入抛出异常：{}", e.toString());
            return new Result(false, 0, "AE2 写入异常：" + e.getClass().getSimpleName());
        }
    }

    /** 插入结果 */
    public record Result(boolean ok, int inserted, String detail) {
    }
}
