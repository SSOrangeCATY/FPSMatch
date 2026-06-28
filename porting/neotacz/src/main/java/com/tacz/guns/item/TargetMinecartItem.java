package com.tacz.guns.item;

import com.tacz.guns.entity.TargetMinecart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;

public class TargetMinecartItem extends Item {
    public TargetMinecartItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        BlockState blockstate = level.getBlockState(blockpos);
        if (!blockstate.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        } else {
            ItemStack itemstack = context.getItemInHand();
            if (!level.isClientSide()) {
                RailShape railshape = blockstate.getBlock() instanceof BaseRailBlock baseRailBlock ? baseRailBlock.getRailDirection(blockstate, level, blockpos, null) : RailShape.NORTH_SOUTH;
                double yOffset = 0;
                if (railshape.isSlope()) {
                    yOffset = 0.5;
                }
                TargetMinecart targetMinecart = new TargetMinecart(level, (double) blockpos.getX() + 0.5, (double) blockpos.getY() + 0.0625 + yOffset, (double) blockpos.getZ() + 0.5);
                Component customName = itemstack.getCustomName();
                if (customName != null) {
                    targetMinecart.setCustomName(customName);
                }
                level.addFreshEntity(targetMinecart);
                level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, blockpos);
            }
            itemstack.shrink(1);
            return InteractionResult.SUCCESS;
        }
    }
}
