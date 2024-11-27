package com.phasetranscrystal.fpsmatch.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import icyllis.modernui.mc.forge.MuiForgeApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

public class CustomTabKey {
    public static final KeyMapping CUSTOM_TAB_KEY = new KeyMapping("key.fpsm.custom.tab.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.category.fpsm");
    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS && CUSTOM_TAB_KEY.matches(event.getKey(), event.getScanCode())) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            // 切换自定义Tab
            ClientData.customTab = !ClientData.customTab;
            if(ClientData.customTab){
                player.displayClientMessage(Component.translatable("key.fpsm.custom.tab.on"),true);
            }else{
                player.displayClientMessage(Component.translatable("key.fpsm.custom.tab.off"),true);
            }
        }
    }
}
