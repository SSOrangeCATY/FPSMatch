package com.phasetranscrystal.fpsmatch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public  class CSGameOverlay implements IGuiOverlay {
    public static int cTWinnerRounds;
    public static int tWinnerRounds;
    public static int pauseTime;
    public static int roundTime;
    public static boolean isDebug;
    public static boolean isStart;
    public static boolean isError;
    public static boolean isPause;
    public static boolean isWaiting;
    public static boolean isWarmTime;
    public static boolean isWaitingWinner;
    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        // Render the values on the screen
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font,"CT Winner Rounds: " + cTWinnerRounds, 10, 10, 0xFFFFFF);
        guiGraphics.drawString(font,"T Winner Rounds: " + tWinnerRounds, 10, 25, 0xFFFFFF);
        guiGraphics.drawString(font,"Pause Time: " + pauseTime, 10, 40, 0xFFFFFF);
        guiGraphics.drawString(font,"Round Time: " + roundTime, 10, 55, 0xFFFFFF);
        guiGraphics.drawString(font,"Debug: " + isDebug, 10, 70, 0xFFFFFF);
        guiGraphics.drawString(font,"Start: " + isStart, 10, 85, 0xFFFFFF);
        guiGraphics.drawString(font,"Error: " + isError, 10, 100, 0xFFFFFF);
        guiGraphics.drawString(font,"Pause: " + isPause, 10, 115, 0xFFFFFF);
        guiGraphics.drawString(font,"Waiting: " + isWaiting, 10, 130, 0xFFFFFF);
        guiGraphics.drawString(font,"Warm Time: " + isWarmTime, 10, 145, 0xFFFFFF);
        guiGraphics.drawString(font,"Waiting Winner: " + isWaitingWinner, 10, 160, 0xFFFFFF);
    }
}
