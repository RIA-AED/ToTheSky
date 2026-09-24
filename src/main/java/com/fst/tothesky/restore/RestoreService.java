package com.fst.tothesky.restore;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.contact.ContactMail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描器补偿清单的执行器：把 {@code restore.json} 里每条被销毁的物品送回它原来的位置。
 *
 * <p><b>投递策略</b>
 * <ul>
 *   <li><b>玩家物品栏 / 末影箱 / 饰品栏</b>——直接进该玩家背包；
 *       <b>背包满</b>才改走 {@link ContactMail#sendParcel}（Contact 原生链路：邮箱满则等待、
 *       离线也送达、上线提示）。在线且有空位时不会打扰玩家的邮箱。</li>
 *   <li><b>普通容器</b>（箱子/桶/潜影盒/布袋/抽屉/Plonk 放置物/精妙背包方块…）——
 *       先试原槽位，再试任意空位；<b>装不下</b>就在附近 {@value #CHEST_SEARCH_RADIUS} 格内找空气方块
 *       放一个箱子继续装；仍放不下则放弃（报告失败，不静默丢弃）。</li>
 *   <li><b>AE2 存储元件</b>——按 AE2 自己的持久化格式（{@code keys}/{@code amts}/{@code ic}）
 *       把原件键写回元件 NBT，再把元件取出放回原槽以触发重新挂载。</li>
 * </ul>
 *
 * <p><b>幂等</b>：每条成功的补偿都记进 {@link RestoreLedger}，重跑自动跳过，不会发双份。
 *
 * <p><b>纯服务端</b>：不注册任何网络包、物品、方块或 GUI，客户端无需更新。
 * 进度用 {@link ServerBossEvent} 展示——那是原版机制，同样不需要模组客户端。
 *
 * <p><b>线程</b>：必须在服务器线程调用（方块实体与 SavedData 都不是线程安全的）。
 */
public final class RestoreService {

    /** 容器装不下时，在附近多大范围内找空气方块放箱子 */
    public static final int CHEST_SEARCH_RADIUS = 26;

    /** 从 {@code block_entities[5].inv.item0.tag.keys[0]} 里取驱动器槽位号 */
    private static final Pattern AE2_DRIVE_SLOT = Pattern.compile("\\.inv\\.item(\\d+)\\.");

    /** 一条条目的执行结果 */
    public enum Outcome {
        /** 已插入目标容器 */
        INSERTED,
        /** 装不下，已放入就近新放的箱子 */
        NEW_CHEST,
        /** 已通过往来包裹寄出 */
        MAILED,
        /** 数据已写入，但载体（AE2 元件）放不回原位，已掉落在原地 */
        DROPPED,
        /** 已经补偿过，跳过 */
        ALREADY_DONE,
        /** 目标容器/实体不存在（方块被拆、区块无实体） */
        NO_CONTAINER,
        /** 放不下且附近找不到空气放箱子 */
        NO_SPACE,
        /** 物品 id 无法解析（替代品没注册）、或 NBT 解析失败 */
        BAD_ITEM,
        /** 无法定位持有者 */
        UNRESOLVED
    }

    /** 一条条目的执行明细，供命令输出与日志使用 */
    public record Result(RestoreManifest.Entry entry, Outcome outcome, String detail) {
    }

    /** 汇总 */
    public static final class Report {
        public int inserted;
        public int newChest;
        public int mailed;
        public int dropped;
        public int skipped;
        public int failed;
        public final List<Result> results = new ArrayList<>();

        void add(Result result) {
            results.add(result);
            switch (result.outcome()) {
                case INSERTED -> inserted++;
                case NEW_CHEST -> newChest++;
                case MAILED -> mailed++;
                case DROPPED -> dropped++;
                case ALREADY_DONE -> skipped++;
                default -> failed++;
            }
        }

        /** 是否全部落地（无失败） */
        public boolean clean() {
            return failed == 0;
        }

        /** 成功补偿数（台账会记下的那些） */
        public int applied() {
            return inserted + newChest + mailed + dropped;
        }
    }

    private RestoreService() {
    }

    /**
     * 判断某个结局是否算「已落地」——是则写入台账，重跑时跳过。
     *
     * <p>{@link Outcome#DROPPED} 也算：数据已经写进载体（如 AE2 元件），
     * 只是载体掉在原地等人工拾取；若不算落地，重跑会把同一份数据再写一遍。
     */
    static boolean isApplied(Outcome outcome) {
        return outcome == Outcome.INSERTED
                || outcome == Outcome.NEW_CHEST
                || outcome == Outcome.MAILED
                || outcome == Outcome.DROPPED;
    }

    // ------------------------------------------------------------------ 单条执行

    static Result applyEntry(MinecraftServer server, RestoreManifest.Entry entry,
                             boolean dryRun) {
        if ("ae2_cell".equals(entry.type())) {
            return applyToAe2Cell(server, entry, dryRun);
        }
        // 无人引用的背包内容：没有坐标也没有主人，但内容还在背包存档里，可按 uuid 直接写回
        if ("backpack_storage_file".equals(entry.type())) {
            return applyToOrphanBackpack(entry, dryRun);
        }

        ItemStack stack = buildStack(entry);
        if (stack.isEmpty()) {
            return new Result(entry, Outcome.BAD_ITEM,
                    "无法构造物品（替代品 " + entry.remapTo() + " 未注册，或 NBT 解析失败）");
        }

        RestoreManifest.Holder outer = entry.outerHolder();
        return switch (outer.kind()) {
            case BLOCK -> applyToBlock(server, entry, stack, outer, dryRun);
            case PLAYER -> applyToPlayer(server, entry, stack, outer, dryRun);
            case ENTITY -> applyToEntity(server, entry, stack, outer, dryRun);
            case UNKNOWN -> new Result(entry, Outcome.UNRESOLVED,
                    "清单未给出可定位的持有者（type=" + entry.type() + "）");
        };
    }

    /**
     * 把清单里的 SNBT 还原成**目标物品**。
     *
     * <p>清单里的 id 仍是旧 id（{@code kubejs:*}），必须先改写成 {@code remapTo}——
     * 否则 {@code ItemStack.of} 对未注册 id 直接返回空（正是这些物品当初消失的原因）。
     * 其余 NBT（自定义名、lore、馅料等）原样保留。
     */
    private static ItemStack buildStack(RestoreManifest.Entry entry) {
        if (entry.snbt() == null || entry.remapTo() == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(entry.snbt());
        } catch (Exception e) {
            ToTheSky.LOGGER.warn("[补偿] SNBT 解析失败：{}（条目 #{}）", e.getMessage(), entry.index());
            return ItemStack.EMPTY;
        }
        Item target = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(entry.remapTo()));
        if (target == null) {
            return ItemStack.EMPTY;
        }
        tag.putString("id", entry.remapTo());
        tag.putInt("Count", entry.amount());

        ItemStack stack = ItemStack.of(tag);
        if (stack.isEmpty()) {
            // ItemStack.of 对「id 合法但 NBT 不合规」也会给空；再兜一次，至少把数量送出去
            stack = new ItemStack(target, entry.amount());
            ToTheSky.LOGGER.warn("[补偿] {} 的 NBT 无法还原为物品栈，已退化为无 NBT 的同名物品（条目 #{}）",
                    entry.remapTo(), entry.index());
        }
        return stack;
    }

    // ------------------------------------------------------------------ 无人引用的背包内容

    /**
     * 写回**无人引用的背包内容**（扫描器的 {@code backpack_storage_file}）。
     *
     * <p>这类条目的背包物品已经不在世界里，因此既没有坐标可定位、也没有主人可投递——
     * 原先只能报 {@code UNRESOLVED} 放弃。但内容本身还躺在精妙背包的全局存档里、
     * 以 {@code contentsUuid} 为键，所以可以按 uuid **直接写回那份内容**：
     * 物品就此重新回到该背包中（背包物品若日后回归，内容就在）。
     */
    private static Result applyToOrphanBackpack(RestoreManifest.Entry entry, boolean dryRun) {
        UUID uuid = uuidFromLabel(entry.label());
        if (uuid == null) {
            return new Result(entry, Outcome.UNRESOLVED,
                    "无法从 label 还原背包 uuid：" + entry.label());
        }
        if (!BackpackStorageBridge.loaded()) {
            return new Result(entry, Outcome.UNRESOLVED,
                    "未安装 SophisticatedBackpacks，无法写回背包内容");
        }
        ItemStack stack = buildStack(entry);
        if (stack.isEmpty()) {
            return new Result(entry, Outcome.BAD_ITEM,
                    "无法构造待写入物品（替代品 " + entry.remapTo() + " 未注册？）");
        }
        if (dryRun) {
            return new Result(entry, Outcome.INSERTED,
                    "演练：将直接写回背包内容 uuid=" + uuid + "（" + stack.getCount() + " 件）");
        }
        BackpackStorageBridge.Result written = BackpackStorageBridge.insert(uuid, stack);
        if (!written.ok()) {
            return new Result(entry, Outcome.NO_SPACE, written.detail());
        }
        return new Result(entry, Outcome.INSERTED, "背包内容 uuid=" + uuid + " " + written.detail());
    }

    /**
     * 还原扫描器记录的背包 uuid。
     * <p>扫描器把 {@code contentsUuid} 的 4 个 int 原样记成 {@code label="uuid=i0,i1,i2,i3"}；
     * 精妙背包用的是原版 {@code NbtUtils.createUUID}，字节序为
     * {@code [msb>>>32, msb, lsb>>>32, lsb]}，这里按同样的顺序还原。
     */
    @Nullable
    private static UUID uuidFromLabel(@Nullable String label) {
        if (label == null || !label.startsWith("uuid=")) {
            return null;
        }
        String[] parts = label.substring("uuid=".length()).split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            long msb = ((long) Integer.parseInt(parts[0].trim()) << 32)
                    | (Integer.parseInt(parts[1].trim()) & 0xFFFFFFFFL);
            long lsb = ((long) Integer.parseInt(parts[2].trim()) << 32)
                    | (Integer.parseInt(parts[3].trim()) & 0xFFFFFFFFL);
            return new UUID(msb, lsb);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 方块容器

    private static Result applyToBlock(MinecraftServer server, RestoreManifest.Entry entry,
                                       ItemStack stack, RestoreManifest.Holder holder, boolean dryRun) {
        ServerLevel level = levelOf(server, holder.dimension());
        if (level == null || holder.pos() == null) {
            return new Result(entry, Outcome.NO_CONTAINER,
                    "维度 " + holder.dimension() + " 不存在或清单没有坐标");
        }
        BlockPos pos = new BlockPos(holder.pos().get(0), holder.pos().get(1), holder.pos().get(2));
        if (!ensureChunk(level, pos)) {
            return new Result(entry, Outcome.NO_CONTAINER,
                    "坐标 " + pos.toShortString() + " 所在区块加载失败");
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        @Nullable IItemHandler handler = blockEntity == null ? null : handlerOf(blockEntity);
        // 有些容器只实现原版 Container（如 plonk:placed_items 的 WorldlyContainer）、
        // 不注册 Forge 的 IItemHandler 能力。此时若直接判「无容器」就会去放新箱子，
        // 物品虽然没丢、却不再回到原方块里。用 InvWrapper 把原版容器接上。
        if (handler == null && blockEntity instanceof Container container) {
            handler = new InvWrapper(container);
        }

        if (dryRun) {
            if (handler != null) {
                return new Result(entry, Outcome.INSERTED, "演练：将写入 " + pos.toShortString()
                        + "（" + stack.getCount() + " 件）");
            }
            return new Result(entry, Outcome.NEW_CHEST, "演练：原容器已不存在，将在 "
                    + pos.toShortString() + " 附近放箱子（" + stack.getCount() + " 件）");
        }

        ItemStack leftover = stack.copy();
        if (handler != null) {
            int preferred = entry.slot() == null ? -1 : entry.slot();
            leftover = insertInto(handler, stack, preferred);
            markChanged(level, pos, blockEntity);
            if (leftover.isEmpty()) {
                return new Result(entry, Outcome.INSERTED, pos.toShortString()
                        + (preferred >= 0 ? " 槽位 " + preferred : ""));
            }
        }

        // 容器装不下 / 容器已不存在：就近放一个新箱子
        String reason = handler == null
                ? "原容器已不存在"
                : "原容器装不下";
        return placeChest(level, pos, leftover, entry, reason);
    }

    /**
     * 在 {@code origin} 处（若已是空气）或附近找空气方块放箱子并放入物品。
     * <p>找不到可放的位置就如实报 {@link Outcome#NO_SPACE}——不静默丢弃。
     */
    private static Result placeChest(ServerLevel level, BlockPos origin, ItemStack leftover,
                                     RestoreManifest.Entry entry, String reason) {
        BlockPos chestPos = level.getBlockState(origin).isAir() ? origin : findAirNear(level, origin);
        if (chestPos == null) {
            return new Result(entry, Outcome.NO_SPACE, reason + "，且 " + CHEST_SEARCH_RADIUS
                    + " 格内无空气可放箱子（缺 " + leftover.getCount() + " 件）");
        }
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity chest = level.getBlockEntity(chestPos);
        @Nullable IItemHandler chestHandler = chest == null ? null : handlerOf(chest);
        if (chestHandler == null) {
            return new Result(entry, Outcome.NO_SPACE, "在 " + chestPos.toShortString()
                    + " 放了箱子但取不到容器（缺 " + leftover.getCount() + " 件）");
        }
        ItemStack stillLeft = insertInto(chestHandler, leftover, -1);
        if (!stillLeft.isEmpty()) {
            return new Result(entry, Outcome.NO_SPACE, "新箱子 " + chestPos.toShortString()
                    + " 也装不下（还缺 " + stillLeft.getCount() + " 件）");
        }
        if (chest != null) {
            chest.setChanged();
        }
        return new Result(entry, Outcome.NEW_CHEST,
                reason + "，已放到新箱子 " + chestPos.toShortString());
    }

    /** 先试优先槽位，再试其它空位；返回未插入的剩余部分 */
    private static ItemStack insertInto(IItemHandler handler, ItemStack stack, int preferred) {
        ItemStack leftover = stack.copy();
        if (preferred >= 0 && preferred < handler.getSlots()) {
            leftover = handler.insertItem(preferred, leftover, false);
            if (leftover.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        for (int i = 0; i < handler.getSlots() && !leftover.isEmpty(); i++) {
            if (i == preferred) {
                continue;
            }
            leftover = handler.insertItem(i, leftover, false);
        }
        return leftover;
    }

    /**
     * 在原点附近找第一个空气方块，按「半径由近及远、同层优先」的顺序。
     * <p>半径上限 {@value #CHEST_SEARCH_RADIUS}；找不到返回 null。
     */
    @Nullable
    private static BlockPos findAirNear(ServerLevel level, BlockPos origin) {
        for (int r = 1; r <= CHEST_SEARCH_RADIUS; r++) {
            // 同一层优先，再向上下对称扩
            for (int dy = 0; dy <= r; dy++) {
                int signs = dy == 0 ? 1 : 2;
                for (int s = 0; s < signs; s++) {
                    int y = origin.getY() + (s == 0 ? dy : -dy);
                    if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                        continue;
                    }
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            // 只取该半径的“壳”，避免重复检查内层
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            BlockPos candidate = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                            // 只看已加载的区块：避免 getBlockState 触发隐式区块加载造成卡顿
                            if (!level.isLoaded(candidate)) {
                                continue;
                            }
                            if (level.getBlockState(candidate).isAir()) {
                                return candidate;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void markChanged(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        blockEntity.setChanged();
        // 让周围的观察者/客户端看到容器变化（纯原版机制，不需要模组客户端）
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    // ------------------------------------------------------------------ 玩家

    /**
     * 玩家的物品栏/末影箱/饰品栏：**先直接进背包**，背包放不下才整份走往来包裹。
     *
     * <p>直接插背包的好处是不用玩家跑去开邮箱。
     *
     * <p><b>为什么放不下要整份改寄、而不是「进一部分、寄一部分」</b>：
     * 拆分会在邮寄失败时（Contact 未装、昵称非法）留下「一半已进背包」的状态，
     * 而该条目的台账不会记（因为算失败），重跑就会把那一半再加一次——重复发放。
     * 因此这里把已塞进去的<b>原样退回</b>，整份交给邮寄；邮寄失败则净变化为零，可安全重跑。
     */
    private static Result applyToPlayer(MinecraftServer server, RestoreManifest.Entry entry,
                                        ItemStack stack, RestoreManifest.Holder holder, boolean dryRun) {
        ServerPlayer online = onlinePlayer(server, holder);
        String name = online != null ? online.getGameProfile().getName() : nameOf(server, holder);
        if (name == null) {
            return new Result(entry, Outcome.UNRESOLVED,
                    "无法确定收件人（uuid=" + holder.playerUuid() + "，name=" + holder.playerName() + "）");
        }

        if (online == null) {
            // 不在线：寄包裹（Contact 会在其上线且邮箱有空位时送达）
            return mail(entry, name, stack, dryRun, "玩家不在线");
        }
        if (dryRun) {
            return new Result(entry, Outcome.INSERTED,
                    "演练：将放入 " + name + " 的背包（" + stack.getCount() + " 件）");
        }

        ItemStack toAdd = stack.copy();
        boolean all = online.getInventory().add(toAdd);
        if (all) {
            return new Result(entry, Outcome.INSERTED, name + " 的背包（" + stack.getCount() + " 件）");
        }
        int added = stack.getCount() - toAdd.getCount();
        if (added > 0) {
            removeFromInventory(online, stack, added);
        }
        return mail(entry, name, stack, false,
                added > 0 ? "背包放不下（已退回试放的 " + added + " 件）" : "背包已满");
    }

    /** 从背包退回 {@code count} 个与 {@code template} 同类的物品（用于撤掉试放） */
    private static void removeFromInventory(ServerPlayer player, ItemStack template, int count) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, template)) {
                continue;
            }
            int take = Math.min(count, slot.getCount());
            slot.shrink(take);
            count -= take;
            if (slot.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
    }

    private static Result mail(RestoreManifest.Entry entry, String recipient, ItemStack stack,
                               boolean dryRun, String why) {
        if (dryRun) {
            return new Result(entry, Outcome.MAILED,
                    "演练：将寄包裹给 " + recipient + "（" + stack.getCount() + " 件，" + why + "）");
        }
        if (stack.isEmpty()) {
            return new Result(entry, Outcome.ALREADY_DONE, why + "，无需邮寄（无剩余）");
        }
        boolean ok = ContactMail.sendParcel(recipient, LocalDate.now(), List.of(stack.copy()));
        if (!ok) {
            return new Result(entry, Outcome.UNRESOLVED,
                    why + "，且邮寄失败（Contact 未安装或昵称非法：" + recipient + "）");
        }
        return new Result(entry, Outcome.MAILED, why + "，已寄往 " + recipient);
    }

    @Nullable
    private static ServerPlayer onlinePlayer(MinecraftServer server, RestoreManifest.Holder holder) {
        if (holder.playerUuid() == null) {
            return holder.playerName() == null ? null
                    : server.getPlayerList().getPlayerByName(holder.playerName());
        }
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(holder.playerUuid()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static String nameOf(MinecraftServer server, RestoreManifest.Holder holder) {
        if (holder.playerUuid() != null) {
            try {
                UUID uuid = UUID.fromString(holder.playerUuid());
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null) {
                    return online.getGameProfile().getName();
                }
                // 不在线：查 usercache（覆盖所有进过服的玩家，含离线模式）
                var cache = server.getProfileCache();
                var profile = cache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty()
                        : cache.get(uuid);
                if (profile.isPresent()) {
                    return profile.get().getName();
                }
            } catch (IllegalArgumentException ignored) {
                // UUID 格式非法，落到名字兜底
            }
        }
        return holder.playerName();
    }

    // ------------------------------------------------------------------ AE2 元件

    /**
     * AE2 存储元件：用 AE2 官方存储 API 把物品写回元件。
     *
     * <p><b>为什么要先把元件取出来</b>：元件挂在驱动器上时，AE2 已按旧内容建立了内存镜像；
     * 取出会让它卸载、写入后放回则按新 NBT 重新挂载。整个过程走 AE2 自己的槽位逻辑，
     * 我们不直接碰它的持久化格式（那是内部实现，见 {@link Ae2CellBridge}）。
     *
     * <p><b>不需要处理「旧键残留」</b>：{@code kubejs:*} 这类未注册 id 本身就无法被
     * {@code AEItemKey} 表示（AE2 装载时会跳过、持久化时不再写回），因此不会重复计数。
     *
     * <p>失败时元件一律放回原位（或任一可接受槽位），绝不因补偿而丢失元件本身。
     */
    private static Result applyToAe2Cell(MinecraftServer server, RestoreManifest.Entry entry,
                                         boolean dryRun) {
        if (!Ae2CellBridge.loaded()) {
            return new Result(entry, Outcome.UNRESOLVED, "未安装 AE2，无法写回 ME 元件");
        }

        RestoreManifest.Holder drive = entry.outerHolder();
        ServerLevel level = levelOf(server, drive.dimension());
        if (level == null || drive.pos() == null) {
            return new Result(entry, Outcome.UNRESOLVED, "维度不存在或清单没有驱动器坐标");
        }
        Matcher matcher = AE2_DRIVE_SLOT.matcher(entry.path());
        if (!matcher.find()) {
            return new Result(entry, Outcome.UNRESOLVED,
                    "无法从 path 解析驱动器槽位：" + entry.path());
        }
        int slot = Integer.parseInt(matcher.group(1));

        BlockPos pos = new BlockPos(drive.pos().get(0), drive.pos().get(1), drive.pos().get(2));
        if (!ensureChunk(level, pos)) {
            return new Result(entry, Outcome.NO_CONTAINER,
                    "坐标 " + pos.toShortString() + " 所在区块加载失败");
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        @Nullable IItemHandler handler = blockEntity == null ? null : handlerOf(blockEntity);
        if (handler == null) {
            return new Result(entry, Outcome.NO_CONTAINER,
                    pos.toShortString() + " 没有可用的物品容器（驱动器可能已被拆）");
        }
        if (slot < 0 || slot >= handler.getSlots()) {
            return new Result(entry, Outcome.NO_CONTAINER,
                    "驱动器槽位 " + slot + " 超出范围（0.." + (handler.getSlots() - 1) + "）");
        }

        // 要写进去的物品：用清单的 SNBT（id 已换成替代品）构造
        ItemStack prototype = buildStack(entry);
        if (prototype.isEmpty()) {
            return new Result(entry, Outcome.BAD_ITEM,
                    "无法构造待写入物品（替代品 " + entry.remapTo() + " 未注册？）");
        }
        int amount = entry.amount();
        if (dryRun) {
            return new Result(entry, Outcome.INSERTED,
                    "演练：将用 AE2 API 把 " + entry.remapTo() + " x" + amount + " 写入 "
                            + pos.toShortString() + " 的槽位 " + slot);
        }

        ItemStack cell = handler.extractItem(slot, 1, false);
        if (cell.isEmpty()) {
            return new Result(entry, Outcome.NO_CONTAINER, "驱动器槽位 " + slot + " 是空的");
        }
        Ae2CellBridge.Result written = Ae2CellBridge.insert(cell, prototype, amount);
        if (!written.ok()) {
            dropOrReturn(handler, slot, cell, level, pos);
            return new Result(entry, Outcome.BAD_ITEM, written.detail());
        }
        ItemStack leftover = putCellBack(handler, slot, cell, level, pos);
        if (!leftover.isEmpty()) {
            // 元件回不到驱动器：宁可掉在原地也不让它凭空消失（数据已写进去了）
            dropAt(level, pos, leftover);
            return new Result(entry, Outcome.DROPPED,
                    "已写入元件，但元件放不回驱动器，已掉落在 " + pos.toShortString() + " 上方，请手动取回");
        }
        return new Result(entry, Outcome.INSERTED,
                pos.toShortString() + " 槽位 " + slot + " " + written.detail());
    }

    /** 写入失败且元件放不回时：先试回原位，实在不行也掉出来，绝不让它消失 */
    private static void dropOrReturn(IItemHandler handler, int slot, ItemStack cell,
                                     ServerLevel level, BlockPos pos) {
        ItemStack leftover = putCellBack(handler, slot, cell, level, pos);
        if (!leftover.isEmpty()) {
            dropAt(level, pos, leftover);
        }
    }

    private static void dropAt(ServerLevel level, BlockPos pos, ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    /** 把元件放回槽位；放不回则试其它槽位，返回剩余（非空表示没放进去） */
    private static ItemStack putCellBack(IItemHandler handler, int slot, ItemStack cell,
                                         ServerLevel level, BlockPos pos) {
        ItemStack leftover = handler.insertItem(slot, cell, false);
        if (leftover.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < handler.getSlots() && !leftover.isEmpty(); i++) {
            if (i == slot) {
                continue;
            }
            leftover = handler.insertItem(i, leftover, false);
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
        return leftover;
    }

    // ------------------------------------------------------------------ 实体

    private static Result applyToEntity(MinecraftServer server, RestoreManifest.Entry entry,
                                        ItemStack stack, RestoreManifest.Holder holder, boolean dryRun) {
        ServerLevel level = levelOf(server, holder.dimension());
        if (level == null || holder.entityUuid() == null) {
            return new Result(entry, Outcome.UNRESOLVED,
                    "清单未给出实体 UUID，或维度不存在：" + holder.dimension());
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(holder.entityUuid());
        } catch (IllegalArgumentException e) {
            return new Result(entry, Outcome.UNRESOLVED, "实体 UUID 格式非法：" + holder.entityUuid());
        }
        if (holder.pos() != null) {
            BlockPos approx = new BlockPos(holder.pos().get(0), holder.pos().get(1), holder.pos().get(2));
            if (!ensureChunk(level, approx)) {
                return new Result(entry, Outcome.NO_CONTAINER,
                        "实体所在区块 " + approx.toShortString() + " 加载失败");
            }
        }
        Entity entity = level.getEntity(uuid);
        if (entity == null) {
            return new Result(entry, Outcome.NO_CONTAINER, "实体不存在或未加载：" + uuid);
        }
        if (dryRun) {
            return new Result(entry, Outcome.INSERTED, "演练：将写入实体 " + entity.getType());
        }
        if (entity instanceof ItemFrame frame) {
            if (!frame.getItem().isEmpty()) {
                return new Result(entry, Outcome.NO_SPACE, "展示框已有物品：" + uuid);
            }
            frame.setItem(stack.copy());
            return new Result(entry, Outcome.INSERTED, "展示框 " + uuid);
        }
        @Nullable IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) {
            @Nullable Container container = entity instanceof Container c ? c : null;
            if (container == null) {
                return new Result(entry, Outcome.NO_CONTAINER,
                        "实体 " + entity.getType() + " 无物品容器能力");
            }
            handler = new InvWrapper(container);
        }
        ItemStack leftover = ItemHandlerHelper.insertItem(handler, stack.copy(), false);
        if (leftover.isEmpty()) {
            return new Result(entry, Outcome.INSERTED, "实体 " + entity.getType() + " " + uuid);
        }
        return placeChest(level, entity.blockPosition(), leftover, entry, "实体容器已满");
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 确保目标方块所在区块已加载，成功返回 true。
     *
     * <p>{@code Level.getChunk} 是**同步阻塞加载**——这正是补偿必须跨 tick 切片的原因。
     * 加载失败时它会抛 {@code IllegalStateException}（原版源码注释：「Should always be able to
     * create a chunk!」），这里兜住并返回 false，让调用方报 NO_CONTAINER，
     * 而不是让异常把整条任务链打断。
     *
     * <p>顺带一提：{@code getChunk} 不追加常驻 ticket，加载进来的区块随后会被正常卸载，
     * 不会因为补偿而长期占住内存。
     */
    private static boolean ensureChunk(ServerLevel level, BlockPos pos) {
        try {
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            return true;
        } catch (RuntimeException e) {
            ToTheSky.LOGGER.warn("[补偿] 区块 ({}, {}) 加载失败：{}",
                    pos.getX() >> 4, pos.getZ() >> 4, e.toString());
            return false;
        }
    }

    @Nullable
    private static IItemHandler handlerOf(BlockEntity blockEntity) {
        for (Direction side : Direction.values()) {
            IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (handler != null) {
                return handler;
            }
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
    }

    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return server.overworld();
        }
        return server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    static void log(RestoreManifest.Entry entry, Result result, boolean dryRun) {
        String prefix = dryRun ? "[补偿/演练] " : "[补偿] ";
        switch (result.outcome()) {
            case INSERTED, NEW_CHEST, MAILED -> ToTheSky.LOGGER.info("{}#{} {} -> {}（{}）",
                    prefix, entry.index(), entry.itemId(), result.outcome(), result.detail());
            case DROPPED -> ToTheSky.LOGGER.warn("{}#{} {} 已写入但载体掉落（{}）",
                    prefix, entry.index(), entry.itemId(), result.detail());
            case ALREADY_DONE -> ToTheSky.LOGGER.debug("{}#{} 跳过：{}", prefix, entry.index(), result.detail());
            default -> ToTheSky.LOGGER.warn("{}#{} 未能落地：{}（{}）",
                    prefix, entry.index(), result.detail(), entry.path());
        }
    }

    /** 给命令输出用的一句话摘要 */
    public static Component summary(Report report, boolean dryRun) {
        StringBuilder sb = new StringBuilder();
        if (dryRun) {
            sb.append("[演练] ");
        }
        sb.append(String.format("完成：写入容器 %d，新放箱子 %d，邮件投递 %d",
                report.inserted, report.newChest, report.mailed));
        if (report.dropped > 0) {
            sb.append(String.format("，掉落在原地 %d", report.dropped));
        }
        sb.append(String.format("，跳过 %d，失败 %d", report.skipped, report.failed));
        return Component.literal(sb.toString());
    }
}
