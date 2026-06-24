package com.phasetranscrystal.fpsmatch.mixin.compat.spectate.tacz;

import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders spectator first-person hands and bypasses the spectator item-in-hand gate.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class MixinGameRendererSpectatorHands {
    @Redirect(
            method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, target = "Lnet/minecraft/world/level/GameType;SPECTATOR:Lnet/minecraft/world/level/GameType;", remap = false),
            require = 0,
            remap = false
    )
    private GameType fpsmatch$allowSpectatorHands() {
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && SpectatorView.shouldRenderHands()) {
            return GameType.SURVIVAL;
        }
        return GameType.SPECTATOR;
    }
}
