package com.phasetranscrystal.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.net.entity.bomb.BombActionC2SPacket;
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
public class DismantleBombKey {
    public static final KeyMapping DISMANTLE_BOMB_KEY = new KeyMapping("key.fpsm.dismantle_bomb.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            "key.category.fpsm");

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        boolean isInGame = isInGame();
        if(isInGame && DISMANTLE_BOMB_KEY.isDown()){
            if (event.getAction() == GLFW.GLFW_PRESS) {
                FPSMatch.INSTANCE.sendToServer(new BombActionC2SPacket(true));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                FPSMatch.INSTANCE.sendToServer(new BombActionC2SPacket(false));
            }
        }
    }

}
