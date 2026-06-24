package com.phasetranscrystal.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class SwitchPreviousItemKey {
    private static int previous = -1;
    private static int current = 0;
    public static final KeyMapping KEY = new KeyMapping("key.fpsm.switch_previous_item.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            FPSMKeyCategories.FPSM);

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        boolean isInGame = isInGame();
        if (isInGame && KEY.isDown()) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) {
                    return;
                }

                if (previous != -1) {
                    player.getInventory().setSelectedSlot(previous);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null) return;
        if (previous == -1) {
            previous = player.getInventory().getSelectedSlot();
            current = player.getInventory().getSelectedSlot();
        }else{
            if (current != player.getInventory().getSelectedSlot()){
                previous = current;
                current = player.getInventory().getSelectedSlot();
            }
        }
    }


}
