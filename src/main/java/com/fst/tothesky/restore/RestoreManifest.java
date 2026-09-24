package com.fst.tothesky.restore;

import com.fst.tothesky.ToTheSky;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 存档扫描器输出的补偿清单（{@code restore.json}，formatVersion 1）。
 *
 * <p>由 {@code tools/save-scanner/scan_save.py} 生成，本类只读不写。字段含义见该工具的 README，
 * 这里只做最小限度的解析与校验——**不认识的 formatVersion 直接拒绝**，避免用错误的字段语义
 * 去动玩家的存档。
 *
 * <p>关键约定：
 * <ul>
 *   <li>{@code status=missing} 才是要补偿的条目（{@code remapTo} 给出同名替代品 id）；
 *       {@code already_remapped} 必须跳过——它代表物品已换名存活，再补就是凭空造物；</li>
 *   <li>{@code item.snbt} 里的 id 仍是**旧** id（{@code kubejs:*}），补偿时必须改写成
 *       {@code remapTo} 再构造物品，否则 {@code ItemStack.of} 对未注册 id 会返回空；</li>
 *   <li>{@code chain} 从最外层容器到该物品；空表示 {@code origin} 本身就是容器。</li>
 * </ul>
 */
public final class RestoreManifest {
    /** 本类支持的清单格式版本 */
    public static final int SUPPORTED_FORMAT = 1;
    /** 清单文件名（放在 {@code config/tothesky/} 下） */
    public static final String FILE_NAME = "restore.json";

    /** 容器的定位方式 */
    public enum HolderKind {
        /** 玩家（物品栏/末影箱/饰品栏）——走往来包裹投递 */
        PLAYER,
        /** 世界中的方块容器——按坐标找方块实体后插入 */
        BLOCK,
        /** 实体（箱船/箱矿车/展示框/驴背包）——按 UUID 找实体后插入 */
        ENTITY,
        /** 兜底：清单未给出可定位的持有者 */
        UNKNOWN
    }

    /** 一个可定位的持有者 */
    public record Holder(HolderKind kind, String dimension, @Nullable List<Integer> pos,
                         @Nullable String blockEntity, @Nullable String playerName,
                         @Nullable String playerUuid, @Nullable String entityUuid) {
    }

    /**
     * 一条待补偿记录。
     *
     * @param index     在清单中的序号（0 起，用于日志对号）
     * @param status    {@code missing} / {@code no_target} / {@code already_remapped} / …
     * @param remapTo   目标物品 id（{@code missing} 时非空）
     * @param type      origin.type（{@code block_entity} / {@code player_inventory} / {@code ae2_cell} …）
     * @param chain     外层容器链（最外层在前）
     */
    public record Entry(int index, String status, @Nullable String remapTo, String type,
                        String dimension, @Nullable List<Integer> pos, @Nullable String blockEntity,
                        @Nullable String playerName, @Nullable String playerUuid, @Nullable String label,
                        List<Holder> chain, String path, @Nullable Integer slot,
                        String itemId, @Nullable Integer itemCount, @Nullable String snbt,
                        @Nullable Integer ae2Amount) {

        /** 是否需要补偿 */
        public boolean isRestorable() {
            // AE2 条目没有 ItemStack 形态的 SNBT（原件键不是物品栈），单独放行
            if ("ae2_cell".equals(type)) {
                return "missing".equals(status) && remapTo != null;
            }
            return "missing".equals(status) && remapTo != null && snbt != null;
        }

        /** 自身（不含外层容器）的持有者 */
        public Holder selfHolder() {
            // 实体类 origin 的 UUID 由扫描器放在 label 里
            return holderOf(type, dimension, pos, blockEntity, playerName, playerUuid, label);
        }

        /** 最外层持有者：有 chain 取 chain[0]，否则就是自身。 */
        public Holder outerHolder() {
            return chain.isEmpty() ? selfHolder() : chain.get(0);
        }

        /** 补偿数量：AE2 条目的数量在 ae2.amount 里，不在 Count */
        public int amount() {
            if (ae2Amount != null) {
                return ae2Amount;
            }
            return itemCount == null ? 1 : itemCount;
        }
    }

    private final int formatVersion;
    private final String generatedAt;
    private final String sourceSave;
    private final List<Entry> entries;

    private RestoreManifest(int formatVersion, String generatedAt, String sourceSave, List<Entry> entries) {
        this.formatVersion = formatVersion;
        this.generatedAt = generatedAt;
        this.sourceSave = sourceSave;
        this.entries = List.copyOf(entries);
    }

    public int formatVersion() {
        return formatVersion;
    }

    public String generatedAt() {
        return generatedAt;
    }

    public String sourceSave() {
        return sourceSave;
    }

    /** 全部条目（含无需补偿的） */
    public List<Entry> entries() {
        return entries;
    }

    /** 需要补偿的条目 */
    public List<Entry> restorable() {
        return entries.stream().filter(Entry::isRestorable).toList();
    }

    /**
     * 清单默认位置：{@code config/tothesky/restore.json}。
     *
     * <p>与 {@code config/tothesky/letters} 同一层级——运维把扫描器产出的 restore.json
     * 拷到这里，执行 {@code /tothesky restore} 即可，无需带路径参数。
     */
    public static Path defaultFile() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(ToTheSky.MODID)
                .resolve(FILE_NAME);
    }

    /**
     * 读取并解析清单。
     *
     * @throws IOException        文件读不到
     * @throws IllegalArgumentException 结构不合法或 formatVersion 不受支持
     */
    public static RestoreManifest load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("根节点不是 JSON 对象");
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("不是合法的 JSON：" + e.getMessage(), e);
        }

        int version = optInt(root, "formatVersion", -1);
        if (version != SUPPORTED_FORMAT) {
            throw new IllegalArgumentException(
                    "清单 formatVersion=" + version + "，本模组只支持 " + SUPPORTED_FORMAT
                            + "（请用配套版本的 tools/save-scanner 重新生成）");
        }

        String generatedAt = optString(root, "generatedAt", "?");
        String sourceSave = "?";
        JsonObject source = optObject(root, "source");
        if (source != null) {
            sourceSave = optString(source, "save", "?");
        }

        List<Entry> entries = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("entries");
        if (array == null) {
            throw new IllegalArgumentException("清单缺少 entries 数组");
        }
        int i = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                i++;
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            entries.add(parseEntry(entry, i++));
        }
        return new RestoreManifest(version, generatedAt, sourceSave, entries);
    }

    private static Entry parseEntry(JsonObject entry, int index) {
        JsonObject origin = optObject(entry, "origin");
        String type = origin == null ? "?" : optString(origin, "type", "?");
        String dimension = origin == null ? "minecraft:overworld"
                : optString(origin, "dimension", "minecraft:overworld");

        List<Integer> pos = readIntList(origin == null ? null : origin.get("pos"));
        String blockEntity = origin == null ? null : optString(origin, "blockEntity", null);
        String label = origin == null ? null : optString(origin, "label", null);
        String playerName = null;
        String playerUuid = null;
        JsonObject player = origin == null ? null : optObject(origin, "player");
        if (player != null) {
            playerName = optString(player, "name", null);
            playerUuid = optString(player, "uuid", null);
        }

        List<Holder> chainHolders = new ArrayList<>();
        JsonArray chainArray = entry.getAsJsonArray("chain");
        if (chainArray != null) {
            for (JsonElement chainElement : chainArray) {
                if (!chainElement.isJsonObject()) {
                    continue;
                }
                JsonObject link = chainElement.getAsJsonObject();
                JsonObject chainPlayer = optObject(link, "player");
                chainHolders.add(holderOf(
                        optString(link, "type", "?"),
                        optString(link, "dimension", dimension),
                        readIntList(link.get("pos")),
                        optString(link, "blockEntity", null),
                        chainPlayer == null ? null : optString(chainPlayer, "name", null),
                        chainPlayer == null ? null : optString(chainPlayer, "uuid", null),
                        null));
            }
        }

        JsonObject item = optObject(entry, "item");
        String itemId = item == null ? "?" : optString(item, "id", "?");
        Integer itemCount = item == null ? null : optInt(item, "count", null);
        String snbt = item == null ? null : optString(item, "snbt", null);
        Integer ae2Amount = null;
        JsonObject ae2 = item == null ? null : optObject(item, "ae2");
        if (ae2 != null) {
            ae2Amount = optInt(ae2, "amount", null);
        }

        return new Entry(index,
                optString(entry, "status", "unknown"),
                optString(entry, "remapTo", null),
                type, dimension, pos, blockEntity, playerName, playerUuid, label,
                chainHolders,
                optString(entry, "path", "?"),
                optInt(entry, "slot", null),
                itemId, itemCount, snbt, ae2Amount);
    }

    /**
     * 由 origin / chain 链接的字段推出持有者类型。
     * <p>判定顺序即优先级：玩家 → 方块坐标 → 实体 UUID → 未知。
     * 之所以先看「有没有玩家」，是因为同一个 {@code backpack_item} 在玩家背包里与放在地上时，
     * origin 里的 pos/player 会有一个为空，靠它区分「寄给玩家」还是「塞回方块」。
     */
    static Holder holderOf(String type, String dimension, @Nullable List<Integer> pos,
                           @Nullable String blockEntity, @Nullable String playerName,
                           @Nullable String playerUuid, @Nullable String entityUuid) {
        HolderKind kind;
        if (playerUuid != null || "player_inventory".equals(type)
                || "player_ender".equals(type) || "curios".equals(type)) {
            kind = HolderKind.PLAYER;
        } else if (pos != null && ("block_entity".equals(type) || "placed_item".equals(type)
                || "backpack_block".equals(type) || "ae2_drive".equals(type))) {
            kind = HolderKind.BLOCK;
        } else if ("entity_container".equals(type) || "entity_item".equals(type)) {
            kind = HolderKind.ENTITY;
        } else {
            kind = HolderKind.UNKNOWN;
        }
        return new Holder(kind, dimension, pos, blockEntity, playerName, playerUuid, entityUuid);
    }

    // ---- JSON 取值小工具（全部容错：清单是外部文件，字段缺失只降级不抛异常） ----

    @Nullable
    private static JsonObject optObject(@Nullable JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static String optString(@Nullable JsonObject parent, String key, @Nullable String fallback) {
        if (parent == null) {
            return fallback;
        }
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @Nullable
    private static Integer optInt(@Nullable JsonObject parent, String key, @Nullable Integer fallback) {
        if (parent == null) {
            return fallback;
        }
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * 读 [x, y, z]。
     *
     * <p><b>必须向下取整，不能用 {@code getAsInt()}</b>：实体坐标是双精度（如 {@code -0.5}），
     * 截断会得到 {@code 0}，于是区块算出 {@code 0>>4 = 0}，而正确结果是 {@code -1>>4 = -1}——
     * 在负坐标附近会加载到错误的区块，方块实体自然找不到。方块坐标本就是整数，取整是恒等变换。
     */
    @Nullable
    private static List<Integer> readIntList(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return null;
        }
        List<Integer> out = new ArrayList<>(3);
        for (JsonElement value : array) {
            try {
                out.add((int) Math.floor(value.getAsDouble()));
            } catch (RuntimeException e) {
                ToTheSky.LOGGER.warn("[补偿] 坐标里有非数字项，按缺失处理");
                return null;
            }
        }
        return out;
    }
}
