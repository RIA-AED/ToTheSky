package com.fst.tothesky.client.jei;

import com.fst.tothesky.item.PackedColorsItem;
import com.fst.tothesky.recipe.PackedColorsRecipe;
import com.fst.tothesky.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 让 JEI 的无序合成分类认识 {@link PackedColorsRecipe}。
 *
 * <p>自定义特殊配方默认没有 JEI 的 crafting category extension，因此不会被渲染；
 * 这里把它的展示用 shape（宣纸 + 4 个染料）交给 JEI 的标准合成网格 helper。</p>
 */
public final class PackedColorsRecipeExtension implements ICraftingCategoryExtension {
    private final PackedColorsRecipe recipe;

    public PackedColorsRecipeExtension(PackedColorsRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper gridHelper, IFocusGroup focuses) {
        builder.setShapeless();

        // 查询具体包装颜料时，focus 里会带 StoredColors。按 NBT 顺序只显示对应的染料。
        ItemStack focused = focuses.getItemStackFocuses()
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> stack.is(ModItems.PACKED_COLORS.get()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
        List<ItemStack> focusedColors = PackedColorsItem.getColors(focused);
        if (!focusedColors.isEmpty() && !this.recipe.getIngredients().isEmpty()) {
            List<List<ItemStack>> exactInputs = new ArrayList<>();
            exactInputs.add(Arrays.asList(this.recipe.getIngredients().get(0).getItems()));
            int colorCount = Math.min(4, focusedColors.size());
            for (int i = 0; i < colorCount; i++) {
                exactInputs.add(List.of(focusedColors.get(i).copyWithCount(1)));
            }
            gridHelper.createAndSetInputs(builder, exactInputs, 3, 3);
            gridHelper.createAndSetOutputs(builder, List.of(focused.copyWithCount(1)));
            return;
        }

        List<List<ItemStack>> inputs = new ArrayList<>();
        for (Ingredient ingredient : this.recipe.getIngredients()) {
            inputs.add(Arrays.asList(ingredient.getItems()));
        }
        var inputSlots = gridHelper.createAndSetInputs(builder, inputs, 3, 3);
        if (inputSlots.size() > 1) {
            inputSlots.get(1).addRichTooltipCallback((slotView, tooltip) -> tooltip.add(
                    net.minecraft.network.chat.Component.translatable(
                                    "jei.tothesky.packed_colors.dye_count")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)));
        }

        var level = Minecraft.getInstance().level;
        if (level != null) {
            ItemStack result = this.recipe.getResultItem(level.registryAccess());
            if (!result.isEmpty()) {
                gridHelper.createAndSetOutputs(builder, List.of(result));
            }
        }
    }

    @Override
    public ResourceLocation getRegistryName() {
        return this.recipe.getId();
    }
}
