package com.fst.tothesky.restore;

import com.fst.tothesky.ToTheSky;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一次补偿任务的执行状态机：把清单条目**分摊到多个 tick**处理。
 *
 * <p><b>为什么不一口气跑完</b>：清单可能有几千条，每条都可能触发一次**阻塞式区块加载**
 * （{@code level.getChunk}）与容器写入。若全塞在一个 tick 里，服务器会卡住数秒到数分钟，
 * 触发看门狗（默认 60 秒无响应即崩服），期间所有玩家卡死。所以这里按
 * 「时间预算 + 条数上限」切片，每 tick 只做一小段，做完立刻把 tick 还给服务器。
 *
 * <p><b>预算怎么算</b>：先看时间（{@value #MILLIS_PER_TICK} 毫秒），再看条数
 * （{@value #MAX_PER_TICK} 条），两者任一超了就停；但**每条之前都不检查时间**，
 * 保证每 tick 至少推进一条——否则遇到单条特别慢（大区块）时会永远卡在原地。
 *
 * <p><b>中断安全</b>：每条成功即写台账（{@link RestoreLedger}），所以中途停服/崩服后
 * 重跑会跳过已完成的条目，不会重复发放。任务只活在内存里，重跑即重建。
 *
 * <p><b>线程</b>：所有处理都在服务器线程（{@link TickEvent.ServerTickEvent} 的 END 阶段），
 * 方块实体与 SavedData 都不是线程安全的，绝不能挪到异步线程。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class RestoreTask {

    /** 每 tick 的时间预算（毫秒）：留足余量给服务器做正常 tick 工作 */
    private static final long MILLIS_PER_TICK = 5L;

    /** 每 tick 最多处理的条数：防止条目极便宜时一口气吃掉整个 tick */
    private static final int MAX_PER_TICK = 64;

    /** bossbar 刷新间隔（条）：避免每件物品都发一个包 */
    private static final int BAR_UPDATE_EVERY = 8;

    /** 正在执行的任务（同一服务器同时只允许一个） */
    private static final List<RestoreTask> ACTIVE = new ArrayList<>();

    private final MinecraftServer server;
    private final List<RestoreManifest.Entry> queue;
    private final RestoreLedger ledger;
    private final boolean dryRun;
    private final UUID requesterId;
    private final String requesterName;
    private final RestoreService.Report report = new RestoreService.Report();
    @Nullable
    private final ServerBossEvent bar;

    private int cursor;
    private boolean finished;

    private RestoreTask(MinecraftServer server, List<RestoreManifest.Entry> queue, RestoreLedger ledger,
                        boolean dryRun, @Nullable ServerPlayer requester) {
        this.server = server;
        this.queue = queue;
        this.ledger = ledger;
        this.dryRun = dryRun;
        this.requesterId = requester == null ? null : requester.getUUID();
        this.requesterName = requester == null ? null : requester.getGameProfile().getName();
        this.bar = createBar(requester, queue.size(), dryRun);
    }

    // ------------------------------------------------------------------ 生命周期

    /**
     * 启动一次补偿任务，并**立即执行第一片**。
     *
     * <p>立即跑第一片是为了让常见的小清单（几十条以内）在同一 tick 内就出结果，
     * 命令能直接给出摘要；只有超出一片预算的清单才会留到后续 tick 继续。
     */
    public static RestoreTask start(MinecraftServer server, RestoreManifest manifest,
                                    boolean dryRun, @Nullable ServerPlayer requester) {
        List<RestoreManifest.Entry> queue = manifest.restorable();
        RestoreLedger ledger = dryRun ? null : RestoreLedger.get(server);
        RestoreTask task = new RestoreTask(server, queue, ledger, dryRun, requester);
        synchronized (ACTIVE) {
            ACTIVE.add(task);
        }
        task.tick();
        return task;
    }

    /** 同一服务器上是否已有任务在执行（命令据此拒绝并发启动） */
    public static boolean busy(MinecraftServer server) {
        synchronized (ACTIVE) {
            return ACTIVE.stream().anyMatch(task -> task.server == server && !task.finished);
        }
    }

    public boolean finished() {
        return finished;
    }

    public RestoreService.Report report() {
        return report;
    }

    // ------------------------------------------------------------------ 每 tick 一片

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<RestoreTask> snapshot;
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) {
                return;
            }
            snapshot = List.copyOf(ACTIVE);
        }
        for (RestoreTask task : snapshot) {
            task.tick();
        }
    }

    /** 服务器停止：丢弃未完成的任务（台账已记录成功的那些，重跑即续上） */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        List<RestoreTask> doomed;
        synchronized (ACTIVE) {
            doomed = ACTIVE.stream().filter(task -> task.server == event.getServer()).toList();
            ACTIVE.removeAll(doomed);
        }
        for (RestoreTask task : doomed) {
            if (!task.finished) {
                task.finished = true;
                ToTheSky.LOGGER.warn("[补偿] 服务器停止，任务中断于 {}/{}（已完成的不受影响，重跑继续）",
                        task.cursor, task.queue.size());
            }
            if (task.bar != null) {
                task.bar.removeAllPlayers();
            }
        }
    }

    private void tick() {
        if (finished) {
            return;
        }
        if (!server.isRunning()) {
            // 服务器正在停：只做清理，不碰玩家列表（此刻它可能已不可用）
            finish(false);
            return;
        }
        long deadline = System.nanoTime() + MILLIS_PER_TICK * 1_000_000L;
        int budget = MAX_PER_TICK;

        while (cursor < queue.size() && budget-- > 0) {
            RestoreManifest.Entry entry = queue.get(cursor++);
            updateBar(cursor, entry, cursor == 1 || cursor == queue.size());

            String fingerprint = RestoreLedger.fingerprint(entry);
            if (ledger != null && ledger.contains(fingerprint)) {
                report.add(new RestoreService.Result(entry, RestoreService.Outcome.ALREADY_DONE,
                        "台账已有记录"));
                continue;
            }
            RestoreService.Result result;
            try {
                result = RestoreService.applyEntry(server, entry, dryRun);
            } catch (RuntimeException e) {
                // 单条炸掉不能带走整个任务：区块加载失败会抛 IllegalStateException，
                // 若任其冒泡，任务会卡在 ACTIVE 里让 busy() 永远为真、之后再也启动不了补偿。
                result = new RestoreService.Result(entry, RestoreService.Outcome.UNRESOLVED,
                        "处理时抛出 " + e.getClass().getSimpleName() + "：" + e.getMessage());
                ToTheSky.LOGGER.warn("[补偿] 条目 #{} 处理异常（已跳过，任务继续）", entry.index(), e);
            }
            report.add(result);
            if (!dryRun && ledger != null && RestoreService.isApplied(result.outcome())) {
                ledger.mark(fingerprint);
            }
            RestoreService.log(entry, result, dryRun);

            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (cursor >= queue.size()) {
            finish();
        }
    }

    private void finish() {
        finish(true);
    }

    /** @param notify 是否给触发者发完成消息（服务器正在停时为 false） */
    private void finish(boolean notify) {
        if (finished) {
            return;
        }
        finished = true;
        if (bar != null) {
            bar.removeAllPlayers();
        }
        synchronized (ACTIVE) {
            ACTIVE.remove(this);
        }

        String label = dryRun ? "演练" : "执行";
        ToTheSky.LOGGER.info("[补偿] {}完成（清单 {} 条）：写入 {}，新箱 {}，邮件 {}，掉落 {}，跳过 {}，失败 {}",
                label, queue.size(), report.inserted, report.newChest, report.mailed,
                report.dropped, report.skipped, report.failed);

        if (!notify || requesterId == null) {
            return;
        }
        ServerPlayer requester = server.getPlayerList().getPlayer(requesterId);
        if (requester != null) {
            requester.sendSystemMessage(RestoreService.summary(report, dryRun));
            if (!report.clean()) {
                requester.sendSystemMessage(Component.literal("[补偿] 有 " + report.failed
                        + " 条未落地（明细见日志）；修正后可直接重跑，已成功的不会重复发放"));
            }
        } else {
            ToTheSky.LOGGER.info("[补偿] 触发者 {} 已离线，结果仅写入日志", requesterName);
        }
    }

    // ------------------------------------------------------------------ 进度条

    @Nullable
    private static ServerBossEvent createBar(@Nullable ServerPlayer player, int total, boolean dryRun) {
        if (player == null || total <= 0) {
            return null;
        }
        ServerBossEvent created = new ServerBossEvent(
                Component.literal((dryRun ? "[演练] " : "") + "补偿中 0/" + total),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS);
        created.setProgress(0f);
        created.addPlayer(player);
        return created;
    }

    private void updateBar(int done, RestoreManifest.Entry entry, boolean force) {
        if (bar == null) {
            return;
        }
        if (!force && done % BAR_UPDATE_EVERY != 0) {
            return;
        }
        bar.setProgress(Math.min(1f, (float) done / queue.size()));
        bar.setName(Component.literal(String.format("%s补偿中 %d/%d  %s",
                dryRun ? "[演练] " : "", done, queue.size(), entry.itemId())));
    }
}
