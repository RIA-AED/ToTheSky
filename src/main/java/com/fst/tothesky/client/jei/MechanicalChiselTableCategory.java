package com.fst.tothesky.client.jei;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.MechanicalChiselTableBlock;
import com.fst.tothesky.item.PackedColorsItem;
import com.fst.tothesky.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.voxelutopia.ultramarine.data.recipe.ChiselTableRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * 动力雕刻台 JEI 页面。
 *
 * <p>布局：左侧是竖直的能量条与材料 / 包装颜料输入，中间是动力雕刻台的 3D 模型
 * （模板槽叠在模型上方），右侧产物；模板不消耗，因此槽位右上角绘制 NC。
 * 模型下方只显示处理时间，能量消耗改由左侧能量条表达。</p>
 */
public final class MechanicalChiselTableCategory implements IRecipeCategory<ChiselTableRecipe> {
    public static final RecipeType<ChiselTableRecipe> RECIPE_TYPE = new RecipeType<>(
            new ResourceLocation(ToTheSky.MODID, "mechanical_chisel_table"), ChiselTableRecipe.class);

    private static final int WIDTH = 175;
    private static final int HEIGHT = 70;

    private static final int ENERGY_X = 4;
    private static final int ENERGY_Y = 4;
    private static final int ENERGY_WIDTH = 8;
    private static final int ENERGY_HEIGHT = 62;
    private static final int ENERGY_INNER_HEIGHT = ENERGY_HEIGHT - 2;

    private static final int MATERIAL_X = 24;
    private static final int MATERIAL_Y = 31;
    private static final int PACKED_COLORS_X = 24;
    private static final int PACKED_COLORS_Y = 13;
    private static final int TEMPLATE_X = 85;
    private static final int TEMPLATE_Y = 20;
    private static final int OUTPUT_X = 150;
    private static final int OUTPUT_Y = 22;

    private static final int ARROW_START_X = 48;
    private static final int ARROW_END_X = 140;
    private static final int ARROW_Y = 30;
    private static final int ARROW_BACKGROUND_COLOR = 0xFFAAAAAA;
    private static final int ARROW_PROGRESS_COLOR = 0xFFFFFFFF;
    private static final long ARROW_CYCLE_NANOS = 2_000_000_000L;

    private static final int BASE_X = 70;
    private static final int BASE_Y = 4;
    private static final float BASE_SCALE = 3.0F;
    private static final int BASE_CENTER_X = 93;
    private static final float BASE_Z = 30.0F;
    private static final float TEMPLATE_SLOT_Z = 40.0F;
    private static final float FOREGROUND_Z = 50.0F;

    private static final int TEXT_Y_TIME = 56;
    private static final int TEXT_COLOR = 0xFF404040;

    private static final int MAX_DISPLAYED_COLORS = 4;

    private final ItemStack machine;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    private final IDrawable energyBarBackground;
    private final IDrawableAnimated energyBarFill;
    private final Component timeLabel;
    private final long animationStartNanos = System.nanoTime();

    public MechanicalChiselTableCategory(IGuiHelper guiHelper) {
        this.machine = new ItemStack(ModItems.MECHANICAL_CHISEL_TABLE_ITEM.get());
        this.icon = guiHelper.createDrawableItemStack(this.machine);
        this.slotBackground = guiHelper.getSlotDrawable();

        // 纹理左半边是空槽，右半边是满格；
        // 空槽整块绘制，填充部分从右半边按比例裁切后从底部对齐。
        ResourceLocation energyTexture = new ResourceLocation(
                ToTheSky.MODID, "textures/gui/jei/energy_bar.png");
        this.energyBarBackground = guiHelper.drawableBuilder(
                        energyTexture, 0, 0, ENERGY_WIDTH, ENERGY_HEIGHT)
                .setTextureSize(32, 64)
                .build();
        // （源区域 u=8, v=1, w=8, h=60，目标位置 y+1）。用 IDrawableAnimated 让填充从底部慢慢涨满。
        this.energyBarFill = guiHelper.drawableBuilder(
                        energyTexture, ENERGY_WIDTH, 1, ENERGY_WIDTH, ENERGY_INNER_HEIGHT)
                .setTextureSize(32, 64)
                .buildAnimated(200, IDrawableAnimated.StartDirection.BOTTOM, false);

        this.timeLabel = Component.translatable(
                "jei.tothesky.mechanical_chisel_table.time",
                String.format(Locale.ROOT, "%.2f", MechanicalChiselTableBlock.CRAFT_TIME_SECONDS), MechanicalChiselTableBlock.MAX_OPERATIONS_PER_TICK);
    }

    @Override
    public RecipeType<ChiselTableRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.tothesky.mechanical_chisel_table");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ChiselTableRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(MATERIAL_X, MATERIAL_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.getMaterial());

        builder.addInputSlot(PACKED_COLORS_X, PACKED_COLORS_Y)
                .setStandardSlotBackground()
                .addItemStack(packedColorsFor(recipe))
                .addRichTooltipCallback((slotView, tooltip) -> tooltip.add(
                        Component.translatable("jei.tothesky.mechanical_chisel_table.packed_colors_hint")
                                .withStyle(ChatFormatting.GRAY)));

        // 包装颜料是高阶物品，单独显示它；染料本身作为隐藏输入保留 JEI 的查询能力。
        var lookupOnly = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT);
        for (Ingredient color : recipe.getColors()) {
            lookupOnly.addIngredients(color);
        }

        builder.addInputSlot(TEMPLATE_X, TEMPLATE_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.getTemplate())
                .addRichTooltipCallback((slotView, tooltip) -> tooltip.add(
                        Component.translatable("jei.tothesky.mechanical_chisel_table.not_consumed")
                                .withStyle(ChatFormatting.GRAY)));

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (!result.isEmpty()) {
            builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                    .setOutputSlotBackground()
                    .addItemStack(result);
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ChiselTableRecipe recipe, IFocusGroup focuses) {
        builder.addDrawable(new NotConsumedMarker(), TEMPLATE_X + 8, TEMPLATE_Y);
    }

    @Override
    public void draw(ChiselTableRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        drawArrow(graphics);
        drawEnergyBar(graphics);
        drawMachine(graphics);
        drawTemplateSlotBackground(graphics);

        Font font = Minecraft.getInstance().font;
        drawForegroundText(graphics, font, this.timeLabel,
                BASE_CENTER_X - font.width(this.timeLabel) / 2, TEXT_Y_TIME);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ChiselTableRecipe recipe, IRecipeSlotsView recipeSlotsView,
                           double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        if (mouseX >= ENERGY_X && mouseX < ENERGY_X + ENERGY_WIDTH
                && mouseY >= ENERGY_Y && mouseY < ENERGY_Y + ENERGY_HEIGHT) {
            tooltip.add(Component.translatable("jei.tothesky.mechanical_chisel_table.energy",
                            MechanicalChiselTableBlock.ENERGY_COST)
                    .withStyle(ChatFormatting.GRAY));
        } else if (mouseX >= BASE_CENTER_X - font.width(this.timeLabel) / 2 && mouseX < BASE_CENTER_X + font.width(this.timeLabel) / 2
                && mouseY >= TEXT_Y_TIME && mouseY < HEIGHT) {
            tooltip.add(Component.translatable("jei.tothesky.mechanical_chisel_table.time_label",
                    MechanicalChiselTableBlock.MAX_OPERATIONS_PER_TICK, MechanicalChiselTableBlock.CRAFT_TIME_SECONDS)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getRegistryName(ChiselTableRecipe recipe) {
        return recipe.getId();
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        // 整条空槽 + 从底部对齐的填充切片。
        this.energyBarBackground.draw(graphics, ENERGY_X, ENERGY_Y);
        this.energyBarFill.draw(graphics, ENERGY_X, ENERGY_Y + 1);
    }

    private void drawMachine(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // renderItem 内部会在 z=150 处再平移一次；预先抵消并抬高，确保 3D 模型在页面底图之上、
        // 又在 JEI 槽位物品（z=150）之下。模板槽背景另外在 z=40 补画一层，避免被模型盖住。
        pose.translate(BASE_X, BASE_Y, BASE_Z - 150.0F * BASE_SCALE);
        pose.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);
        graphics.renderItem(this.machine, 0, 0);
        pose.popPose();
    }

    private void drawTemplateSlotBackground(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, TEMPLATE_SLOT_Z);
        this.slotBackground.draw(graphics, TEMPLATE_X - 1, TEMPLATE_Y - 1);
        pose.popPose();
    }

    /**
     * 与 1.21.1 参考实现相同的长箭头画法：四段矩形拼出箭头，
     * 先用背景色画满，再用进度色按时间比例裁切右端。
     */
    private void drawArrow(GuiGraphics graphics) {
        drawArrow(graphics, ARROW_BACKGROUND_COLOR, ARROW_END_X);

        long elapsedNanos = Math.max(0L, System.nanoTime() - animationStartNanos);
        float progress = (elapsedNanos % ARROW_CYCLE_NANOS) / (float) ARROW_CYCLE_NANOS;
        int progressEnd = ARROW_START_X
                + (int) Math.ceil((ARROW_END_X - ARROW_START_X) * progress);
        drawArrow(graphics, ARROW_PROGRESS_COLOR, progressEnd);
    }

    private static void drawArrow(GuiGraphics graphics, int color, int clipEnd) {
        fillClipped(graphics, ARROW_START_X, ARROW_Y - 1, ARROW_END_X - 5, ARROW_Y + 1, color, clipEnd);
        fillClipped(graphics, ARROW_END_X - 6, ARROW_Y - 5, ARROW_END_X - 4, ARROW_Y + 6, color, clipEnd);
        fillClipped(graphics, ARROW_END_X - 4, ARROW_Y - 3, ARROW_END_X - 2, ARROW_Y + 4, color, clipEnd);
        fillClipped(graphics, ARROW_END_X - 2, ARROW_Y - 1, ARROW_END_X, ARROW_Y + 2, color, clipEnd);
    }

    private static void fillClipped(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                    int color, int clipEnd) {
        int clippedX1 = Math.min(x1, clipEnd);
        if (clippedX1 > x0) {
            graphics.fill(x0, y0, clippedX1, y1, color);
        }
    }

    private static void drawForegroundText(GuiGraphics graphics, Font font, Component text, int x, int y) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, FOREGROUND_Z);
        graphics.drawString(font, text, x, y, TEXT_COLOR, false);
        pose.popPose();
    }

    /**
     * 用配方里的染料顺序构造一个展示用包装颜料；包装颜料的物品提示会列出这些染料。
     * 只取每种 Ingredient 的第一个匹配物品，标签类原料的其它等价物仍可通过隐藏输入查询。
     */
    private static ItemStack packedColorsFor(ChiselTableRecipe recipe) {
        ItemStack stack = new ItemStack(ModItems.PACKED_COLORS.get());
        ListTag storedColors = new ListTag();
        int colorCount = Math.min(MAX_DISPLAYED_COLORS, recipe.getColors().size());
        for (int i = 0; i < colorCount; i++) {
            ItemStack[] matches = recipe.getColors().get(i).getItems();
            if (matches.length == 0) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(matches[0].getItem());
            if (id != null) {
                storedColors.add(StringTag.valueOf(id.toString()));
            }
        }
        if (!storedColors.isEmpty()) {
            CompoundTag tag = new CompoundTag();
            tag.put(PackedColorsItem.TAG_COLORS, storedColors);
            stack.setTag(tag);
        }
        return stack;
    }

    /** 模板槽右上角的红色 NC（Not Consumed）标记。 */
    private static final class NotConsumedMarker implements IDrawable {
        private static final float SCALE = 0.75F;

        @Override
        public int getWidth() {
            return 9;
        }

        @Override
        public int getHeight() {
            return 6;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 200.0F);
            pose.scale(SCALE, SCALE, 1.0F);
            graphics.drawString(Minecraft.getInstance().font, "NC", 0, 0, 0xFFFF4A4A, false);
            pose.popPose();
        }
    }
}
