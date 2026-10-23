package com.fst.tothesky.client.jei;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.recipe.PackedColorsRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * JEI 入口。真正引用 ultramarine 配方类的代码放在
 * {@code MechanicalChiselTableJei} 里，只有安装了 ultramarine 时才通过反射加载，
 * 避免软依赖缺失时 JEI 注解扫描把本模组的插件类加载失败。
 */
@JeiPlugin
public final class ToTheSkyJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(ToTheSky.MODID, "jei_plugin");
    private static final String CHISEL_TABLE_INTEGRATION =
            "com.fst.tothesky.client.jei.MechanicalChiselTableJei";

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!ultramarineLoaded()) {
            return;
        }
        invoke(CHISEL_TABLE_INTEGRATION, "registerCategories", IRecipeCategoryRegistration.class, registration);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ultramarineLoaded()) {
            return;
        }
        invoke(CHISEL_TABLE_INTEGRATION, "registerRecipes", IRecipeRegistration.class, registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!ultramarineLoaded()) {
            return;
        }
        invoke(CHISEL_TABLE_INTEGRATION, "registerRecipeCatalysts", IRecipeCatalystRegistration.class, registration);
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        if (!ultramarineLoaded()) {
            return;
        }
        registration.getCraftingCategory().addCategoryExtension(
                PackedColorsRecipe.class, PackedColorsRecipeExtension::new);
    }

    private static boolean ultramarineLoaded() {
        return ModList.get().isLoaded("ultramarine");
    }

    private static void invoke(String className, String method, Class<?> parameterType, Object argument) {
        try {
            Class.forName(className)
                    .getMethod(method, parameterType)
                    .invoke(null, argument);
        } catch (ReflectiveOperationException | LinkageError e) {
            ToTheSky.LOGGER.warn("Failed to register JEI integration {}", className, e);
        }
    }
}
