package com.fst.tothesky.restore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * 补偿台账（服务器级 SavedData，存于主世界 {@code data/tothesky_restore_ledger.dat}）。
 *
 * <p><b>为什么必须有它</b>：补偿的产物是真实物品。若同一条清单被执行两次
 * （重跑命令、崩溃后重试、运维换人再点一次），玩家就会凭空拿到双份——这是不可逆的
 * 经济事故。台账以「条目指纹」为键，记录**已成功补偿**的条目，重跑时自动跳过。
 *
 * <p>指纹取「来源定位 + 物品内容」的组合（见 {@link #fingerprint}）：同一件物品在
 * 同一位置反复出现会得到同一个指纹，从而被认作已处理；而清单换了一份、位置或数量变了，
 * 指纹随之改变，会正常当作新条目处理。
 *
 * <p>只在服务器线程读写；指纹集合量级为「条目数」，全量序列化。
 */
public final class RestoreLedger extends SavedData {
    public static final String DATA_NAME = "tothesky_restore_ledger";
    private static final String TAG_APPLIED = "applied";

    /** 已成功补偿的条目指纹 */
    private final Set<String> applied = new HashSet<>();

    private RestoreLedger() {
    }

    /** 读取/创建（服务器级：挂在主世界上） */
    public static RestoreLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(RestoreLedger::read, RestoreLedger::new, DATA_NAME);
    }

    private static RestoreLedger read(CompoundTag tag) {
        RestoreLedger data = new RestoreLedger();
        ListTag list = tag.getList(TAG_APPLIED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.applied.add(list.getString(i));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String fingerprint : applied) {
            list.add(StringTag.valueOf(fingerprint));
        }
        tag.put(TAG_APPLIED, list);
        return tag;
    }

    /** 该条目是否已经补偿过 */
    public boolean contains(String fingerprint) {
        return applied.contains(fingerprint);
    }

    /** 记一笔已补偿 */
    public void mark(String fingerprint) {
        if (applied.add(fingerprint)) {
            setDirty();
        }
    }

    /** 已记录条数（诊断用） */
    public int size() {
        return applied.size();
    }

    /**
     * 条目指纹：来源定位 + 物品内容摘要，SHA-256 十六进制。
     *
     * <p>用摘要而非拼接原文，是为了让台账文件不随条目数膨胀成巨型 NBT。
     * 指纹里刻意<b>不含</b>清单路径与生成时间——同一件物品换个清单文件再扫一次，
     * 仍然应当被认作「已补偿」。
     */
    public static String fingerprint(RestoreManifest.Entry entry) {
        RestoreManifest.Holder outer = entry.outerHolder();
        StringBuilder sb = new StringBuilder(128);
        sb.append(entry.type()).append('\u0001')
                .append(outer.dimension()).append('\u0001')
                .append(outer.pos() == null ? "-" : outer.pos().toString()).append('\u0001')
                .append(outer.playerUuid() == null ? "-" : outer.playerUuid()).append('\u0001')
                .append(outer.entityUuid() == null ? "-" : outer.entityUuid()).append('\u0001')
                .append(entry.path()).append('\u0001')
                .append(entry.itemId()).append('\u0001')
                .append(entry.amount()).append('\u0001')
                .append(entry.snbt() == null ? "-" : entry.snbt());
        return sha256(sb.toString());
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 规范要求必备的算法，走不到这里；退化成明文键也比崩溃强
            return Integer.toHexString(text.hashCode());
        }
    }
}
