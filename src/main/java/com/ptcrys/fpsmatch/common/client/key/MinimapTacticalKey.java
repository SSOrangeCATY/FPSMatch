package com.ptcrys.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapKeys;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalOpenRequest;
import com.ptcrys.fpsmatch.common.client.net.FPSMClientPacketRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Generic IN_GAME tactical map key. Does not use TACZ InputExtraCheck.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class MinimapTacticalKey {
    public static final KeyMapping KEY = new KeyMapping(
            "key.fpsm.minimap.tactical.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.category.fpsm"
    );

    private static volatile MinimapClientScreens screens;

    private MinimapTacticalKey() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!KEY.consumeClick() && !KEY.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        boolean textInputActive = mc.screen instanceof ChatScreen
                || (mc.screen != null && mc.screen.isPauseScreen() == false && mc.screen.getClass().getName().contains("Editor"));
        boolean inGame = mc.screen == null || !textInputActive;
        boolean capabilityPresent = FPSMClientPacketRegistrar.minimapServices() != null
                && FPSMClientPacketRegistrar.minimapServices()
                .subscriptions().matchHudAvailable();
        MinimapClientScreens current = screens;
        if (current == null || !MinimapKeys.canConsumeOpen(
                inGame, capabilityPresent, textInputActive,
                current.controller().isOpen()
        )) {
            return;
        }
        if (current.controller().isOpen()) {
            current.close();
        } else {
            current.openIfAllowed(new TacticalOpenRequest(
                    true, capabilityPresent, false, false
            ));
        }
    }

    public static void install(MinimapClientScreens screens) {
        MinimapTacticalKey.screens = screens;
    }
}
