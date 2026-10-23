package com.fst.tothesky.client.jei;

import com.fst.tothesky.registry.ModItems;
import com.voxelutopia.ultramarine.data.recipe.ChiselTableRecipe;
import com.voxelutopia.ultramarine.data.registry.RecipeTypeRegistry;
import java.util.List;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * ultramarine 凿刻配方与 JEI 的桥接。类本身引用 ultramarine 类型，
 * 只在 ultramarine 已加载时由 {@link ToTheSkyJeiPlugin} 反射调用。
 */
public final class MechanicalChiselTableJei {
    private MechanicalChiselTableJei() {
    }

    public static void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MechanicalChiselTableCategory(
                registration.getJeiHelpers().getGuiHelper()));
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<ChiselTableRecipe> recipes = level.getRecipeManager()
                .getAllRecipesFor(RecipeTypeRegistry.CHISEL_TABLE.get());
        if (!recipes.isEmpty()) {
            registration.addRecipes(MechanicalChiselTableCategory.RECIPE_TYPE, recipes);
        }
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.MECHANICAL_CHISEL_TABLE_ITEM.get()),
                MechanicalChiselTableCategory.RECIPE_TYPE);
    }
}
