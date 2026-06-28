package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.other.LivingEntityRenderStateTracker;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, remap = false)
public class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"), remap = false)
    private void tacz$rememberLivingEntity(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        LivingEntityRenderStateTracker.remember(state, entity);
    }
}
