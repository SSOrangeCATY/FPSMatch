package com.tacz.guns.client.input;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
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
public class ZoomKey {
    public static final KeyMapping ZOOM_KEY = new KeyMapping("key.tacz.zoom.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            TaczKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onZoomKeyPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS && TaczKeyMappings.matches(ZOOM_KEY, event)) {
            doZoomLogic();
        }
    }

    @SubscribeEvent
    public static void onZoomMousePress(InputEvent.MouseButton.Post event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS && TaczKeyMappings.matchesMouse(ZOOM_KEY, event)) {
            doZoomLogic();
        }
    }

    public static boolean onZoomControllerPress(boolean isPress) {
        if (isInGame() && isPress) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return false;
            }
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (operator.isAim()) {
                NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerZoom());
                return true;
            }
        }
        return false;
    }

    private static void doZoomLogic() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator.isAim()) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerZoom());
        }
    }
}
