package com.fst.tothesky.command;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarData;
import com.fst.tothesky.calendar.CalendarEvent;
import com.fst.tothesky.contact.LetterScheduler;
import com.fst.tothesky.network.ModNetwork;
import com.fst.tothesky.restore.RestoreLedger;
import com.fst.tothesky.restore.RestoreManifest;
import com.fst.tothesky.restore.RestoreService;
import com.fst.tothesky.restore.RestoreTask;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * {@code /tothesky} 命令树。
 *
 * <p>{@code /tothesky reloadletters}：重读 {@code config/tothesky/letters} 并立刻投递一轮。
 * 配置不会自动热重载（见 {@link LetterScheduler}），所以「改完 json」到「生效」之间就差这条命令；
 * 顺便它也会马上把当天该发的信投出去，省掉等下一轮 60 秒检查。
 *
 * <p><b>今天的信会重发</b>：这条命令会把「本应在今天投递」的信重新武装并再发一次——
 * 哪怕它今天已经投过了。所以连跑两次，收件人就会收到两份；这是给「改完 json 想立刻看到效果 /
 * 手工补发」用的，日常的 60 秒检查不重发（见 {@link LetterScheduler#reload}）。
 * <p>权限等级 2（与 {@code /reload} 同级）：重载会改存档里的信件排期。
 *
 * <p>{@code /tothesky setbirthday <month> <day> [isnongli]}：把**执行者自己**的生日写进日历
 * （{@code type=birthday} 的活动，{@code name} = 昵称，图标 = 自己的玩家头）。
 * <b>所有玩家可用</b>——生日是玩家的个人信息，没有理由要求管理员代劳；这也意味着它**只能改自己**的
 * 生日（没有目标玩家参数，避免互相乱改）。{@code isnongli} 缺省为 {@code false}（公历），
 * 为 {@code true} 时 {@code month}/{@code day} 按**农历**解释，逐年换算成公历日子（见
 * {@link com.fst.tothesky.calendar.LunarCalendar}）。
 * <p>同一个人重复执行 = 覆盖（不是新增第二条）；已有生日时保留原有的图标与描述。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class ToTheSkyCommands {
    /** 重载节日信/生日信配置所需的权限等级（与 /reload 同级） */
    private static final int RELOAD_PERMISSION = 2;

    /**
     * 执行补偿所需的权限等级。
     * <p>比 {@code /reload} 更高：补偿会往世界与玩家手里发实物，属于一次性的运维动作，
     * 不该被普通管理员顺手执行。
     */
    private static final int RESTORE_PERMISSION = 3;

    /** 农历单月最多 30 天（大月 30、小月 29；闰月不参与换算） */
    private static final int LUNAR_MAX_DAY = 30;

    private ToTheSkyCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(tree());
    }

    /** 命令树（不含 dispatcher 依赖，便于离线把玩各节点的权限与结构） */
    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal(ToTheSky.MODID)
                .then(Commands.literal("reloadletters")
                        .requires(source -> source.hasPermission(RELOAD_PERMISSION))
                        .executes(ToTheSkyCommands::reloadLetters))
                // 不加 requires：所有玩家都能登记自己的生日
                .then(Commands.literal("setbirthday")
                        .then(Commands.argument("month", IntegerArgumentType.integer(1, 12))
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, LUNAR_MAX_DAY))
                                        .executes(context -> setBirthday(context, false))
                                        .then(Commands.argument("isnongli", BoolArgumentType.bool())
                                                .executes(context -> setBirthday(context,
                                                        BoolArgumentType.getBool(context, "isnongli")))))))
                // 扫描器补偿清单的执行入口（见 tools/save-scanner）
                .then(Commands.literal("restore")
                        .requires(source -> source.hasPermission(RESTORE_PERMISSION))
                        .executes(context -> restore(context, false))
                        .then(Commands.literal("dry")
                                .executes(context -> restore(context, true)))
                        .then(Commands.literal("status")
                                .executes(ToTheSkyCommands::restoreStatus)));
    }

    /**
     * 启动扫描器补偿清单的执行。
     *
     * <p>默认读 {@code config/tothesky/restore.json}（由 {@code tools/save-scanner} 生成，拷进来即可）。
     * {@code dry} 子命令只报告会做什么、不改动任何数据——<b>先在正式存档上跑 dry 是推荐流程</b>。
     *
     * <p><b>执行是跨 tick 的</b>：条目按时间预算切片处理（见 {@link RestoreTask}），
     * 不会把服务器卡在一个 tick 里。小清单（几十条内）通常当场跑完、直接给出摘要；
     * 大清单会立即返回并继续在后台推进，进度显示在 bossbar、完成时再通知触发者。
     *
     * <p>幂等：成功补偿的条目记在 {@code tothesky_restore_ledger}，重跑自动跳过，
     * 因此「执行两次」不会发双份；失败条目可以改正清单后重跑补齐。
     */
    private static int restore(CommandContext<CommandSourceStack> context, boolean dryRun) {
        CommandSourceStack source = context.getSource();
        Path file = RestoreManifest.defaultFile();

        if (!Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("[补偿] 找不到清单：" + file
                    + "\n把 tools/save-scanner 生成的 restore.json 放到 config/tothesky/ 下"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        if (RestoreTask.busy(server)) {
            source.sendFailure(Component.literal("[补偿] 已有补偿任务在执行，请等它结束（或看 bossbar 进度）"));
            return 0;
        }
        RestoreManifest manifest;
        try {
            manifest = RestoreManifest.load(file);
        } catch (IOException | IllegalArgumentException e) {
            source.sendFailure(Component.literal("[补偿] 清单无法读取：" + e.getMessage()));
            ToTheSky.LOGGER.warn("[补偿] 清单读取失败：{}", e.getMessage());
            return 0;
        }

        if (manifest.restorable().isEmpty()) {
            source.sendSuccess(() -> Component.literal("[补偿] 清单里没有需要补偿的条目（共 "
                    + manifest.entries().size() + " 条，均非 missing）"), false);
            return Command.SINGLE_SUCCESS;
        }

        ServerPlayer requester = source.getEntity() instanceof ServerPlayer player ? player : null;
        RestoreTask task = RestoreTask.start(server, manifest, dryRun, requester);

        if (task.finished()) {
            // 小清单：一片就做完了，直接给结果（完成消息已由 RestoreTask 发出）
            return task.report().clean() ? Command.SINGLE_SUCCESS : 0;
        }
        int total = manifest.restorable().size();
        source.sendSuccess(() -> Component.literal(String.format(
                "[补偿] 已开始%s：共 %d 条，正在跨 tick 处理（进度见 bossbar，完成后会通知你）",
                dryRun ? "演练" : "", total)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** 查看当前清单状态与台账规模（不动数据） */
    private static int restoreStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Path file = RestoreManifest.defaultFile();
        if (!Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("[补偿] 找不到清单：" + file));
            return 0;
        }
        try {
            RestoreManifest manifest = RestoreManifest.load(file);
            RestoreLedger ledger = RestoreLedger.get(source.getServer());
            source.sendSuccess(() -> Component.literal(String.format(
                    "[补偿] 清单 %s（formatVersion=%d，生成于 %s）\n条目 %d，其中待补偿 %d\n台账已记录 %d 条",
                    file.getFileName(), manifest.formatVersion(), manifest.generatedAt(),
                    manifest.entries().size(), manifest.restorable().size(), ledger.size())), false);
            return Command.SINGLE_SUCCESS;
        } catch (IOException | IllegalArgumentException e) {
            source.sendFailure(Component.literal("[补偿] 清单无法读取：" + e.getMessage()));
            return 0;
        }
    }

    private static int reloadLetters(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        LetterScheduler.ReloadResult result = LetterScheduler.reload(server);

        source.sendSuccess(() -> Component.literal("[节日信] 重载完成：启用 " + result.loaded() + " 封"), true);
        if (!result.contactAvailable()) {
            source.sendSuccess(() -> Component.literal("[节日信] 未安装 Contact，本次未投递（排期保留）"), false);
        } else if (result.delivered() == 0) {
            source.sendSuccess(() -> Component.literal("[节日信] 当前没有到期的信"), false);
        } else {
            source.sendSuccess(() -> Component.literal("[节日信] 已投递 " + result.delivered() + " 封"), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 登记/覆盖执行者自己的生日。
     * <p>玩家来源只认 {@link ServerPlayer}：控制台没有生日可登记，也没有昵称可当收件人。
     */
    private static int setBirthday(CommandContext<CommandSourceStack> context, boolean lunar) {
        CommandSourceStack source = context.getSource();
        int month = IntegerArgumentType.getInteger(context, "month");
        int day = IntegerArgumentType.getInteger(context, "day");
        // 先校验再要玩家：参数写错了就直接说参数的事，别扯到「只能由玩家执行」
        String error = validate(lunar, month, day);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("这条命令只能由玩家执行：生日是登记给某个昵称的"));
            return 0;
        }
        String name = player.getGameProfile().getName();

        MinecraftServer server = source.getServer();
        CalendarEvent event = registerBirthday(CalendarData.get(server), name, month, day, lunar);
        ModNetwork.broadcastCalendar(server);

        LocalDate today = LocalDate.now();
        LocalDate next = LocalDate.ofEpochDay(event.nextOccurrenceOn(today));
        String dateText = lunar
                ? "农历" + com.fst.tothesky.calendar.LunarCalendar.describe(month, day)
                : month + "月" + day + "日";
        // 只回给执行者本人（生日是个人信息），服务端日志里留一条给管理员
        source.sendSuccess(() -> Component.literal("已把你的生日设为 " + dateText
                + "（" + (lunar ? "农历" : "公历") + "），下一次是 " + next
                + (next.equals(today) ? "（也就是今天，一分钟内会收到生日信）" : "")), false);
        ToTheSky.LOGGER.info("[生日] {} 把生日设为 {}{}（下一次 {}）",
                name, (lunar ? "农历" : "公历"), dateText, next);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 写入/覆盖某人的生日，返回落库后的活动。
     * <p>同一个人只会有一条生日（按昵称大小写不敏感地找），重复登记 = 覆盖而不是新增；
     * 覆盖时保留原有的图标与描述（可能有人手工配过头像或备注，不该因为改个日期就被清掉），
     * 但**昵称按本次执行者的大小写刷新**——投递要用它查档案，用玩家当前的写法最稳。
     * <p>调用方负责广播（{@code ModNetwork.broadcastCalendar}）——本方法只管数据。
     */
    static CalendarEvent registerBirthday(CalendarData data, String name, int month, int day,
                                          boolean lunar) {
        CalendarEvent existing = data.findByName(CalendarEvent.TYPE_BIRTHDAY, name);
        if (existing == null) {
            CalendarEvent created = CalendarEvent.create(java.util.UUID.randomUUID(), name,
                    CalendarEvent.TYPE_BIRTHDAY, month, day, CalendarEvent.ICON_PLAYER, name,
                    "", "", lunar);
            data.add(created);
            return created;
        }
        CalendarEvent updated = existing.with(name, null, month, day, null, null, null, null, lunar);
        data.update(existing.id, updated);
        return updated;
    }

    /**
     * 月-日合法性：公历按月长（2 月按闰年允许 29）、农历按大月 30 天。
     * <p>参数区间已由 Brigadier 挡掉一部分（月 1-12、日 1-30），这里补上「2 月 30 日」这类
     * 区间内但不存在的组合。返回错误文案，合法返回 {@code null}。
     */
    @Nullable
    private static String validate(boolean lunar, int month, int day) {
        if (month < 1 || month > 12) {
            return "月份要在 1-12 之间";
        }
        if (day < 1) {
            return "日期要在 1 以上";
        }
        if (lunar) {
            return day <= LUNAR_MAX_DAY ? null : "农历一个月最多 30 天";
        }
        int max = YearMonth.of(2000, month).lengthOfMonth(); // 2000 是闰年 → 2 月按 29 天
        return day <= max ? null : month + " 月只有 " + max + " 天";
    }
}
