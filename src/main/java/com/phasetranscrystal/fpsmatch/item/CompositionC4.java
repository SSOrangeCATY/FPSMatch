package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;


public class CompositionC4 extends Item {

    public CompositionC4(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if(level.isClientSide) return InteractionResultHolder.pass(itemstack);
        BaseMap baseMap = FPSMCore.getInstance().getMapByPlayer(player);
        if(baseMap instanceof BlastModeMap map) {
            if(!baseMap.isStart) {
                player.displayClientMessage(Component.literal("You can't place bombs because the game hasn't started yet "), true);
                return InteractionResultHolder.pass(itemstack);
            }
            BaseTeam team = baseMap.getMapTeams().getTeamByPlayer(player);
            if(team == null) {
                player.displayClientMessage(Component.literal("You can't place bombs because you haven't joined the team "), true);
                return InteractionResultHolder.pass(itemstack);
            }
            boolean canPlace = map.checkCanPlacingBombs(team.getName());
            boolean isInBombArea = map.checkPlayerIsInBombArea(player);
            if(canPlace && isInBombArea){
                player.startUsingItem(hand);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
                return InteractionResultHolder.consume(itemstack);
            }else{
                if(!canPlace) {
                    player.displayClientMessage(Component.literal("You cannot place bombs!"), true);
                }else{
                    if(map.getBombAreaData().isEmpty()) {
                        player.displayClientMessage(Component.literal("You are not define the designated area!"), true);
                    }else{
                        player.displayClientMessage(Component.literal("You are not in the designated area!"), true);
                    }
                }
                return InteractionResultHolder.pass(itemstack);
            }
        }else{
            player.displayClientMessage(Component.literal("You can't place bombs because you haven't joined blast mode map "), true);
            return InteractionResultHolder.pass(itemstack);
        }
    }

    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack pStack, @NotNull Level pLevel, @NotNull LivingEntity pLivingEntity) {
        if (pLivingEntity instanceof ServerPlayer player) {
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map instanceof BlastModeMap blastModeMap){
                CompositionC4Entity entityC4 = new CompositionC4Entity(pLevel,player.getX(), player.getY(), player.getZ(),player,blastModeMap);
                pLevel.addFreshEntity(entityC4);
            }else{
                return pStack;
            }
            if (player.isCreative()) return pStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack pStack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack itemstack) {
        return 80;
    }


}