package com.phasetranscrystal.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;


@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class CustomHudKey {
    public static final KeyMapping KEY = new KeyMapping("key.fpsm.hud.custom.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            FPSMKeyCategories.FPSM);

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS && KEY.matches(event.getKeyEvent())) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            // 切换自定义Tab
            FPSMGameHudManager.enable = !FPSMGameHudManager.enable;
            if(FPSMGameHudManager.enable){
                player.sendSystemMessage(Component.translatable("key.fpsm.hud.custom.on").withStyle(ChatFormatting.GREEN));
            }else{
                player.sendSystemMessage(Component.translatable("key.fpsm.hud.custom.off").withStyle(ChatFormatting.RED));
            }
        }
    }
}
