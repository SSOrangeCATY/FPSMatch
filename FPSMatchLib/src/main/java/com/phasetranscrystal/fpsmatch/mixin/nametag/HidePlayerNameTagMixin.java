package com.phasetranscrystal.fpsmatch.mixin.nametag;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 永久隐藏玩家名称标签
 */
@Mixin(PlayerRenderer.class)
public abstract class HidePlayerNameTagMixin {
    @Inject(
            method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;" +
                    "Lnet/minecraft/network/chat/Component;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fpsmatch$cancelNametag(AbstractClientPlayer player,
                                       Component        msg,
                                       PoseStack        poseStack,
                                       MultiBufferSource buffer,
                                       int              packedLight,
                                       CallbackInfo     ci) {

        if(FPSMConfig.Server.disableRenderNameTag.get()) ci.cancel();
    }
}