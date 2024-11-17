package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import static com.phasetranscrystal.fpsmatch.util.RenderUtil.color;

public class CSGameOverlay implements IGuiOverlay {
    public static final int PAUSE_TIME = 120;
    public static final int WINNER_WAITING_TIME = 8;
    public static final int WARM_UP_TIME = 60;
    public static final int WAITING_TIME = 20;
    public static final int ROUND_TIME_LIMIT = 115;
    public static int textCTWinnerRoundsColor = color(7,128,215);
    public static int textTWinnerRoundsColor = color(253,217,141);
    public static int noColor = color(0,0,0,0);

    public static int textRoundTimeColor = color(255,255,255);
    // 60*24 30*50
    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (ClientData.currentMap.equals("fpsm_none")) return;
        Font font = Minecraft.getInstance().font;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        guiGraphics.pose().pushPose();
        guiGraphics.fillGradient((screenWidth / 2) - 16, 2, (screenWidth / 2) + 16, 15, -1072689136, -804253680);
        guiGraphics.fillGradient(((screenWidth / 2) - 16), 16, (screenWidth / 2) - 1, 35, -1072689136, noColor);
        guiGraphics.fillGradient((screenWidth / 2) + 1, 16, ((screenWidth / 2) + 16), 35, -1072689136, noColor);
        guiGraphics.drawString(font, String.valueOf(ClientData.cTWinnerRounds), (screenWidth / 2) - 9 - (font.width(String.valueOf(ClientData.cTWinnerRounds)) / 2), 19, textCTWinnerRoundsColor,false);
        guiGraphics.drawString(font,String.valueOf(ClientData.tWinnerRounds), (screenWidth / 2) + 8 - (font.width(String.valueOf(ClientData.tWinnerRounds)) / 2), 19, textTWinnerRoundsColor,false);
        String roundTime;
        if(ClientData.roundTime == -1){
            roundTime = "xx:xx";
            textRoundTimeColor = color(240,40,40);
        }else{
            roundTime  = getCSGameTime();
        }
        guiGraphics.drawString(font,roundTime, (screenWidth / 2) - ((font.width(roundTime)) / 2), 5, textRoundTimeColor,false);
        guiGraphics.pose().popPose();
    }

    public static String getCSGameTime(){
        String time;
        if(ClientData.isWaiting){
            time = formatTime(WAITING_TIME,ClientData.pauseTime / 20);
        }else if (ClientData.isWarmTime) {
            time = formatTime(WARM_UP_TIME,ClientData.pauseTime / 20);
        }else if (ClientData.isWaitingWinner){
            time = formatTime(WINNER_WAITING_TIME,ClientData.pauseTime / 20);
        }else if(ClientData.isStart){
            time = formatTime(ROUND_TIME_LIMIT,ClientData.roundTime / 20);
        }else{
            time = "00:00";
        }
        return time;
    }

    /**
     * 将总秒数和过去秒数转换为分钟和秒的字符串表示。
     *
     * @param totalSeconds 总秒数
     * @param passedSeconds 过去秒数
     * @return 格式化的时间字符串，如 "01:00"
     */
    public static String formatTime(int totalSeconds, int passedSeconds) {
        // 计算剩余的总秒数
        int remainingSeconds = totalSeconds - passedSeconds;

        // 计算剩余的分钟和秒
        int remainingMinutes = remainingSeconds / 60;
        int remainingSecondsPart = remainingSeconds % 60;

        if(remainingMinutes == 0 && remainingSecondsPart <= 10){
            textRoundTimeColor = color(240,40,40);
        }else {
            textRoundTimeColor = color(255,255,255);
        }

        String minutesPart = String.format("%02d", remainingMinutes);
        String secondsPart = String.format("%02d", remainingSecondsPart);

        return minutesPart + ":" + secondsPart;
    }

}
