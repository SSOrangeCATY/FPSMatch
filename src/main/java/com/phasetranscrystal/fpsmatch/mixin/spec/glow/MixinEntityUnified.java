package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorGlowManager;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntityUnified {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return;
        }
        if(!FPSMConfig.Server.disableDefaultGlow.get()) return;

        Entity self = (Entity) (Object) this;

        if (self instanceof LivingEntity living) {
            // 允许SpectatorGlowManager控制的发光，以及带有发光效果的实体（队友透视等）
            if (SpectatorGlowManager.shouldGlow(living) || living.hasEffect(MobEffects.GLOWING)) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
            cir.cancel();
        } else {
            // 允许带有发光标签的物品实体（如掉落的C4）保持发光
            if (self instanceof ItemEntity itemEntity && itemEntity.hasGlowingTag()) {
                cir.setReturnValue(true);
            } else if (localPlayer.isSpectator()) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
            cir.cancel();
        }
    }
}
