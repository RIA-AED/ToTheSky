package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 动力雕刻台（PR#59）：4 栏位（0 材料 / 1 模板 / 2 打包染料 / 3 输出），
 * 接收 FE（上限 10000）后每 tick 最多执行 10 次 ultramarine 凿刻配方。
 * 移植自 kubejs/startup_scripts/block/MechanicalChiselTableBlock.js。
 */
public class MechanicalChiselTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final VoxelShape shape;

    public static final int SLOT_MATERIAL = 0;
    public static final int SLOT_TEMPLATE = 1;
    public static final int SLOT_PACKED_COLORS = 2;
    public static final int SLOT_RESULT = 3;
    public static final int MAX_ENERGY = 10000;
    public static final int ENERGY_COST = 100;
    public static final int MAX_OPERATIONS_PER_TICK = 10;
    /**
     * 单次凿刻的标称耗时（tick）。实际一 tick 内最多批量执行
     * {@link #MAX_OPERATIONS_PER_TICK} 次，这里仅作为 JEI/提示中的制作时间基准。
     */
    public static final int CRAFT_TIME_TICKS = 1;
    public static final float TICKS_PER_SECOND = 20.0F;
    public static final float CRAFT_TIME_SECONDS = CRAFT_TIME_TICKS / TICKS_PER_SECOND;


    public MechanicalChiselTableBlock(VoxelShape shape, Properties properties) {
        super(properties);
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return shape;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanicalChiselTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != com.fst.tothesky.registry.ModBlockEntities.MECHANICAL_CHISEL_TABLE.get()) {
            return null;
        }
        return (l, p, s, be) -> MechanicalChiselTableBlockEntity.serverTick((MechanicalChiselTableBlockEntity) be);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof MechanicalChiselTableBlockEntity entity)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemStack itemInHand = player.getItemInHand(hand);
        boolean doConsume = !player.isCreative();

        if (itemInHand.isEmpty()) {
            if (player.isShiftKeyDown()) {
                for (int i = 0; i < entity.getContainerSize(); i++) {
                    ItemStack stack = entity.removeItem(i, entity.getContainerSize() == 0 ? 0 : 64);
                    if (!stack.isEmpty()) {
                        player.getInventory().placeItemBackInInventory(stack);
                    }
                }
                player.displayClientMessage(Component.literal("已清空所有槽位"), true);
            } else if (entity.getFilterId() != null) {
                entity.setFilterId(null);
                player.displayClientMessage(Component.literal("过滤器已清空"), true);
            }
        } else if (isChiselTemplate(itemInHand)) {
            ItemStack preTemplate = entity.removeItem(SLOT_TEMPLATE, 1);
            ItemStack toInsert = itemInHand.copyWithCount(1);
            ItemStack remainder = entity.insertItem(SLOT_TEMPLATE, toInsert, false);
            if (remainder.isEmpty()) {
                if (doConsume) {
                    itemInHand.shrink(1);
                }
                if (!preTemplate.isEmpty() && doConsume) {
                    player.getInventory().placeItemBackInInventory(preTemplate);
                }
                player.displayClientMessage(Component.literal("模板已设置为: " + toInsert.getHoverName().getString()), true);
            } else {
                entity.insertItem(SLOT_TEMPLATE, preTemplate, false);
            }
        } else {
            entity.setFilterId(ForgeRegistries.ITEMS.getKey(itemInHand.getItem()));
            player.displayClientMessage(Component.literal("过滤器已设置为: " + itemInHand.getHoverName().getString()), true);
        }
        entity.setChanged();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** ultramarine 凿刻模板判定（软依赖：模组缺失时不可设模板） */
    public static boolean isChiselTemplate(ItemStack stack) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ultramarine")) {
            return false;
        }
        Class<?> templateClass;
        try {
            templateClass = Class.forName("com.voxelutopia.ultramarine.world.item.ChiselTemplate");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return templateClass.isInstance(stack.getItem());
    }
}