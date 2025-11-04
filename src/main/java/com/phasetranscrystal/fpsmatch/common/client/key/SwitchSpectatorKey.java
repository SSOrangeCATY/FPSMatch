package com.phasetranscrystal.fpsmatch.common.client.key;

import com.phasetranscrystal.fpsmatch.common.packet.spec.SwitchSpectateC2SPacket;
import com.phasetranscrystal.fpsmatch.common.spectator.FPSMSpecManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;


public class SwitchSpectatorKey {
    public static final KeyMapping KEY_SPECTATE_PREV = new KeyMapping(
            "key.fpsm.switch_spec_previous.desc", GLFW.GLFW_KEY_A, "key.category.fpsm.spec");
    public static final KeyMapping KEY_SPECTATE_NEXT = new KeyMapping(
            "key.fpsm.switch_spec_next.desc", GLFW.GLFW_KEY_D, "key.category.fpsm.spec");

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            if (KEY_SPECTATE_PREV.consumeClick()) {
                FPSMSpecManager.sendSwitchSpectate(SwitchSpectateC2SPacket.SwitchDirection.PREV);
            } else if (KEY_SPECTATE_NEXT.consumeClick()) {
                FPSMSpecManager.sendSwitchSpectate(SwitchSpectateC2SPacket.SwitchDirection.NEXT);
            }
        }
    }
}
