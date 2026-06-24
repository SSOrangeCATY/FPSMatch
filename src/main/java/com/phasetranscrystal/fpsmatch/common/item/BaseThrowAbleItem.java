package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.event.FPSMThrowGrenadeEvent;
import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileEntity;
import com.phasetranscrystal.fpsmatch.core.function.IHolder;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.common.packet.entity.ThrowEntityC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public class BaseThrowAbleItem extends Item implements IThrowEntityAble {
    private int tickCount = 0;
    private boolean isLeftPressed = false;
    private boolean isRightPressed = false;
    public final BiFunction<Player,Level,BaseProjectileEntity> factory;
    public final IHolder<SoundEvent> voice;
    public BaseThrowAbleItem(Properties pProperties, BiFunction<Player,Level,BaseProjectileEntity> factory) {
        super(pProperties);
        this.factory = factory;
        this.voice = null;
    }

    public BaseThrowAbleItem(Properties pProperties, BiFunction<Player,Level,BaseProjectileEntity> factory, IHolder<SoundEvent> voice) {
        super(pProperties);
        this.factory = factory;
        this.voice = voice;
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack pStack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public boolean releaseUsing(@NotNull ItemStack pStack, Level level, @NotNull LivingEntity pEntityLiving, int pTimeLeft) {
        if(level.isClientSide()){
            if (isLeftPressed && isRightPressed) {
                handleThrow(level,pEntityLiving,pStack, ThrowType.MID);
            } else {
                if (!isRightPressed && isLeftPressed) {
                    handleThrow(level,pEntityLiving,pStack, ThrowType.HIGH);
                } else {
                    handleThrow(level,pEntityLiving,pStack, ThrowType.LOW);
                }
            }
        }
        return true;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack pStack, @NotNull LivingEntity user) {
        return 72000;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack itemStack, @NotNull ServerLevel level, @NotNull Entity entity, @Nullable EquipmentSlot slot) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        boolean isSelected = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        boolean isLocal = entity.getUUID().equals(player.getUUID());
        if (isLocal && isSelected) {
            boolean currentLeft = minecraft.options.keyAttack.isDown();
            boolean currentRight = minecraft.options.keyUse.isDown();
            if(tickCount == 5){
                isLeftPressed = currentLeft;
                isRightPressed = currentRight;
            }else{
                if (currentRight && !isRightPressed){
                    isRightPressed = true;
                }
                if (currentLeft && !isLeftPressed){
                    isLeftPressed = true;
                }
            }

            if(tickCount > 5){
                tickCount = 0;
            }else{
                tickCount++;
            }
        }
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level pLevel, Player pPlayer, @NotNull InteractionHand pHand) {
        pPlayer.startUsingItem(pHand);
        return InteractionResult.CONSUME;
    }

    public void handleThrow(Level level,LivingEntity entity, ItemStack stack, ThrowType type) {
        if (level.isClientSide()) {
            if(NeoForge.EVENT_BUS.post(new FPSMThrowGrenadeEvent(entity,stack,type)).isCanceled()) return;

            FPSMatch.sendToServer(new ThrowEntityC2SPacket(type));
            this.isLeftPressed = false;
            this.isRightPressed = false;
            this.tickCount = 0;
        }
    }

    @Override
    public BaseProjectileEntity getEntity(Player pPlayer, Level pLevel) {
        return this.factory.apply(pPlayer, pLevel);
    }

    @Override
    public SoundEvent getThrowVoice() {
        return this.voice == null ? IThrowEntityAble.super.getThrowVoice() : this.voice.get();
    }

    public enum ThrowType {
        HIGH(1.5F,1),
        MID(1F,0.75f),
        LOW(0.5f,0.5f);

        private final float velocity;
        private final float inaccuracy;

        ThrowType(float v, float i) {
            this.velocity = v;
            this.inaccuracy = i;
        }

        public float velocity() {
            return velocity;
        }
        public float inaccuracy() {
            return inaccuracy;
        }
    }

}
