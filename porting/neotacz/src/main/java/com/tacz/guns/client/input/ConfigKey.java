package com.tacz.guns.client.input;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@EventBusSubscriber(value = Dist.CLIENT)
public class ConfigKey {
    private static final String CLOTH_CONFIG_URL = "https://www.curseforge.com/minecraft/mc-mods/cloth-config";
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping("key.tacz.open_config.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            TaczKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onOpenConfig(InputEvent.Key event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS
                && TaczKeyMappings.matches(OPEN_CONFIG_KEY, event)
                && KeyModifier.getActiveModifiers().contains(OPEN_CONFIG_KEY.getKeyModifier())) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!ModList.get().isLoaded(CompatRegistry.CLOTH_CONFIG)) {
                ClickEvent clickEvent = new ClickEvent.OpenUrl(URI.create(CLOTH_CONFIG_URL));
                HoverEvent hoverEvent = new HoverEvent.ShowText(Component.translatable("gui.tacz.cloth_config_warning.download"));
                MutableComponent component = Component.translatable("gui.tacz.cloth_config_warning.tips").withStyle(style ->
                        style.applyFormat(ChatFormatting.BLUE).applyFormat(ChatFormatting.UNDERLINE).withClickEvent(clickEvent).withHoverEvent(hoverEvent));
                player.sendSystemMessage(component);
            } else {
                MinecraftGuiCompat.setScreen(MenuIntegration.getConfigScreen(null));
            }
        }
    }
}
