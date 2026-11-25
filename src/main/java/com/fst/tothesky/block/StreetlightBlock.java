package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Floor-standing lantern, or a wall-mounted lantern with the supplied mounting bracket. */
public class StreetlightBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light_level", 0, 15);
    private static final VoxelShape FLOOR_SHAPE = Shapes.or(
            Block.box(6, 0, 6, 10, 4, 10),
            Block.box(1.5, 3, 2.5, 14.5, 14, 13.5),
            Block.box(7, 14, 7, 9, 16, 9));
    // The source model faces east. Blockstate rotations include the requested 90-degree correction.
    private static final VoxelShape WALL_EAST = Shapes.or(FLOOR_SHAPE.move(2.0 / 16, 0, 0),
            Block.box(0, 3, 5.5, 1, 15, 10.5),
            Block.box(1, 4, 7, 4, 14, 9),
            Block.box(3, -1, 7, 11, 4, 9),
            Block.box(8.5, -4, 6.5, 11.5, 0, 9.5));
    private static final VoxelShape WALL_SOUTH = rotateClockwise(WALL_EAST);
    private static final VoxelShape WALL_WEST = rotateClockwise(WALL_SOUTH);
    private static final VoxelShape WALL_NORTH = rotateClockwise(WALL_WEST);

    public StreetlightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(WALL_MOUNTED, false).setValue(LIGHT_LEVEL, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WALL_MOUNTED, LIGHT_LEVEL);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            int lightLevel = switch (state.getValue(LIGHT_LEVEL)) {
                case 15 -> 10;
                case 10 -> 0;
                default -> 15;
            };
            level.setBlock(pos, state.setValue(LIGHT_LEVEL, lightLevel), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) return null;
        boolean wall = face.getAxis().isHorizontal();
        BlockState state = defaultBlockState().setValue(WALL_MOUNTED, wall)
                .setValue(FACING, wall ? face : context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(WALL_MOUNTED)) return Block.canSupportCenter(level, pos.below(), Direction.UP);
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction support = state.getValue(WALL_MOUNTED) ? state.getValue(FACING).getOpposite() : Direction.DOWN;
        if (direction == support && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(WALL_MOUNTED)) return FLOOR_SHAPE;
        return switch (state.getValue(FACING)) {
            case EAST -> WALL_EAST;
            case SOUTH -> WALL_SOUTH;
            case WEST -> WALL_WEST;
            default -> WALL_NORTH;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape rotated = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            rotated = Shapes.or(rotated, Shapes.box(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX));
        }
        return rotated;
    }
}
