package com.phasetranscrystal.fpsmatch.mixin.spec.teammate;

import com.phasetranscrystal.fpsmatch.common.client.key.SwitchSpectatorKey;
import com.phasetranscrystal.fpsmatch.common.client.spectator.SpectateState;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (!SpectateState.isAttach()) return;
        if (mc.gameMode.getPlayerMode() != GameType.SPECTATOR) return;
        if (mc.screen instanceof ChatScreen) return;
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return;
        boolean allowEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
        boolean allowTeamSwitch =
                SwitchSpectatorKey.KEY_SPECTATE_PREV.matches(keyCode, scanCode) ||
                        SwitchSpectatorKey.KEY_SPECTATE_NEXT.matches(keyCode, scanCode);

        if (!(allowEscape || allowTeamSwitch)) {
            ci.cancel();
        }
    }
}