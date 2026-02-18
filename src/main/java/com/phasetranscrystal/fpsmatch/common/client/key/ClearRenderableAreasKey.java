package com.phasetranscrystal.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClearRenderableAreasKey {
    public static final KeyMapping KEY = new KeyMapping("key.fpsm.clear_renderable_areas.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "key.category.fpsm");

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        boolean isInGame = isInGame();
        if (isInGame && KEY.isDown()) {
            FPSMClient.getGlobalData().getDebugData().clearAreas();
        }
    }
}
