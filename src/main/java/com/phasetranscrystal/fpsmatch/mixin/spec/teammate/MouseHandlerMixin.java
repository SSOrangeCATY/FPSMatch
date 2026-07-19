package com.phasetranscrystal.fpsmatch.mixin.spec.teammate;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateMode;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateState;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorCameraController;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorSwitchDirection;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SpectatorSwitchC2SPacket;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void fpsmatch$handleRestrictedClick(long window, int button, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR || mc.screen instanceof ChatScreen || !SpectateState.isRestricted()) return;
        if (action != GLFW.GLFW_PRESS) return;
        if (SpectateState.get() == SpectateMode.TEAMMATE || SpectateState.get() == SpectateMode.ATTACH) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { FPSMatch.sendToServer(new SpectatorSwitchC2SPacket(SpectatorSwitchDirection.PREV)); ci.cancel(); }
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { FPSMatch.sendToServer(new SpectatorSwitchC2SPacket(SpectatorSwitchDirection.NEXT)); ci.cancel(); }
        } else {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void fpsmatch$handleRestrictedMove(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR || mc.screen instanceof ChatScreen || !SpectateState.isRestricted()) return;
        SpectateMode mode = SpectateState.get();
        if (mode == SpectateMode.C4_ORBIT || mode == SpectateMode.DEATH_SPOT) {
            SpectatorCameraController.applyAngles((float) xOffset * 0.15f, (float) yOffset * 0.15f);
            ci.cancel();
        }
    }
}
