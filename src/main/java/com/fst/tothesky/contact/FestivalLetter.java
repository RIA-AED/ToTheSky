package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 一封信件的内容定义——{@code config/tothesky/letters/*.json} 解析后的不可变结果
 * （解析见 {@link LetterLibrary}）。字段全部经过校验，拿到实例即可直接投递。
 *
 * <p><b>信件只描述「送什么」，不描述「送给谁、什么时候送」</b>——触发一律来自日历：
 * <ul>
 *   <li><b>节日</b>：节日活动的 {@code letter} 字段指向某封信（见 {@code calendar.CalendarEvent}），
 *       节日当天发给**全服每位玩家**；同一封信可被多个节日绑定，各按自己的日子发；</li>
 *   <li><b>生日</b>：日历里 {@code type=birthday} 的活动当天，收件人（活动名 = 玩家昵称）收到
 *       {@code birthday.json}（文件名固定，见 {@link LetterLibrary#BIRTHDAY_ID}）——一封生日信服务全服。</li>
 * </ul>
 * 因此文件里没有 {@code trigger} / {@code player} / {@code date} 这类字段；
 * 写了也只会被忽略并提醒一次（旧版本的遗留写法）。没人调用的信就是一叠摆设，不会被投递。
 *
 * <p><b>JSON 格式</b>（一个文件 = 一封信；未知字段忽略，手写的 {@code _comment} 会原样保留）：
 * <pre>{@code
 * {
 *   "enabled": true,                                        // 可选，默认 true；false = 不投递
 *   "type": "postcard",                                     // 必填：postcard / parcel / red_packet
 *   "style": "contact:new_year_2023",                       // type=postcard 必填：明信片款式
 *   "items": [{"item": "minecraft:cake", "count": 3}],      // type=parcel/red_packet 必填，count 默认 1
 *   "text": ["祝${player}生日快乐！", "今天是${date}。"]         // postcard/red_packet 可选：正文/祝福语，每项一行
 * }
 * }</pre>
 *
 * <p>字段名与三类邮件一一对应：明信片 = 样式 + 正文，包裹 = 内容物（Contact 的包裹没有正文），
 * 红包 = 内容物 + 祝福语。
 *
 * <p><b>{@code text} 是字符串数组，每项一行</b>——投递时按顺序用换行（{@code \n}）拼成最终正文。
 * 单个字符串仍然接受（旧写法，等价于只有一行的数组）。拼完的正文是**一个字符串**，原样交给
 * Contact 的 {@code PostcardItem.setText}（NBT 里始终是单个 {@code Text} 字符串标签），
 * 换行由客户端按原版换行规则渲染——所以投递格式没变，<b>老客户端不需要换模组</b>。
 *
 * <p><b>占位符</b>（只在 {@code text} 里生效，投递当天替换）：
 * <ul>
 *   <li>{@value #PLACEHOLDER_PLAYER} → 收件人昵称（生日信就是当天过生日的那个人），
 *       如 {@code 祝${player}生日快乐！} → {@code 祝Steve生日快乐！}；</li>
 *   <li>{@value #PLACEHOLDER_DATE} → 投递当天的日期，格式 {@code yyyy-MM-dd}；</li>
 *   <li>{@value #PLACEHOLDER_ITEM} → 内容物清单（{@code 物品显示名×数量}，多项用 {@code 、} 连接），
 *       <b>只在红包里有效</b>——包裹没有正文可写，明信片没有内容物，这两类里该占位符原样保留。
 *       名字就是物品的显示名 {@link ItemStack#getHoverName()}（自定义名也算），
 *       所以专用服务器上会是服务端语言的叫法（没有语言包时为英文）。</li>
 * </ul>
 *
 * <p>日期与投递时机一概由日历活动决定（逐年循环、农历换算见 {@code calendar.CalendarEvent}），
 * 这里只管内容；何时检查、如何投递见 {@link LetterScheduler}。
 */
public final class FestivalLetter {
    /** 收件人占位符 → 被送的玩家昵称 */
    public static final String PLACEHOLDER_PLAYER = "${player}";
    /** 日期占位符 → 投递当天的日期（{@code yyyy-MM-dd}） */
    public static final String PLACEHOLDER_DATE = "${date}";
    /** 内容物占位符 → 包含的物品（仅红包有效） */
    public static final String PLACEHOLDER_ITEM = "${item}";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 邮件类型（JSON 的 {@code type}） */
    public enum Type {
        POSTCARD("postcard"),
        PARCEL("parcel"),
        RED_PACKET("red_packet");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 未知类型返回 null（由调用方报错） */
        @Nullable
        static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 文件名去掉 {@code .json}；改文件名 = 换了一封信（排期状态从头算）。
     * <p>被节日绑定用的是这个 id（{@code calendar.CalendarEvent.letter}）。
     */
    private final String id;
    private final Type type;
    /** 仅 {@link Type#POSTCARD} */
    @Nullable
    private final ResourceLocation style;
    /** 仅 {@link Type#PARCEL} / {@link Type#RED_PACKET}，其它类型为空列表 */
    private final List<ItemStack> items;
    /** 仅 {@link Type#POSTCARD} / {@link Type#RED_PACKET}；红包里即祝福语。每项一行，其它类型为空列表 */
    private final List<String> text;

    private FestivalLetter(String id, Type type, @Nullable ResourceLocation style,
                           List<ItemStack> items, List<String> text) {
        this.id = id;
        this.type = type;
        this.style = style;
        this.items = items;
        this.text = text;
    }

    public String id() {
        return id;
    }

    public Type type() {
        return type;
    }

    /** 是否生日信（文件名固定为 {@code birthday.json}，见 {@link LetterLibrary#BIRTHDAY_ID}） */
    public boolean birthday() {
        return LetterLibrary.BIRTHDAY_ID.equals(id);
    }

    /** 仅明信片非 null */
    @Nullable
    public ResourceLocation style() {
        return style;
    }

    /** 内容物（包裹/红包；其它类型为空列表） */
    public List<ItemStack> items() {
        return items;
    }

    /** 正文 / 红包祝福语各行（没有正文时为空列表；行与行之间投递时用换行拼接） */
    public List<String> text() {
        return text;
    }

    /**
     * 渲染正文：每行替换 {@value #PLACEHOLDER_PLAYER}、{@value #PLACEHOLDER_DATE}，
     * {@code forItems} 非 null 时再替换 {@value #PLACEHOLDER_ITEM}（即只有红包传内容物）；
     * 最后按顺序用换行（{@code \n}）把各行拼成一整段（空列表 = 空串）。
     * <p>物品清单最后替换，物品名里万一有别的占位符也不会被二次替换。
     *
     * @param recipient 收件人昵称（节日绑定信是名单里的这一位，生日信是当天过生日的那位）
     */
    public String renderText(LocalDate today, String recipient, @Nullable List<ItemStack> forItems) {
        String renderedItems = forItems == null ? null : describeItems(forItems);
        String date = DATE_FORMAT.format(today);
        StringBuilder builder = new StringBuilder();
        for (String line : text) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            String rendered = line
                    .replace(PLACEHOLDER_PLAYER, recipient)
                    .replace(PLACEHOLDER_DATE, date);
            builder.append(renderedItems == null ? rendered : rendered.replace(PLACEHOLDER_ITEM, renderedItems));
        }
        return builder.toString();
    }

    /**
     * 内容物的可读清单：{@code 钻石×8、蛋糕×1}，名字取物品的显示名
     * （{@link ItemStack#getHoverName()}，含自定义名）。信里那句话由此在投递当天定死。
     */
    public static String describeItems(List<ItemStack> items) {
        StringBuilder builder = new StringBuilder();
        for (ItemStack stack : items) {
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(stack.getHoverName().getString()).append('×').append(stack.getCount());
        }
        return builder.toString();
    }

    // ==================== 解析 ====================

    /**
     * 解析结果（不落日志，供网页编辑回显）。
     * <ul>
     *   <li>{@code error} 非 null = 配置非法，此时 {@code letter} 必为 null；</li>
     *   <li>{@code error} 为 null 而 {@code letter} 非 null = 校验通过，内容可用；
     *       {@code enabled} 决定它是否会真的投递；</li>
     *   <li>三个字段都「空」（{@code letter} 为 null、{@code enabled} 为 false、无错）= 加载路径上被跳过的
     *       停用信——不校验也不投递，写坏了也没人收到。</li>
     * </ul>
     * {@code notes} 是「某个字段不生效」之类的提醒（如「包裹没有正文，text 已忽略」），不影响投递。
     */
    public record Outcome(@Nullable FestivalLetter letter, boolean enabled, @Nullable String error,
                          List<String> notes) {
    }

    /**
     * 按加载语义把 {@link Outcome} 打成日志（成功不打）。
     * <p>「同一个文件内容只报一次」由 {@code LetterLibrary} 的内容缓存保证。
     */
    public static void logOutcome(String id, Outcome outcome) {
        if (outcome.error() != null) {
            warn(id, outcome.error());
            return;
        }
        if (!outcome.enabled()) {
            ToTheSky.LOGGER.info("[节日信] {} 已禁用（enabled=false），跳过", id);
            return;
        }
        for (String message : outcome.notes()) {
            note(id, message);
        }
    }

    /**
     * 解析一个 JSON 对象（不落日志）。任何一处不合法都只把原因放进 {@link Outcome#error()}——
     * 宁可不投递，也不要把半成品信件发出去。
     *
     * @param validateDisabled {@code true} 时即使 {@code enabled=false} 也完整校验（网页保存用：
     *                         停用不等于可以存进一份「重新启用就投不出去」的配置）；
     *                         加载传 {@code false}——停用的信既不校验也不投递，写坏了没人会收到。
     */
    public static Outcome parseOutcome(String id, JsonObject json, boolean validateDisabled) {
        List<String> notes = new ArrayList<>();
        boolean enabled = !Boolean.FALSE.equals(bool(json, "enabled"));
        if (!enabled && !validateDisabled) {
            return new Outcome(null, false, null, notes);
        }
        String typeId = string(json, "type");
        Type type = typeId == null ? null : Type.byId(typeId);
        if (type == null) {
            return invalid("type 缺失或未知（可用 postcard / parcel / red_packet），实为 " + typeId);
        }
        // 旧版本的触发字段：信件现在只由日历调用，这些字段一律无效——提醒一次，免得对着不生效的配置猜
        for (String legacy : List.of("trigger", "player", "date")) {
            if (json.has(legacy)) {
                notes.add(legacy + " 已废弃（信件不再自己排期，改由节日绑定或生日调用），已忽略");
            }
        }
        TextResult parsedText = parseText(json);
        if (parsedText.error() != null) {
            return invalid(parsedText.error());
        }
        List<String> text = parsedText.lines();

        ResourceLocation style = null;
        List<ItemStack> items = List.of();
        switch (type) {
            case POSTCARD -> {
                String styleId = string(json, "style");
                // 空串会被 tryParse 解析成 `minecraft:`（一个永远投不出去的假款式），必须按「缺失」处理
                style = styleId == null || styleId.isBlank() ? null : ResourceLocation.tryParse(styleId);
                if (style == null) {
                    return invalid("明信片缺少 style（款式 id，如 contact:new_year_2023），实为 " + styleId);
                }
            }
            case PARCEL, RED_PACKET -> {
                int capacity = type == Type.PARCEL ? ContactMail.PARCEL_CAPACITY : ContactMail.RED_PACKET_CAPACITY;
                ItemsResult parsed = parseItems(json, capacity);
                if (parsed.error() != null) {
                    return invalid(parsed.error());
                }
                items = parsed.items();
                if (type == Type.PARCEL && !text.isEmpty()) {
                    notes.add("包裹没有正文，text 已忽略");
                }
            }
        }
        // 走到这里说明配置本身没问题（停用的信在 validateDisabled 时才到这）；enabled 只决定投不投
        return new Outcome(new FestivalLetter(id, type, style, items, text), enabled, null, notes);
    }

    private static Outcome invalid(String message) {
        return new Outcome(null, false, message, List.of());
    }

    /** {@link #parseItems} 的结果：内容物，或错因 */
    private record ItemsResult(List<ItemStack> items, @Nullable String error) {
    }

    /** {@link #parseText} 的结果：正文各行，或错因 */
    private record TextResult(List<String> lines, @Nullable String error) {
    }

    /**
     * 解析 {@code text}：<b>字符串数组，每项一行</b>；也接受单个字符串（旧写法，等价于只有一行的数组）。
     * <p>缺省或空数组 = 没有正文；空串项是合法的空行。行间换行在 {@link #renderText} 拼接。
     */
    private static TextResult parseText(JsonObject json) {
        JsonElement element = json.get("text");
        if (element == null || element.isJsonNull()) {
            return new TextResult(List.of(), null);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new TextResult(List.of(element.getAsString()), null);
        }
        if (!element.isJsonArray()) {
            return new TextResult(null,
                    "text 必须是字符串数组（每项一行，如 [\"祝${player}生日快乐！\", \"今天是${date}。\"]），实为 " + element);
        }
        JsonArray array = element.getAsJsonArray();
        List<String> lines = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement child = array.get(i);
            if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isString()) {
                return new TextResult(null, "text[" + i + "] 必须是字符串（每项一行）");
            }
            lines.add(child.getAsString());
        }
        return new TextResult(lines, null);
    }

    /** 解析 items；不合法返回错因 */
    private static ItemsResult parseItems(JsonObject json, int capacity) {
        JsonElement element = json.get("items");
        if (element == null) {
            return new ItemsResult(null, "缺少 items（内容物数组，元素形如 {\"item\": \"minecraft:cake\", \"count\": 3}）");
        }
        if (!element.isJsonArray()) {
            return new ItemsResult(null, "items 必须是数组");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > capacity) {
            return new ItemsResult(null, "items 有 " + array.size() + " 项，超过上限 " + capacity + " 件（Contact 的容量限制）");
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement child = array.get(i);
            if (!child.isJsonObject()) {
                return new ItemsResult(null, "items[" + i + "] 必须是 {\"item\": ..., \"count\": ...} 对象");
            }
            JsonObject entry = child.getAsJsonObject();
            String itemId = string(entry, "item");
            ResourceLocation key = itemId == null ? null : ResourceLocation.tryParse(itemId);
            // getValue 对未知 id 返回默认值（空气），判存在性必须用 containsKey——否则「拼错的物品 id」会被
            // 报成「不能是空气」，对着错因怎么改都改不对
            if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
                return new ItemsResult(null, "items[" + i + "] 未知物品 " + itemId);
            }
            Item item = ForgeRegistries.ITEMS.getValue(key);
            int count = 1;
            if (entry.has("count")) {
                Integer parsed = integer(entry, "count");
                if (parsed == null || parsed < 1 || parsed > 64) {
                    return new ItemsResult(null, "items[" + i + "] 的 count 必须是 1..64 的整数");
                }
                count = parsed;
            }
            ItemStack stack = new ItemStack(item, count);
            if (stack.isEmpty()) {
                return new ItemsResult(null, "items[" + i + "] 不能是空气");
            }
            items.add(stack);
        }
        return new ItemsResult(items, null);
    }

    @Nullable
    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    @Nullable
    private static Boolean bool(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : null;
    }

    @Nullable
    private static Integer integer(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : null;
    }

    /** 解析失败：整封信跳过 */
    private static void warn(String id, String message) {
        ToTheSky.LOGGER.warn("[节日信] {} 已跳过：{}", id, message);
    }

    /** 可继续投递的配置问题：某个字段不生效（信照发，只是那个字段被忽略） */
    private static void note(String id, String message) {
        ToTheSky.LOGGER.warn("[节日信] {}：{}", id, message);
    }
}
