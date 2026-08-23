package com.ptcrys.fpsmatch.mixin.spec.teammate;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.key.MinimapTacticalKey;
import com.ptcrys.fpsmatch.common.client.spec.SpecKeyHandler;
import com.ptcrys.fpsmatch.common.client.spec.SpectateMode;
import com.ptcrys.fpsmatch.common.client.spec.SpectateState;
import com.ptcrys.fpsmatch.common.client.spec.SpectatorSwitchDirection;
import com.ptcrys.fpsmatch.common.packet.spec.SpectatorSwitchC2SPacket;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restricts in-world keys while restricted spectator is active.
 * Open GUIs and the tactical map key must remain usable.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        if (!SpectateState.isRestricted() || mc.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            return;
        }
        // Let pause menu, map select, chat, inventory, and other screens handle keys.
        if (mc.screen != null) {
            return;
        }
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return;
        }

        boolean allowEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
        boolean allowTeamSwitch = SpecKeyHandler.switchKeyMatches(keyCode, scanCode);
        boolean allowTacticalMap = MinimapTacticalKey.KEY.matches(keyCode, scanCode);

        if (keyCode == GLFW.GLFW_KEY_SPACE
                && action == GLFW.GLFW_PRESS
                && (SpectateState.get() == SpectateMode.TEAMMATE || SpectateState.get() == SpectateMode.ATTACH)) {
            FPSMatch.sendToServer(new SpectatorSwitchC2SPacket(SpectatorSwitchDirection.NEXT));
            ci.cancel();
            return;
        }

        if (!(allowEscape || allowTeamSwitch || allowTacticalMap)) {
            ci.cancel();
        }
    }
}
