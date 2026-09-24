package com.fst.tothesky.mixin.oelib;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 让 {@code tothesky.oelib.mixins.json} 只在 OELib 存在时应用。
 *
 * <p>ToTheSky 不依赖 OELib（contact 才有），所以这个补丁必须按需开关：OELib 不在时
 * 直接跳过，连目标类的解析都不做——否则 {@code @Mixin(targets = ...)} 找不到类会把
 * mixin 判为失败，反而崩掉本来能正常启动的游戏。
 *
 * <p>判据用「类能否被这个类加载器加载」而不是 {@code ModList.isLoaded("oelib")}：
 * mixin 配置在模组发现完成前就可能被处理，而类加载器视角与 mixin 能否解析到目标类
 * 完全一致（mixin 也是用同一个类加载器去找目标类的），不依赖 FML 的初始化时序。
 * {@code initialize = false} 确保不会顺手触发 OELib 的静态初始化。
 */
public final class OelibMixinPlugin implements IMixinConfigPlugin {

    private static final String PROBE_CLASS = "cc.sighs.oelib.forge.OELibForge";

    private static boolean oelibPresent() {
        try {
            Class.forName(PROBE_CLASS, false, OelibMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return oelibPresent();
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;  // 用配置里的 refmap
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
