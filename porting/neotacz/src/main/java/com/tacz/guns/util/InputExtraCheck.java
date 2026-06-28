package com.tacz.guns.util;

import net.minecraft.client.Minecraft;

public final class InputExtraCheck {
    public static boolean isInGame() {
        Minecraft mc = Minecraft.getInstance();
        // 不能是加载界面
        if (MinecraftGuiCompat.overlay() != null) {
            return false;
        }
        // 不能打开任何 GUI
        if (MinecraftGuiCompat.screen() != null) {
            return false;
        }
        // 当前窗口捕获鼠标操作
        if (!mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        // 选择了当前窗口
        return mc.isWindowActive();
    }
}
