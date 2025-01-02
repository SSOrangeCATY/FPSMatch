package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
        if(baseMap instanceof BlastModeMap<?> map) {
            if(!baseMap.isStart) {
                player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail.map.notStart"), true);
                return InteractionResultHolder.pass(itemstack);
            }
            BaseTeam team = baseMap.getMapTeams().getTeamByPlayer(player);
            if(team == null) {
                player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail.team.notInTeam"), true);
                return InteractionResultHolder.pass(itemstack);
            }
            // 检查玩家是否在指定区域内, 检查地图是否在爆炸状态中, 检查玩家是否在地上
            boolean canPlace = map.checkCanPlacingBombs(team.getFixedName()) && map.isBlasting() == 0 && player.onGround();
            boolean isInBombArea = map.checkPlayerIsInBombArea(player);
            if(canPlace && isInBombArea){
                player.startUsingItem(hand);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
                return InteractionResultHolder.consume(itemstack);
            }else{
                if(!canPlace) {
                    player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail"), true);
                }else{
                    if(map.getBombAreaData().isEmpty()) {
                        player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail.noArea"), true);
                    }else{
                        player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail.notInArea"), true);
                    }
                }
                return InteractionResultHolder.pass(itemstack);
            }
        }else{
            player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail.noMap"), true);
            return InteractionResultHolder.pass(itemstack);
        }
    }

    @Override
    public void onUseTick(@NotNull Level pLevel, @NotNull LivingEntity pLivingEntity, @NotNull ItemStack pStack, int pRemainingUseDuration) {
        if (pLevel.isClientSide && Minecraft.getInstance().player != null && pLivingEntity.getUUID().equals(Minecraft.getInstance().player.getUUID())) {
            Minecraft.getInstance().options.keyUp.setDown(false);
            Minecraft.getInstance().options.keyLeft.setDown(false);
            Minecraft.getInstance().options.keyDown.setDown(false);
            Minecraft.getInstance().options.keyRight.setDown(false);
            Minecraft.getInstance().options.keyJump.setDown(false);
        }
    }


    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack pStack, @NotNull Level pLevel, @NotNull LivingEntity pLivingEntity) {
        if (pLivingEntity instanceof ServerPlayer player) {
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map instanceof BlastModeMap<?> blastModeMap){
                boolean isInBombArea = blastModeMap.checkPlayerIsInBombArea(player);
                if(!isInBombArea) {
                    player.displayClientMessage(Component.translatable("fpsm.item.c4.use.fail.notInArea"), true);
                    return pStack;
                }else{
                    BaseTeam team = map.getMapTeams().getTeamByPlayer(player);
                    if(team != null && map instanceof ShopMap shopMap){
                        team.getPlayerList().forEach((uuid -> {
                            shopMap.addPlayerMoney(uuid,300);
                        }));
                    }
                    CompositionC4Entity entityC4 = new CompositionC4Entity(pLevel,player.getX(), player.getY(), player.getZ(),player,blastModeMap);
                    pLevel.addFreshEntity(entityC4);
                    map.getMapTeams().getJoinedPlayers().forEach(uuid -> {
                        ServerPlayer serverPlayer = (ServerPlayer) pLevel.getPlayerByUUID(uuid);
                        if(serverPlayer != null){
                            serverPlayer.displayClientMessage(Component.translatable("fpsm.item.c4.planted").withStyle(ChatFormatting.RED),true);
                        }
                    });
                    return ItemStack.EMPTY;
                }
            }else{
                return pStack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack pStack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack itemstack) {
        return 80;
    }


}