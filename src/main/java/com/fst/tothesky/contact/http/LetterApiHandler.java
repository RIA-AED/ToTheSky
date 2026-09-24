package com.fst.tothesky.contact.http;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.contact.ContactMail;
import com.fst.tothesky.contact.FestivalLetter;
import com.fst.tothesky.contact.LetterLibrary;
import com.fst.tothesky.contact.LetterScheduler;
import com.fst.tothesky.web.WebHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

/**
 * 信件 REST API：网页管理端读写 {@code config/tothesky/letters}（由 {@code web.WebApiServer}
 * 挂在 {@code /api/letters/}）。
 *
 * <p>路由：
 * <ul>
 *   <li>{@code GET    /} — 全部信件 + 编辑器元数据（可选类型、容量上限、明信片款式、Contact 在场与否、生日信 id）</li>
 *   <li>{@code GET    /{id}} — 单封（404）</li>
 *   <li>{@code PUT    /{id}} — 创建或整体替换（body = 信件 JSON 本体；未知字段原样写回）</li>
 *   <li>{@code DELETE /{id}} — 删除</li>
 * </ul>
 *
 * <p>信件对象 = 文件原文 + 解析结论 + 摘要字段：
 * <ul>
 *   <li>{@code raw}（对象，解析不过去时为 null）/ {@code rawText}（原文，任何时候都有）——
 *       <b>编辑器以原文为准</b>，这样 {@code _comment} 之类手写的字段不会被网页抹掉；</li>
 *   <li>{@code valid} / {@code enabled} / {@code birthday}（是否那封固定的生日信）/
 *       {@code error}（不合法时的原因）/ {@code notes}（字段被忽略之类的提醒）；</li>
 *   <li>{@code type} / {@code style} / {@code items} / {@code text}——解析通过时才有值，供列表显示
 *       （{@code text} 是**逐行数组**，编辑器按行回填文本框）。</li>
 * </ul>
 * 信件不描述「谁、什么时候收」，所以这里也没有 trigger/收件人/日期——触发一律在日历侧
 * （节日绑定或生日调用，见 {@code contact.LetterScheduler}）。
 *
 * <p>写操作全部经 {@link WebHttp#submitOnServer} 回服务器线程：{@link LetterLibrary} 的内容缓存、
 * {@link LetterScheduler} 的信件定义都归主线程独占。保存成功后只重读定义
 * （{@link LetterScheduler#reloadDefinitions()}）——<b>不重发今天已投过的信</b>；
 * 要连今天的信一起重发，在游戏里跑 {@code /tothesky reloadletters}。
 */
public final class LetterApiHandler {

    /** PUT 的结果：{@code error} 非空 = 400；{@code created} 决定回 201 还是 200 */
    private record Saved(@Nullable LetterLibrary.Entry entry, @Nullable String error, boolean created) {
    }

    private final MinecraftServer server;

    public LetterApiHandler(MinecraftServer server) {
        this.server = server;
    }

    // ---- 路由 ----

    public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            // createContext 挂在 /api/letters/，但 context path 裁切不可依赖（尾部斜杠行为不一致）——手动剥前缀
            String prefix = "/api/letters";
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
            }
            if ("OPTIONS".equals(method)) {
                WebHttp.respondOptions(exchange);
                return;
            }
            String id = path.startsWith("/") ? path.substring(1) : path;
            if (id.isEmpty()) {
                if ("GET".equals(method)) {
                    handleList(exchange);
                } else {
                    WebHttp.respond(exchange, 405, WebHttp.errorJson("GET /（列表）或 GET/PUT/DELETE /{id}"));
                }
                return;
            }
            switch (method) {
                case "GET" -> handleGet(exchange, id);
                case "PUT" -> handlePut(exchange, id);
                case "DELETE" -> handleDelete(exchange, id);
                default -> WebHttp.respond(exchange, 405, WebHttp.errorJson("unsupported method " + method));
            }
        } catch (Exception e) {
            ToTheSky.LOGGER.error("[信件API] 处理请求失败", e);
            try {
                WebHttp.respond(exchange, 500, WebHttp.errorJson("internal error"));
            } catch (IOException ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    // ---- 各路由实现 ----

    private void handleList(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String json = WebHttp.submitOnServer(server, () -> {
            JsonObject root = new JsonObject();
            JsonArray letters = new JsonArray();
            for (LetterLibrary.Entry entry : LetterLibrary.list()) {
                letters.add(letterObject(entry));
            }
            root.add("letters", letters);
            JsonArray types = new JsonArray();
            for (FestivalLetter.Type type : FestivalLetter.Type.values()) {
                types.add(type.id());
            }
            root.add("types", types);
            root.addProperty("parcelCapacity", ContactMail.PARCEL_CAPACITY);
            root.addProperty("redPacketCapacity", ContactMail.RED_PACKET_CAPACITY);
            JsonArray styles = new JsonArray();
            for (String style : ContactMail.postcardStyles()) {
                styles.add(style);
            }
            root.add("styles", styles);
            root.addProperty("contact", ContactMail.loaded());
            // 生日信的文件名固定，页面要把它标出来（其余信件只能被节日绑定）
            root.addProperty("birthdayId", LetterLibrary.BIRTHDAY_ID);
            return WebHttp.toJson(root);
        });
        respond(exchange, json, 200);
    }

    private void handleGet(com.sun.net.httpserver.HttpExchange exchange, String id) throws IOException {
        // 用 Optional 区分「超时」与「没有这封信」（submitOnServer 超时也返回 null）
        Optional<String> json = WebHttp.submitOnServer(server, () ->
                Optional.ofNullable(LetterLibrary.find(id))
                        .map(LetterApiHandler::letterObject)
                        .map(WebHttp::toJson));
        if (json == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        if (json.isEmpty()) {
            WebHttp.respond(exchange, 404, WebHttp.errorJson("letter not found: " + id));
            return;
        }
        WebHttp.respond(exchange, 200, json.get());
    }

    private void handlePut(com.sun.net.httpserver.HttpExchange exchange, String id) throws IOException {
        JsonObject body = WebHttp.readJsonBody(exchange);
        if (body == null) {
            return; // readJsonBody 已响应错误
        }
        Saved saved = WebHttp.submitOnServer(server, () -> {
            boolean existed = LetterLibrary.find(id) != null;
            String error = LetterLibrary.save(id, body);
            if (error != null) {
                return new Saved(null, error, false);
            }
            // 保存即生效：只重读定义，不重发今天已投过的信（见类注释）
            LetterScheduler.reloadDefinitions();
            return new Saved(LetterLibrary.find(id), null, !existed);
        });
        if (saved == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        if (saved.error() != null) {
            WebHttp.respond(exchange, 400, WebHttp.errorJson(saved.error()));
            return;
        }
        ToTheSky.LOGGER.info("[信件API] 网页{}信件 {}（{}）", saved.created() ? "新建" : "更新", id,
                saved.entry() == null || saved.entry().letter() == null
                        ? "未启用" : saved.entry().letter().type().id());
        respond(exchange, WebHttp.toJson(letterObject(saved.entry())), saved.created() ? 201 : 200);
    }

    private void handleDelete(com.sun.net.httpserver.HttpExchange exchange, String id) throws IOException {
        Boolean removed = WebHttp.submitOnServer(server, () -> {
            boolean ok = LetterLibrary.delete(id);
            if (ok) {
                LetterScheduler.reloadDefinitions();
            }
            return ok;
        });
        if (removed == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        if (removed) {
            ToTheSky.LOGGER.info("[信件API] 网页删除信件 {}", id);
        }
        WebHttp.respond(exchange, removed ? 200 : 404,
                statusJson(removed ? "deleted" : "letter not found: " + id));
    }

    // ---- 序列化 ----

    private static JsonObject letterObject(LetterLibrary.Entry entry) {
        JsonObject obj = new JsonObject();
        JsonObject raw = parseRaw(entry.raw());
        FestivalLetter letter = entry.letter();
        obj.addProperty("id", entry.id());
        obj.addProperty("valid", entry.valid());
        obj.addProperty("enabled", entry.enabled());
        obj.addProperty("birthday", LetterLibrary.BIRTHDAY_ID.equals(entry.id()));
        if (entry.error() != null) {
            obj.addProperty("error", entry.error());
        }
        JsonArray notes = new JsonArray();
        for (String note : entry.notes()) {
            notes.add(note);
        }
        obj.add("notes", notes);
        obj.add("raw", raw);
        obj.addProperty("rawText", entry.raw());

        obj.addProperty("type", letter == null ? null : letter.type().id());
        obj.addProperty("style", letter == null || letter.style() == null ? null : letter.style().toString());
        obj.add("text", letterText(letter));
        JsonArray items = new JsonArray();
        if (letter != null) {
            for (ItemStack stack : letter.items()) {
                JsonObject item = new JsonObject();
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                item.addProperty("item", key == null ? "" : key.toString());
                item.addProperty("count", stack.getCount());
                items.add(item);
            }
        }
        obj.add("items", items);
        return obj;
    }

    /** 信件正文各行；解析不过去（{@code letter} 为 null）时给 JSON null，没有正文时给空数组 */
    private static JsonElement letterText(@Nullable FestivalLetter letter) {
        if (letter == null) {
            return JsonNull.INSTANCE;
        }
        JsonArray lines = new JsonArray();
        for (String line : letter.text()) {
            lines.add(line);
        }
        return lines;
    }

    /** 文件原文可解析为 JSON 对象时返回它，否则 null（编辑器据此改用 {@code rawText} 兜底） */
    @Nullable
    private static JsonObject parseRaw(String raw) {
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String statusJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", message);
        return WebHttp.toJson(obj);
    }

    /** 统一的「服务器线程超时 → 503」出口 */
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, @Nullable String json, int okStatus)
            throws IOException {
        if (json == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        WebHttp.respond(exchange, okStatus, json);
    }
}
