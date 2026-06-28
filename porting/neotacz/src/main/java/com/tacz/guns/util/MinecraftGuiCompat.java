package com.tacz.guns.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

public final class MinecraftGuiCompat {
    private MinecraftGuiCompat() {
    }

    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static Overlay overlay() {
        return Minecraft.getInstance().gui.overlay();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }
}
