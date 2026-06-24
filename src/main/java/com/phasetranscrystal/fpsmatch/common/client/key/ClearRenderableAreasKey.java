package com.phasetranscrystal.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ClearRenderableAreasKey {
    public static final KeyMapping KEY = new KeyMapping("key.fpsm.clear_renderable_areas.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            FPSMKeyCategories.FPSM);

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        boolean isInGame = isInGame();
        if (isInGame && KEY.isDown()) {
            FPSMClient.getGlobalData().getDebugData().clearAll();
        }
    }
}
