package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class StatueBlock extends BaseEntityBlock {
    public static final MapCodec<StatueBlock> CODEC = simpleCodec(StatueBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StatueBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return state.getValue(HALF).equals(DoubleBlockHalf.LOWER) && level.isClientSide() ? createTickerHelper(blockEntityType, ModBlocks.STATUE_BE.get(), StatueBlockEntity::clientTick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return pState.getValue(HALF) == DoubleBlockHalf.LOWER ? new StatueBlockEntity(pPos, pState) : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return interact(state, level, pos, player, hand, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return interact(state, level, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof StatueBlockEntity statueBlockEntity) {
                if (stack.getItem() instanceof IGun) {
                    statueBlockEntity.setGun(stack);
                    stack.shrink(1);
                    return InteractionResult.SUCCESS;
                }

                if (stack.isEmpty()) {
                    statueBlockEntity.dropItem();
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos above = clickedPos.above();
        Level level = context.getLevel();
        if (level.getBlockState(above).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(above)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide()) {
            BlockPos above = pos.above();
            world.setBlock(above, state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            world.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(world, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos currentPos, Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);

        if (facing.getAxis() == Direction.Axis.Y) {
            if (half.equals(DoubleBlockHalf.LOWER) && facing == Direction.UP || half.equals(DoubleBlockHalf.UPPER) && facing == Direction.DOWN) {
                // 拆一半另外一半跟着没
                if (!facingState.is(this)) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }

        return state;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }
}
