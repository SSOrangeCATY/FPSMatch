package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import static com.phasetranscrystal.fpsmatch.util.RenderUtil.color;

public class CSGameOverlay implements IGuiOverlay {
    public static final int PAUSE_TIME = 60;
    public static final int WINNER_WAITING_TIME = 8;
    public static final int WARM_UP_TIME = 60;
    public static final int WAITING_TIME = 15;
    public static final int ROUND_TIME_LIMIT = 115;
    public static int textCTWinnerRoundsColor = color(7,128,215);
    public static int textTWinnerRoundsColor = color(253,217,141);
    public static int noColor = color(0,0,0,0);
    public static int textRoundTimeColor = color(255,255,255);
    public static final String code = "7355608";
    // 60*24 30*50
    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (ClientData.currentMap.equals("fpsm_none")) return;
        Font font = Minecraft.getInstance().font;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if(mc.player == null) return;
        guiGraphics.pose().pushPose();
        guiGraphics.fillGradient((screenWidth / 2) - 16, 2, (screenWidth / 2) + 16, 15, -1072689136, -804253680);
        guiGraphics.fillGradient(((screenWidth / 2) - 16), 16, (screenWidth / 2) - 1, 35, -1072689136, noColor);
        guiGraphics.fillGradient((screenWidth / 2) + 1, 16, ((screenWidth / 2) + 16), 35, -1072689136, noColor);

        Component ctLiving = Component.translatable("fpsm.cs.ct.living",ClientData.getLivingWithTeam("ct"));
        int ct_t_w = (screenWidth / 2) - 48 - (font.width(ctLiving) / 2);
        guiGraphics.fillGradient(ct_t_w - 2, 2, (screenWidth / 2) - 48 + font.width(ctLiving) / 2 + 3, 15, -1072689136, -1072689136);
        guiGraphics.drawString(font, ctLiving, ct_t_w, 4, textCTWinnerRoundsColor,false);
        guiGraphics.drawString(font, String.valueOf(ClientData.cTWinnerRounds), (screenWidth / 2) - 8 - (font.width(String.valueOf(ClientData.cTWinnerRounds)) / 2), 19, textCTWinnerRoundsColor,false);

        Component tLiving = Component.translatable("fpsm.cs.t.living",ClientData.getLivingWithTeam("t"));
        int t_t_w = (screenWidth / 2) + 48 - (font.width(tLiving) / 2);
        guiGraphics.fillGradient(t_t_w - 2, 2, (screenWidth / 2) + 48 + font.width(tLiving) / 2 + 3, 15, -1072689136, -1072689136);
        guiGraphics.drawString(font,tLiving, (screenWidth / 2) + 48 - (font.width(tLiving) / 2), 4, textTWinnerRoundsColor,false);
        guiGraphics.drawString(font,String.valueOf(ClientData.tWinnerRounds), (screenWidth / 2) + 8 - (font.width(String.valueOf(ClientData.tWinnerRounds)) / 2), 19, textTWinnerRoundsColor,false);

        String roundTime;
        if(ClientData.roundTime == -1 && !ClientData.isWaitingWinner){
            roundTime = "--:--";
            textRoundTimeColor = color(240,40,40);
        }else{
            roundTime = getCSGameTime();
        }

        guiGraphics.drawString(font,roundTime, (screenWidth / 2) - ((font.width(roundTime)) / 2), 5, textRoundTimeColor,false);
        guiGraphics.pose().popPose();

        if(ClientData.dismantleBombProgress > 0){
            MutableComponent component = Component.empty();
            for (int i = 1; i < 8 ; i++){
                boolean flag = getDemolitionProgressTextStyle(i);
                int color = flag ? 5635925 : 16777215;
                component.append(Component.literal(String.valueOf(code.toCharArray()[i - 1])).withStyle(Style.EMPTY.withColor(color).withObfuscated(!flag)));
            }
            player.displayClientMessage(component,true);
        }
    }

    public static boolean getDemolitionProgressTextStyle(int index){
        float i = (float) index / 7;
        return ClientData.dismantleBombProgress >= i;
    }

    public static String getCSGameTime(){
        String time;
        if(ClientData.isPause){
            time = formatTime(PAUSE_TIME,ClientData.pauseTime / 20);
        }else{
            if(ClientData.isWaiting){
                time = formatTime(WAITING_TIME,ClientData.pauseTime / 20);
            }else if (ClientData.isWarmTime) {
                time = formatTime(WARM_UP_TIME,ClientData.pauseTime / 20);
            }else if (ClientData.isWaitingWinner){
                time = formatTime(WINNER_WAITING_TIME,ClientData.pauseTime / 20);
            } else if(ClientData.isStart){
                time = formatTime(ROUND_TIME_LIMIT,ClientData.roundTime / 20);
            }else{
                time = "00:00";
            }
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
