package com.tacz.guns.client.input;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.util.MinecraftGuiCompat;
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
public class RefitKey {
    public static final KeyMapping REFIT_KEY = new KeyMapping("key.tacz.refit.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            TaczKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onRefitPress(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && TaczKeyMappings.matches(REFIT_KEY, event)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            if (isInGame()) {
                if (IGun.mainHandHoldGun(player) && MinecraftGuiCompat.screen() == null) {
                    IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
                    if (iGun != null && iGun.hasAttachmentLock(player.getMainHandItem())) {
                        return;
                    }
                    MinecraftGuiCompat.setScreen(new GunRefitScreen());
                }
            } else if (MinecraftGuiCompat.screen() instanceof GunRefitScreen refitScreen) {
                refitScreen.onClose();
            }
        }
    }
}
