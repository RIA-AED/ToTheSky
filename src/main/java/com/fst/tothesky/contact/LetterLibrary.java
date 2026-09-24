package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 信件库：读写 {@code config/tothesky/letters}。
 *
 * <p>一个 {@code *.json} = 一封信（格式见 {@link FestivalLetter}）；**信件只是「送什么」的定义，
 * 自己不排期**——投递时机来自日历（节日活动的 {@code letter} 绑定，或 {@code type=birthday} 活动
 * 调用固定的 {@code birthday.json}，见 {@link LetterScheduler}）。
 *
 * <p>首次启动时若目录不存在，就建目录并放三份示例（明信片 / 包裹 / 红包，都带 {@code enabled: false}，
 * 不会误发），另外总会备好默认启用中的 {@code birthday.json}（见 {@link #ensureDefaultFiles()}）。
 *
 * <p><b>何时重读</b>：服务器启动后的首轮检查、{@code /tothesky reloadletters}，以及网页保存信件之后
 * （见 {@link #save(String, JsonObject)}）——「往目录里手工丢新 json」本身不触发重读，改完要跑一次命令
 * 才生效（见 {@link #reload()}）。
 *
 * <p>正文 {@code text} 是**字符串数组，每项一行**（单个字符串也接受，等价于只有一行的数组），
 * 投递时按顺序用换行拼成一个字符串——格式细节见 {@link FestivalLetter}。
 *
 * <p>目录只扫一层：子目录与非 {@code .json} 文件（编辑器临时文件、{@code .json.bak} 等）不会被读取。
 *
 * <p><b>按内容缓存</b>：加载路径（{@link #reload()}）同一文件内容没变就不重新解析——既省掉重复解析，
 * 更关键的是避免「坏文件反复重载都刷一遍 WARN」。文件删掉、被网页改写即从缓存移除。
 * 网页那侧（{@link #list()} / {@link #find(String)}）另走一条不打日志的严格解析，
 * 因为它要展示停用信的内容、且每次刷新都会跑。
 *
 * <p><b>只在服务端线程调用</b>（见 {@link LetterScheduler}）：缓存与定义都不是线程安全的，
 * 故网页那侧（{@code contact.http.LetterApiHandler}）把每个请求都 {@code server.execute(...)}
 * 提交回主线程再碰这里。
 */
public final class LetterLibrary {
    /** 节日信目录：{@code config/tothesky/letters} */
    private static final String LETTERS_DIR = "letters";
    private static final String LETTER_SUFFIX = ".json";
    /** 生日信文件名（默认内容见 {@link #BIRTHDAY_LETTER}） */
    private static final String BIRTHDAY_FILE = "birthday.json";
    /**
     * 生日信的 id：所有 {@code type=birthday} 的日历活动当天都调用它（见 {@code LetterScheduler}）。
     * <p>这是本模组唯一「按文件名认领」的信件——其余信件只能由节日活动的 {@code letter} 字段绑定。
     */
    public static final String BIRTHDAY_ID = stripExtension(BIRTHDAY_FILE);

    /** 文件名 → 缓存（文件内容 + 解析结果，解析失败也照记以免重复报错） */
    private static final Map<String, Cached> CACHE = new HashMap<>();

    /** 写回文件用：美化 + 不转义中文/符号（Gson 默认会把 {@code <>&='} 写成 \\uXXXX） */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 「birthday.json 写不进去」只告警一次，避免每分钟刷屏 */
    private static boolean birthdayWriteWarned;

    private LetterLibrary() {
    }

    private record Cached(String content, FestivalLetter.Outcome outcome) {
    }

    /** 磁盘上一封信：文件原文 + 解析结果 */
    private record Loaded(String raw, FestivalLetter.Outcome outcome) {
    }

    /** 信件目录 {@code config/tothesky/letters} */
    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(ToTheSky.MODID).resolve(LETTERS_DIR);
    }

    /**
     * 备好目录与默认文件（幂等）。
     *
     * <p>调用点有三处：模组构造期（首次加载即备好，玩家还没进世界就能改配置，见 {@code ToTheSky}）、
     * 服务器启动后的首轮检查、{@code /tothesky reloadletters}（后两处见 {@link LetterScheduler}）。
     *
     * <p>三份**示例**（{@code example_*.json}）只在目录不存在时写一次——它们是说明书，
     * 删掉不该复活。{@code birthday.json} 不同：它是**启用中**的功能配置（文件名固定，所有生日活动都调用它），
     * 缺失就补回默认的一份，所以「删掉它」不等于停用（下次加载又会长回来），停用请把文件里的
     * {@code enabled} 改成 {@code false}。
     */
    public static void ensureDefaultFiles() {
        Path dir = dir();
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
                writeExample(dir, "example_postcard.json", POSTCARD_EXAMPLE);
                writeExample(dir, "example_parcel.json", PARCEL_EXAMPLE);
                writeExample(dir, "example_red_packet.json", RED_PACKET_EXAMPLE);
                ToTheSky.LOGGER.info("[节日信] 已生成示例目录 {}（三份示例均为 enabled=false，改完再改成 true）", dir);
            } catch (IOException e) {
                ToTheSky.LOGGER.warn("[节日信] 生成示例目录 {} 失败：{}", dir, e.getMessage());
                return;
            }
        }
        ensureBirthdayLetter(dir);
    }

    /** 生日信配置缺失就补回默认内容（见 {@link #ensureDefaultFiles()} 的说明） */
    private static void ensureBirthdayLetter(Path dir) {
        Path file = dir.resolve(BIRTHDAY_FILE);
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.writeString(file, BIRTHDAY_LETTER, StandardCharsets.UTF_8);
            ToTheSky.LOGGER.info("[节日信] 已生成默认生日信 {}（日历里 type=birthday 的活动当天投递；停用请改 enabled=false）",
                    file);
        } catch (IOException e) {
            if (!birthdayWriteWarned) {
                birthdayWriteWarned = true;
                ToTheSky.LOGGER.warn("[节日信] 生成 {} 失败（生日信不会投递）：{}", file, e.getMessage());
            }
        }
    }

    /**
     * 重读目录，返回当前所有**有效且启用**的节日信（顺序按文件名，稳定）。
     *
     * <p>只在两处调用：服务器启动后的首轮检查、以及 {@code /tothesky reloadletters}
     * （见 {@link LetterScheduler}）——投递检查本身不碰磁盘，所以「改配置」总是显式的一次动作。
     * 网页保存信件后走的是同一套读取（见 {@link #list()}、{@code LetterScheduler#reloadDefinitions}）。
     */
    public static List<FestivalLetter> reload() {
        Path dir = dir();
        List<FestivalLetter> letters = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return letters;
        }
        Set<String> seen = new HashSet<>();
        for (Path file : letterFiles(dir)) {
            seen.add(file.getFileName().toString());
            Loaded loaded = load(file);
            if (loaded != null && loaded.outcome().letter() != null && loaded.outcome().enabled()) {
                letters.add(loaded.outcome().letter());
            }
        }
        // 文件已删除的缓存条目一并清掉，重新放回同名文件时会重新解析
        CACHE.keySet().retainAll(seen);
        return letters;
    }

    /** 目录里的信件文件（只扫一层、只认 {@code *.json}、按文件名排序）；扫描失败告警并返回空列表 */
    private static List<Path> letterFiles(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(LETTER_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[节日信] 扫描 {} 失败：{}", dir, e.getMessage());
            return List.of();
        }
    }

    /** 读一个文件并解析（内容未变则用缓存）；IO 失败返回 null（已告警） */
    @Nullable
    private static Loaded load(Path file) {
        String name = file.getFileName().toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[节日信] 读取 {} 失败：{}", name, e.getMessage());
            return null;
        }
        Cached cached = CACHE.get(name);
        if (cached != null && cached.content().equals(content)) {
            return new Loaded(content, cached.outcome());
        }
        FestivalLetter.Outcome outcome = parse(name, content);
        CACHE.put(name, new Cached(content, outcome));
        return new Loaded(content, outcome);
    }

    /** 解析文件内容（加载语义）：JSON 语法错也算一种错因。日志在这里打，靠缓保证「一个文件内容只报一次」 */
    private static FestivalLetter.Outcome parse(String name, String content) {
        String id = stripExtension(name);
        FestivalLetter.Outcome outcome;
        try {
            outcome = FestivalLetter.parseOutcome(id, JsonParser.parseString(content).getAsJsonObject(), false);
        } catch (Exception e) {
            outcome = new FestivalLetter.Outcome(null, false,
                    "JSON 解析失败（" + (e.getMessage() == null ? e.toString() : e.getMessage()) + "）",
                    List.of());
        }
        FestivalLetter.logOutcome(id, outcome);
        return outcome;
    }

    private static String stripExtension(String fileName) {
        return fileName.endsWith(LETTER_SUFFIX)
                ? fileName.substring(0, fileName.length() - LETTER_SUFFIX.length())
                : fileName;
    }

    private static void writeExample(Path dir, String fileName, String json) throws IOException {
        Files.writeString(dir.resolve(fileName), json, StandardCharsets.UTF_8);
    }

    // ==================== 网页编辑（REST 用） ====================

    /**
     * 磁盘上一封信的当前状态：文件原文 + 解析结果。编辑器一律以**原文**为准
     * （它才是文件的事实），解析结果只用来告诉用户「这封信现在算不算数、为什么不算数」。
     * <p>{@code error} 非 null = 这个文件现在解析不过去（没在投递）；
     * {@code letter} 为 null 只说明「加载时被跳过了」（停用），不代表内容有问题。
     */
    public record Entry(String id, String raw, @Nullable FestivalLetter letter, boolean enabled,
                        @Nullable String error, List<String> notes) {

        /** 配置是否解析通过（停用的信也算通过） */
        public boolean valid() {
            return error == null;
        }
    }

    /**
     * 目录里所有信件的当前状态（按文件名排序）。
     * <p>用**严格**规则解析（停用的信也完整解析）：编辑器要展示停用信的内容才能改它，
     * 所以这里拿到的是「文件长什么样」，与「加载时会不会被跳过」是两件事。
     * <p>列目录与解析都不打日志——不然每次刷新网页都刷一遍 WARN（加载路径的日志见 {@link #parse}）。
     */
    public static List<Entry> list() {
        Path dir = dir();
        List<Entry> entries = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return entries;
        }
        for (Path file : letterFiles(dir)) {
            Loaded loaded = loadForEdit(file);
            if (loaded != null) {
                entries.add(entry(file.getFileName().toString(), loaded));
            }
        }
        return entries;
    }

    /** 单封信的当前状态（同 {@link #list()} 的规则）；id 非法或文件不存在返回 null */
    @Nullable
    public static Entry find(String id) {
        if (validateId(id) != null) {
            return null;
        }
        Path file = dir().resolve(id + LETTER_SUFFIX);
        Loaded loaded = Files.isRegularFile(file) ? loadForEdit(file) : null;
        return loaded == null ? null : entry(file.getFileName().toString(), loaded);
    }

    /** 读一个文件并按**严格**规则解析（网页编辑用；不打日志、不用加载路径的内容缓存） */
    @Nullable
    private static Loaded loadForEdit(Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[节日信] 读取 {} 失败：{}", file.getFileName(), e.getMessage());
            return null;
        }
        String name = file.getFileName().toString();
        FestivalLetter.Outcome outcome;
        try {
            outcome = FestivalLetter.parseOutcome(stripExtension(name),
                    JsonParser.parseString(content).getAsJsonObject(), true);
        } catch (Exception e) {
            outcome = new FestivalLetter.Outcome(null, false,
                    "JSON 解析失败（" + (e.getMessage() == null ? e.toString() : e.getMessage()) + "）",
                    List.of());
        }
        return new Loaded(content, outcome);
    }

    private static Entry entry(String fileName, Loaded loaded) {
        return new Entry(stripExtension(fileName), loaded.raw(), loaded.outcome().letter(),
                loaded.outcome().enabled(), loaded.outcome().error(), loaded.outcome().notes());
    }

    /**
     * 写入一封信（创建或整体替换 {@code <id>.json}）。
     * <p>内容先按**严格**规则校验一遍（{@code enabled=false} 也完整解析），不合法就原样退回错因、不落盘——
     * 免得网页存进一份「重新启用就投不出去」的配置。
     *
     * @return 错因；写入成功返回 {@code null}
     */
    @Nullable
    public static String save(String id, JsonObject json) {
        String idError = validateId(id);
        if (idError != null) {
            return idError;
        }
        FestivalLetter.Outcome outcome = FestivalLetter.parseOutcome(id, json, true);
        if (outcome.error() != null) {
            return outcome.error();
        }
        if (outcome.letter() != null && outcome.letter().style() != null
                && !ContactMail.styleAvailable(outcome.letter().style())) {
            return "未知明信片款式 " + outcome.letter().style() + "（可用款式见 data/<命名空间>/postcards/*.json）";
        }
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(id + LETTER_SUFFIX), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "写入失败：" + e.getMessage();
        }
        // 缓存失效：下次读取（重载或网页刷新）按新内容重解析、重新打一遍日志
        CACHE.remove(id + LETTER_SUFFIX);
        return null;
    }

    /** 删除一封信；返回是否真的删掉了文件 */
    public static boolean delete(String id) {
        if (validateId(id) != null) {
            return false;
        }
        try {
            boolean removed = Files.deleteIfExists(dir().resolve(id + LETTER_SUFFIX));
            CACHE.remove(id + LETTER_SUFFIX);
            return removed;
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[节日信] 删除 {} 失败：{}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 信件 id（= 文件名去 {@code .json}）合法性：非空、≤64 字符、不含文件名禁用字符与控制字符、
     * 不以点或空格结尾、不是 {@code .} / {@code ..}。返回错因，合法返回 {@code null}。
     * <p>比日历里「绑定信件」字段的校验更严（那边只是一个人名，这里是真写文件）。
     */
    @Nullable
    public static String validateId(String id) {
        if (id == null || id.isBlank()) {
            return "信件 id 不能为空";
        }
        if (id.equals(".") || id.equals("..")) {
            return "信件 id 不能是 " + id;
        }
        if (id.length() > 64) {
            return "信件 id 过长（最多 64 字符）";
        }
        if (id.endsWith(LETTER_SUFFIX)) {
            return "信件 id 填文件名去掉 .json（如 chunjie），实为 " + id;
        }
        if (id.endsWith(".") || id.endsWith(" ")) {
            return "信件 id 不能以点或空格结尾：" + id;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < 0x20) {
                return "信件 id 含控制字符";
            }
            if ("\\/:*?\"<>|".indexOf(c) >= 0) {
                return "信件 id 含文件名不允许的字符：" + id;
            }
        }
        return null;
    }

    // ==================== 默认文件（信件只描述内容，由节日绑定或生日调用） ====================

    /**
     * 默认生日信：文件名固定为 {@code birthday.json}（见 {@link #BIRTHDAY_ID}），
     * 日历里每条 {@code type=birthday} 的活动当天都会调用它，收件人 = 活动名（玩家昵称）。
     * <p>默认发一张明信片——明信片可以带正文，是「生日祝福」最自然的载体；
     * 想改送包裹/红包，把 {@code type} 与对应字段换掉即可（见 {@link FestivalLetter} 的格式说明）。
     */
    private static final String BIRTHDAY_LETTER = """
            {
              "_comment": "生日信：文件名固定，日历里每条 type=birthday 的活动当天都发它（收件人 = 活动名 = 玩家昵称），当天过生日的每位玩家各收一份。改这里即可更换生日礼物，停用请把 enabled 改成 false。text 是字符串数组，每项在明信片上一行。",
              "enabled": true,
              "type": "postcard",
              "style": "contact:spring_day",
              "text": [
                "祝${player}生日快乐！",
                "今天是${date}。"
              ]
            }
            """;

    private static final String POSTCARD_EXAMPLE = """
            {
              "_comment": "示例：明信片（样式 + 正文）。信件不写收件人与日期——在日历里新建一个节日，把「绑定信件」填成本文件名（去掉 .json），当天这封信就发给全服每位玩家。enabled 改成 true 才会投递。${player} 会替换成收件人，${date} 会替换成投递当天的日期（yyyy-MM-dd）。text 是字符串数组，每项在明信片上一行（也可以写成单个字符串，那就只有一行）。",
              "enabled": false,
              "type": "postcard",
              "style": "contact:new_year_2023",
              "text": [
                "祝${player}节日快乐！",
                "今天是${date}。"
              ]
            }
            """;

    private static final String PARCEL_EXAMPLE = """
            {
              "_comment": "示例：包裹（只有内容物，Contact 的包裹没有正文），最多 4 件。count 可省略（默认 1）。绑定方式同上：日历里某个节日的「绑定信件」填本文件名。",
              "enabled": false,
              "type": "parcel",
              "items": [
                { "item": "minecraft:cake", "count": 3 },
                { "item": "minecraft:apple", "count": 5 }
              ]
            }
            """;

    private static final String RED_PACKET_EXAMPLE = """
            {
              "_comment": "示例：红包（内容物 + 祝福语），最多 1 件。${item} 只在红包里有效，会替换成内容物清单（物品显示名×数量）。text 是字符串数组，每项一行。",
              "enabled": false,
              "type": "red_packet",
              "items": [
                { "item": "minecraft:diamond", "count": 8 }
              ],
              "text": [
                "祝${player}节日快乐！",
                "红包里是${item}。"
              ]
            }
            """;
}
