package com.phasetranscrystal.fpsmatch.mixin.spec.teammate;

import com.phasetranscrystal.fpsmatch.common.client.spec.SpecKeyHandler;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateState;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, remap = false)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, remap = false)
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (!SpectateState.isAttach()) return;
        if (mc.gameMode.getPlayerMode() != GameType.SPECTATOR) return;
        if (mc.gui.screen() instanceof ChatScreen) return;
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return;
        int keyCode = event.key();
        int scanCode = event.scancode();
        boolean allowEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
        boolean allowTeamSwitch = SpecKeyHandler.switchKeyMatches(keyCode, scanCode);

        if (!(allowEscape || allowTeamSwitch) && FPSMConfig.Server.lockSpecKeyHandle.get()) {
            ci.cancel();
        }
    }
}
