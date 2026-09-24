package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.time.LocalDate;
import java.util.List;

/**
 * 往来（Contact）邮件转接：把「给某个玩家寄一封明信片/包裹/红包」收敛成三个入口，
 * 调用方只给「收件人昵称 + 投递日期 + 内容」。
 *
 * <p>邮件本体（Contact 那套物品 NBT）由 {@link ContactMailBridge} 按 Contact 自己的写法拼装；
 * 投递日期落在 {@link ScheduledMailData}（主世界 SavedData），由 {@link ContactMailScheduler}
 * 每 20 tick 轮询一次到期任务，再交给 Contact 的挂号队列（{@code MailToBeSent}）——
 * 与 mod 自己的 {@code /contact postcard deliver} 走同一条收件链路，因此
 * 「收件人邮箱满时等待、投递到未上线的玩家、上线时提示有新邮件、开邮箱取件」
 * 全部由 Contact 原生处理，本类只负责拼装与排期。
 *
 * <p><b>收件人用昵称而非 UUID</b>：昵称是人给的（日历生日、活动名单都是名字），
 * 而 UUID 要调用方自己去查；更关键的是「现在还没进过服的玩家」此刻根本查不到 UUID。
 * 所以排期只记昵称，<b>到投递那一刻才解析成 UUID</b>（见 {@link ContactMailScheduler}），
 * 玩家中途才首次进服也照样收得到。解析顺序：在线玩家 → 服务器 usercache（{@code GameProfileCache}，
 * 覆盖所有进过服的玩家，含离线模式）。
 *
 * <p><b>日期语义</b>（现实时间 {@link LocalDate}，与日历模块同一套「服务器本地日期」）：
 * <ul>
 *   <li>date 在将来 → 该日的第一分钟（本地时间 00:00 之后的第一次轮询）投递；</li>
 *   <li>date 是今天或已经过去 → 立即投递（当天第一分钟早已过去，不再等待）。</li>
 * </ul>
 *
 * <p><b>约定与限制</b>：
 * <ul>
 *   <li>寄件人固定为 {@link #SYSTEM_SENDER}（Contact 的 {@code Sender} 标签，物品提示显示「寄件人：服务器」）；</li>
 *   <li>包裹 ≤ {@link #PARCEL_CAPACITY} 件、红包 ≤ {@link #RED_PACKET_CAPACITY} 件——
 *       与 Contact 1.2.3 的 {@code ParcelItem#getCapacity()} / {@code RedPacketItem#getCapacity()} 一致，
 *       超出的部分会落在 {@code parcel} 标签之外，玩家开包即丢，故这里直接拒绝；</li>
 *   <li>明信片款式取 {@code data/<命名空间>/postcards/*.json} 的 id（如 {@code contact:new_year_2023}），
 *       未知款式直接拒绝：Contact 对未知 {@code CardID} 会静默回落成默认款式，发出去比发不出去更糟；</li>
 *   <li>昵称只做格式校验（{@link ScheduledMailData#isValidPlayerName}），能否解析留到投递时：拼错的名字会一直留在排期里
 *       并只告警一次——这样「玩家还没进过服」与「名字写错」不会互相误伤；</li>
 *   <li>Contact 未安装时三个入口都返回 {@code false}（只告警一次）：本 mod 对 Contact 是编译期硬依赖、
 *       运行期可选（mods.toml 里 {@code mandatory=false}），故所有 Contact 类引用都关在 {@link ContactMailBridge} 里，
 *       未安装时该类不会被加载；</li>
 *   <li>排期存主世界 SavedData（{@code data/tothesky_contact_mail.dat}）：正常停服与自动保存后重启不丢，
 *       强杀最多丢掉「最后一次自动保存之后新排的期」；</li>
 *   <li><b>必须在服务端线程调用</b>：排期列表不是线程安全的。HTTP/REST 之类的异线程入口
 *       （见 {@code calendar.http.CalendarApiHandler} 的做法）需先 {@code server.execute(...)} 提交回主线程。</li>
 * </ul>
 *
 * <p>调用示例（把 2027 年元旦的第一分钟投递一张明信片给某玩家）：
 * <pre>{@code
 * ContactMail.sendPostcard("Notch", LocalDate.of(2027, 1, 1),
 *         new ResourceLocation("contact", "new_year_2023"), "新年快乐");
 * ContactMail.sendParcel("Notch", LocalDate.now(), List.of(new ItemStack(Items.CAKE, 3)));
 * ContactMail.sendRedPacket("Notch", LocalDate.now(), List.of(new ItemStack(Items.DIAMOND, 8)), "恭喜发财");
 * }</pre>
 */
public final class ContactMail {
    /** 系统邮件寄件人：写进 Contact 物品的 {@code Sender} 标签 */
    public static final String SYSTEM_SENDER = "服务器";
    /** 包裹容量上限，对齐 {@code ParcelItem#getCapacity()}（Contact 1.2.3） */
    public static final int PARCEL_CAPACITY = 4;
    /** 红包容量上限，对齐 {@code RedPacketItem#getCapacity()}（Contact 1.2.3） */
    public static final int RED_PACKET_CAPACITY = 1;

    /** Contact 是否在场（类初始化时求值：本门面只在游戏内被调用，那时 ModList 已就绪） */
    private static final boolean CONTACT_LOADED = ModList.get().isLoaded("contact");
    /** 「Contact 缺失」只告警一次，避免调用方循环里刷屏 */
    private static boolean missingWarned;

    private ContactMail() {
    }

    /**
     * 给玩家寄一张明信片。
     *
     * @param target 收件人昵称
     * @param date   投递日期（现实日期；今天或更早 = 立即投递）
     * @param style  明信片款式 id，取自 {@code data/<命名空间>/postcards/*.json}，未知款式会被拒绝
     * @param text   明信片正文（{@code null} 视为空；可含换行 {@code \n}，客户端按换行分列显示——
     *               NBT 里始终是单个字符串，投递格式没变）
     * @return 是否已排期
     */
    public static boolean sendPostcard(String target, LocalDate date, ResourceLocation style, String text) {
        if (!ScheduledMailData.isValidPlayerName(target) || date == null || style == null) {
            ToTheSky.LOGGER.warn("[往来] 明信片参数无效：收件人={} 日期={} 款式={}", target, date, style);
            return false;
        }
        if (!CONTACT_LOADED) {
            return contactMissing();
        }
        if (!ContactMailBridge.hasPostcard(style)) {
            ToTheSky.LOGGER.warn("[往来] 未知明信片款式 {}，未排期（款式取自 data/<命名空间>/postcards/*.json）", style);
            return false;
        }
        return schedule(target, date, ContactMailBridge.postcard(style, text == null ? "" : text));
    }

    /**
     * 给玩家寄一个包裹。
     *
     * @param target   收件人昵称
     * @param date     投递日期（现实日期；今天或更早 = 立即投递）
     * @param contents 内容物（≤ {@link #PARCEL_CAPACITY} 件，不可含空气）
     * @return 是否已排期
     */
    public static boolean sendParcel(String target, LocalDate date, List<ItemStack> contents) {
        if (!ScheduledMailData.isValidPlayerName(target)) {
            ToTheSky.LOGGER.warn("[往来] 包裹收件人昵称无效：{}", target);
            return false;
        }
        if (!CONTACT_LOADED) {
            return contactMissing();
        }
        if (!validContents(contents, PARCEL_CAPACITY, "包裹")) {
            return false;
        }
        return schedule(target, date, ContactMailBridge.parcel(contents));
    }

    /**
     * 给玩家寄一个红包。
     *
     * @param target   收件人昵称
     * @param date     投递日期（现实日期；今天或更早 = 立即投递）
     * @param contents 内容物（≤ {@link #RED_PACKET_CAPACITY} 件，不可含空气；可以为空 = 只有祝福语的红包）
     * @param blessing 祝福语（{@code null} 或空白 = 不写祝福语标签，与 mod 打包红包的行为一致）
     * @return 是否已排期
     */
    public static boolean sendRedPacket(String target, LocalDate date, List<ItemStack> contents, String blessing) {
        if (!ScheduledMailData.isValidPlayerName(target)) {
            ToTheSky.LOGGER.warn("[往来] 红包收件人昵称无效：{}", target);
            return false;
        }
        if (!CONTACT_LOADED) {
            return contactMissing();
        }
        if (!validContents(contents, RED_PACKET_CAPACITY, "红包")) {
            return false;
        }
        return schedule(target, date, ContactMailBridge.redPacket(contents, blessing));
    }

    /**
     * Contact 是否在场：{@link ContactMailScheduler} 用它决定要不要消费已排期的任务
     * （不在场时保留排期，装上后照常投递）；网页管理端也用它提示「现在投不出去」。
     */
    public static boolean loaded() {
        return CONTACT_LOADED;
    }

    /**
     * 明信片款式是否可用（网页保存信件时校验，避免存进一份「永远投不出去」的配置）。
     * <p>Contact 未安装、或款式表尚未装载时一律返回 {@code true}：判定留给投递那一刻，
     * 与 {@link ContactMailBridge#hasPostcard} 同一个态度——不把未知当非法。
     */
    public static boolean styleAvailable(ResourceLocation style) {
        return !CONTACT_LOADED || ContactMailBridge.hasPostcard(style);
    }

    /** 当前可用的明信片款式 id（网页编辑下拉用）；Contact 未安装时为空列表 */
    public static List<String> postcardStyles() {
        return CONTACT_LOADED ? ContactMailBridge.postcardStyles() : List.of();
    }

    /** 排期：邮件本体已经拼好，这里只负责落 SavedData */
    private static boolean schedule(String target, LocalDate date, ItemStack mail) {
        if (date == null || mail.isEmpty()) {
            ToTheSky.LOGGER.warn("[往来] 邮件无效，未排期：收件人={} 日期={} 物品={}", target, date, mail);
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            ToTheSky.LOGGER.warn("[往来] 服务器未运行，未排期定时邮件");
            return false;
        }
        ScheduledMailData.get(server).add(target, date.toEpochDay(), mail);
        ToTheSky.LOGGER.info("[往来] 已排期定时邮件：{} → {}（{}，物品 {}）",
                SYSTEM_SENDER, target, date, mail.getHoverName().getString());
        return true;
    }

    private static boolean validContents(List<ItemStack> contents, int capacity, String kind) {
        if (contents == null || contents.size() > capacity) {
            ToTheSky.LOGGER.warn("[往来] {}内容物无效：{} 件（上限 {} 件）",
                    kind, contents == null ? "null" : contents.size(), capacity);
            return false;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.isEmpty()) {
                ToTheSky.LOGGER.warn("[往来] {}内容物含空物品，未排期", kind);
                return false;
            }
        }
        return true;
    }

    private static boolean contactMissing() {
        if (!missingWarned) {
            missingWarned = true;
            ToTheSky.LOGGER.warn("[往来] 未安装 Contact，邮件转接不可用（已排期的任务会保留到 Contact 回归）");
        }
        return false;
    }
}
