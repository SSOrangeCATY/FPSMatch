package com.phasetranscrystal.fpsmatch.client.screen.hud.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public interface KillAnimator {
    void start(); // 启动动画
    void reset(); // 强制停止
    void render(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int centerX, int y); // 渲染逻辑
    boolean isActive(); // 是否在播放中
    void addKill(); // 增加击杀(默认实现圆弧)
}