package com.fst.tothesky.event;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 已处理区块台账（维度级 SavedData，落盘于该维度 {@code data/tothesky_copycat_fix.dat}）。
 *
 * <p>{@code CopycatBlockEntityDataFixMixin} 的一次性迁移配套：旧存档的 Copycats+ 伪装板
 * 材质数据每个区块只需要检查一次——修复是幂等的，改完就永久正确，玩家之后怎么动这些方块
 * 与本代码无关。所以这里记的是「哪些区块已经跑过那一遍扫描」，**不区分当时是否真的改到了
 * 东西**；未命中则跑扫描，然后无条件落台账，此后该区块不再进入扫描。
 *
 * <p>查询是两级数组索引 + 位测试，O(1)。
 *
 * <p><b>按 region 位图存</b>，而不是一个 long 一个区块：键为区块所属 region
 * （{@code chunkX >> 5, chunkZ >> 5}，与世界文件夹里 {@code r.X.Z.mca} 的切分完全一致），
 * 值为 1024 位（32×32 区块）的位图。代价从「区块数」降到「跨过的 region 数」：
 * 10000×10000 的存档 = 20×20 = 400 个 region 封顶，即 400 × 128 B ≈ 51 KB；
 * 平铺成一个 long/区块则是 39 万个 ≈ 3.1 MB，且每次自动保存都要整份重写。
 *
 * <p>按维度分文件：挂在该 {@link ServerLevel} 自己的 {@code DimensionDataStorage} 上。
 * 不同维度的同一对 x/z 是两个互不相干的区块，分开存既省掉维度前缀编码，也天然隔离。
 *
 * <p>只在服务端线程读写（{@code ChunkSerializer.read} 在主线程执行），全量序列化。
 */
public final class CopycatFixLedger extends SavedData {
    public static final String DATA_NAME = "tothesky_copycat_fix";
    private static final String TAG_REGIONS = "regions";
    private static final String TAG_POS = "pos";
    private static final String TAG_BITS = "bits";

    /** region 边长（区块），与 {@code ChunkPos} 的 region 切分一致 */
    private static final int REGION_SIZE = 32;
    private static final int REGION_SHIFT = 5;
    private static final int REGION_MASK = REGION_SIZE - 1;
    private static final int BITS_PER_REGION = REGION_SIZE * REGION_SIZE;
    private static final int WORDS_PER_REGION = BITS_PER_REGION / Long.SIZE;

    /** region 键（{@link ChunkPos#asLong}）→ {@value #WORDS_PER_REGION} 个 long 的位图 */
    private final Long2ObjectMap<long[]> regions = new Long2ObjectOpenHashMap<>();

    private CopycatFixLedger() {
    }

    /** 读取/创建该维度的台账 */
    public static CopycatFixLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CopycatFixLedger::read, CopycatFixLedger::new, DATA_NAME);
    }

    private static CopycatFixLedger read(CompoundTag tag) {
        CopycatFixLedger data = new CopycatFixLedger();
        ListTag list = tag.getList(TAG_REGIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long[] bits = entry.getLongArray(TAG_BITS);
            if (bits.length != WORDS_PER_REGION) {
                continue;  // 文件被改坏就丢掉这一条，其余照常读
            }
            data.regions.put(entry.getLong(TAG_POS), bits);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<long[]> region : regions.long2ObjectEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_POS, region.getLongKey());
            entry.putLongArray(TAG_BITS, region.getValue());
            list.add(entry);
        }
        tag.put(TAG_REGIONS, list);
        return tag;
    }

    /** 该区块是否已经跑过扫描（O(1)） */
    public boolean isVisited(ChunkPos pos) {
        long[] bits = regions.get(regionKey(pos.x, pos.z));
        if (bits == null) {
            return false;
        }
        int bit = bitIndex(pos.x, pos.z);
        return (bits[bit >>> 6] & bitMask(bit)) != 0;
    }

    /** 记一笔已处理（O(1)） */
    public void markVisited(ChunkPos pos) {
        long key = regionKey(pos.x, pos.z);
        long[] bits = regions.get(key);
        if (bits == null) {
            // 用 get/put 而不是 computeIfAbsent：后者的函数式重载在 fastutil 各版本间签名有变，
            // 这里不值得为省两行冒 NoSuchMethodError 的风险。
            bits = new long[WORDS_PER_REGION];
            regions.put(key, bits);
        }
        int bit = bitIndex(pos.x, pos.z);
        int word = bit >>> 6;
        long mask = bitMask(bit);
        if ((bits[word] & mask) == 0) {
            bits[word] |= mask;
            setDirty();
        }
    }

    /** 区块 → 所属 region 的键 */
    private static long regionKey(int chunkX, int chunkZ) {
        return ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
    }

    /** 区块 → 位图内位号：行 = region 内 z，列 = region 内 x */
    private static int bitIndex(int chunkX, int chunkZ) {
        return ((chunkZ & REGION_MASK) * REGION_SIZE) + (chunkX & REGION_MASK);
    }

    /** 位号 → long 内的掩码（位号对 64 取模由 Java 的移位语义保证） */
    private static long bitMask(int bit) {
        return 1L << (bit & (Long.SIZE - 1));
    }
}
