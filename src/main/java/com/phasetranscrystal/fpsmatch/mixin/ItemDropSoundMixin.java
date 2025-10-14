package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ItemEntity.class)
public abstract class ItemDropSoundMixin extends Entity {
    public ItemDropSoundMixin(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Shadow
    public abstract ItemStack getItem();
    @Unique
    private boolean fPSMatch$hasPlayedLandSound = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if(this.level().isClientSide) return;

        if (this.onGround() && !fPSMatch$hasPlayedLandSound) {
            fpsMatch$playLandSound(this.getItem());
            fPSMatch$hasPlayedLandSound = true;
        }else{
            if(!this.onGround()) fPSMatch$hasPlayedLandSound = false;
        }
    }

    @Unique
    private void fpsMatch$playLandSound(ItemStack itemStack) {
        if (!this.level().isClientSide) {
            if (itemStack.getItem() instanceof IGun iGun) {
                Optional<GunTabType> type = FPSMUtil.getGunTypeByGunId(iGun.getGunId(itemStack));
                type.ifPresent(t -> {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            FPSMSoundRegister.getGunDropSound(t),
                            this.getSoundSource(), 0.3F, 0.8F + this.random.nextFloat() * 0.4F);
                });
            } else {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        FPSMSoundRegister.getItemDropSound(itemStack.getItem()),
                        this.getSoundSource(), 0.3F, 0.8F + this.random.nextFloat() * 0.4F);
            }
        }
    }
}
