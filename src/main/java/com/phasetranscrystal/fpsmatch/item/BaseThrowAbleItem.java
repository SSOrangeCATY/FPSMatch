package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileEntity;
import com.phasetranscrystal.fpsmatch.core.function.IHolder;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.net.ThrowEntityC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

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
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack pStack) {
        return UseAnim.BOW;
    }

    public void releaseUsing(ItemStack pStack, Level level, LivingEntity pEntityLiving, int pTimeLeft) {
        if(level.isClientSide){
            if (isLeftPressed && isRightPressed) {
                handleThrow(level, ThrowType.MID);
            } else {
                if (!isRightPressed && isLeftPressed) {
                    handleThrow(level, ThrowType.HIGH);
                } else {
                    handleThrow(level, ThrowType.LOW);
                }
            }
        }
    }

    public int getUseDuration(ItemStack pStack) {
        return 72000;
    }

    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null) return;
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
    }

    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        pPlayer.startUsingItem(pHand);
        return InteractionResultHolder.consume(itemstack);
    }

    public void handleThrow(Level level, ThrowType type) {
        if (level.isClientSide) {
            FPSMatch.INSTANCE.sendToServer(new ThrowEntityC2SPacket(type));
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
