package com.tacz.guns.client.input;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@EventBusSubscriber(value = Dist.CLIENT)
public class InspectKey {
    public static final KeyMapping INSPECT_KEY = new KeyMapping("key.tacz.inspect.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            TaczKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS && TaczKeyMappings.matches(INSPECT_KEY, event)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            IClientPlayerGunOperator.fromLocalPlayer(player).inspect();
        }
    }

    public static boolean onInspectControllerPress(boolean isPress) {
        if (isInGame() && isPress) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return false;
            }
            IClientPlayerGunOperator.fromLocalPlayer(player).inspect();
            return true;
        }
        return false;
    }
}
