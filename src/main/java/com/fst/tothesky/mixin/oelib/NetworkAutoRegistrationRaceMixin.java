package com.fst.tothesky.mixin.oelib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Set;

/**
 * 修 OELib（{@code oelib}）0.2.4.1 的启动竞态崩溃。
 *
 * <p><b>症状</b>：开局随机崩在模组构造期，报
 * {@code java.util.ConcurrentModificationException}，
 * {@code Mod File: OELib-forge-1.20.1-0.2.4.1.jar / OELib (oelib) has failed to load correctly}，
 * 栈顶固定为
 * {@code cc.sighs.oelib.network.api.NetworkAutoRegistration.findAllAnnotatedPackets(…:81)}
 * ← {@code cc.sighs.oelib.forge.OELibForge.<init>}。
 *
 * <p><b>成因</b>：{@code BASE_PACKAGES} 是
 * {@code Collections.synchronizedSet(new LinkedHashSet<>())}。该包装器只把单次
 * {@code add} 放进监视器，<b>迭代不受保护</b>——而第 81 行正是
 * {@code Set.copyOf(BASE_PACKAGES)}（{@code HashSet} 拷贝构造 → 迭代源集合）。
 * 同时，往来（Contact）的构造器第一件事就是
 * {@code NetworkManager.registerPacketScanPackage("com.flechazo.contact.network")}
 * → {@code registerBasePackage} → {@code BASE_PACKAGES.add(...)}。
 * 两个 mod 的构造器都由 FML 并行分发到 worker 线程，且各自第一条语句就落在这两行上，
 * 于是「一边迭代、一边 add」几乎是同时发生，随机抛 CME，OELib 构造失败 → 整个启动失败。
 *
 * <p><b>修法</b>：把那次复制挪进 {@code BASE_PACKAGES} 自己的监视器里——
 * 这正是 {@code Collections.synchronizedSet} 文档要求的用法（遍历前手动 synchronized）。
 * {@code registerBasePackage} 的 {@code add} 走的是同一把锁，于是迭代与修改互斥，竞态消失。
 * 行为其余部分不变：复制出来仍是同样的不可变快照。
 *
 * <p>类里另一处 {@code Set.copyOf} 拷的是 {@code REGISTERED_PACKET_CLASSES}，它是
 * {@code ConcurrentHashMap.newKeySet()}，迭代弱一致、不会 CME，故不动；
 * {@code scanAndCollect} 的复制都在 {@code REGISTRATION_LOCK} 内，也无需处理。
 *
 * <p>仅当 OELib 存在时才应用（见 {@link OelibMixinPlugin}）。
 */
@Mixin(targets = "cc.sighs.oelib.network.api.NetworkAutoRegistration")
public abstract class NetworkAutoRegistrationRaceMixin {

    /**
     * 拦截 {@code findAllAnnotatedPackets} 里第一处 {@code Set.copyOf(...)}
     * （即 {@code Set.copyOf(BASE_PACKAGES)}，源码第 81 行），改为持锁复制。
     *
     * @param source 被复制的集合；运行时就是 OELib 的 {@code BASE_PACKAGES} 实例
     * @return 与原来完全相同的快照
     */
    @Redirect(
            method = "findAllAnnotatedPackets",
            at = @At(
                    value = "INVOKE",
                    ordinal = 0,
                    target = "Ljava/util/Set;copyOf(Ljava/util/Collection;)Ljava/util/Set;"))
    private static Set<?> tothesky$copyBasePackagesUnderLock(Collection<?> source) {
        // synchronizedSet 的互斥量就是包装器自身，也就是这里的 source
        synchronized (source) {
            return Set.copyOf(source);
        }
    }
}
