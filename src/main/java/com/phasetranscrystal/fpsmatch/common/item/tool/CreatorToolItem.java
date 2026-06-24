package com.phasetranscrystal.fpsmatch.common.item.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public abstract class CreatorToolItem extends FPSMToolItem{

    public CreatorToolItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public boolean canDestroyBlock(@NotNull ItemStack itemStack, @NotNull BlockState state, @NotNull Level level,
                                   @NotNull BlockPos pos, @NotNull LivingEntity user) {
        return false;
    }

}
