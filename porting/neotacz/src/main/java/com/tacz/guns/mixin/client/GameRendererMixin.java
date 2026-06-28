package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererMixin {
    @Unique
    private boolean tacz$renderingLevelBob;
    @Unique
    private float tacz$bobPartialTick;

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, remap = false)
    public void onBobHurt(CameraRenderState cameraState, PoseStack pMatrixStack, CallbackInfo ci) {
        // 取消受伤导致的视角摇晃
        if (Minecraft.getInstance().getCameraEntity() instanceof LocalPlayer player && !player.isDeadOrDying()) {
            if (GunHurtBobTweak.onHurtBobTweak(player, pMatrixStack, tacz$bobPartialTick)) {
                ci.cancel();
                return;
            }
        }
        // 触发其他事件
        boolean cancel;
        if (tacz$renderingLevelBob) {
            cancel = NeoForge.EVENT_BUS.post(new RenderLevelBobEvent.BobHurt()).isCanceled();
        } else {
            cancel = NeoForge.EVENT_BUS.post(new RenderItemInHandBobEvent.BobHurt()).isCanceled();
        }
        if (cancel) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, remap = false)
    public void onBobView(CameraRenderState cameraState, PoseStack pMatrixStack, CallbackInfo ci) {
        boolean cancel;
        if (tacz$renderingLevelBob) {
            cancel = NeoForge.EVENT_BUS.post(new RenderLevelBobEvent.BobView()).isCanceled();
        } else {
            cancel = NeoForge.EVENT_BUS.post(new RenderItemInHandBobEvent.BobView()).isCanceled();
        }
        if (cancel) {
            ci.cancel();
        }
    }

    /**
     * 26.1 将相机 bob 拆进 CameraRenderState；在两个真实入口处记录当前 bob 属于世界还是手部路径。
     */
    @Inject(method = "renderLevel", at = @At("HEAD"), remap = false)
    public void markLevelBob(DeltaTracker deltaTracker, CallbackInfo ci) {
        this.tacz$renderingLevelBob = true;
        this.tacz$bobPartialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), remap = false)
    public void markItemInHandBob(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        this.tacz$renderingLevelBob = false;
        this.tacz$bobPartialTick = deltaPartialTick;
    }
}
