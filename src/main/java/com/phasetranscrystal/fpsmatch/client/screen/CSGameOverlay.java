package com.phasetranscrystal.fpsmatch.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import static com.phasetranscrystal.fpsmatch.util.RenderUtil.color;

public class CSGameOverlay implements IGuiOverlay {
    private static final ResourceLocation GUI_BARS_LOCATION = new ResourceLocation("textures/gui/bars.png");
    public static final int PAUSE_TIME = 60;
    public static final int WINNER_WAITING_TIME = 8;
    public static final int WARM_UP_TIME = 60;
    public static final int WAITING_TIME = 15;
    public static final int ROUND_TIME_LIMIT = 115;
    public static int textCTWinnerRoundsColor = color(182, 210, 240);
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
        
        // 计算缩放因子 (以855x480为基准)
        float scaleFactor = Math.min(screenWidth / 855.0f, screenHeight / 480.0f);
        
        int centerX = screenWidth / 2;
        int startY = (int)(2 * scaleFactor);
        int backgroundHeight = (int)(35 * scaleFactor);
        int timeBarHeight = (int)(13 * scaleFactor);
        int scoreBarHeight = (int)(19 * scaleFactor);
        int boxWidth = (int)(24 * scaleFactor);
        
        // 计算各种间距
        int gap = (int)(2 * scaleFactor); // 统一的2px间距
        int timeAreaWidth = (int)(20 * scaleFactor); // 16 * 1.25 = 20
        
        // 计算存活栏位置
        int ctBoxX = centerX - timeAreaWidth - gap - boxWidth; // 左侧存活栏
        int tBoxX = centerX + timeAreaWidth + gap; // 右侧存活栏
        
        // 渲染中间时间区域背景 (扩大1.25倍)
        guiGraphics.fillGradient(centerX - timeAreaWidth, startY, centerX + timeAreaWidth, startY + timeBarHeight, -1072689136, -804253680);
        
        // 分数栏背景
        guiGraphics.fillGradient(centerX - timeAreaWidth, startY + timeBarHeight + gap, // 只间隔2px
                               centerX - gap/2, startY + backgroundHeight, -1072689136, noColor);
        guiGraphics.fillGradient(centerX + gap/2, startY + timeBarHeight + gap,
                               centerX + timeAreaWidth, startY + backgroundHeight, -1072689136, noColor);

        // 渲染CT存活信息（左侧）
        int ctLivingCount = ClientData.getLivingWithTeam("ct");
        String ctLivingStr = String.valueOf(ctLivingCount);
        
        // CT背景渐变
        int gradientStartY = (int)(startY + timeBarHeight + scaleFactor);
        // 上半部分
        guiGraphics.fillGradient(
            ctBoxX, 
            startY,
            ctBoxX + boxWidth, 
            startY + timeBarHeight + (int)scaleFactor, // 增加1px高度
            -1072689136, 
            -1072689136
        );
        // 下半部分渐变
        guiGraphics.fillGradient(
            ctBoxX, 
            gradientStartY,
            ctBoxX + boxWidth, 
            startY + backgroundHeight, 
            -1072689136, 
            noColor
        );
        
        // CT存活数字
        guiGraphics.pose().pushPose();
        float numberScale = scaleFactor * 1.5f;
        guiGraphics.pose().translate(
            ctBoxX + boxWidth/2,
            startY + backgroundHeight/2 - 6 * scaleFactor, // 从-2改为-6，向上移动4px
            0
        );
        guiGraphics.pose().scale(numberScale, numberScale, 1.0f);
        int ctNumberWidth = font.width(ctLivingStr);
        guiGraphics.drawString(font, ctLivingStr,
            -ctNumberWidth/2,
            -4,
            textCTWinnerRoundsColor,
            false);
        guiGraphics.pose().popPose();
        
        // CT "存活" 文字
        float smallScale = numberScale * 0.5f; // 恢复为数字大小的一半
        String livingText = "存活";
        int smallTextWidth = font.width(livingText);
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
            ctBoxX + boxWidth/2,
            startY + backgroundHeight/2 + 2 * scaleFactor,
            0
        );
        guiGraphics.pose().scale(smallScale, smallScale, 1.0f); // 使用smallScale
        guiGraphics.drawString(font, livingText,
            -smallTextWidth/2, // 使用smallTextWidth
            0,
            textCTWinnerRoundsColor,
            false);
        guiGraphics.pose().popPose();

        // 渲染T存活信息（右侧）
        int tLivingCount = ClientData.getLivingWithTeam("t");
        String tLivingStr = String.valueOf(tLivingCount);
        
        // T背景渐变
        // 上半部分
        guiGraphics.fillGradient(
            tBoxX, 
            startY,
            tBoxX + boxWidth, 
            startY + timeBarHeight + (int)scaleFactor, // 增加1px高度
            -1072689136, 
            -1072689136
        );
        // 下半部分渐变
        guiGraphics.fillGradient(
            tBoxX, 
            gradientStartY,
            tBoxX + boxWidth, 
            startY + backgroundHeight, 
            -1072689136, 
            noColor
        );
        
        // T存活数字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
            tBoxX + boxWidth/2,
            startY + backgroundHeight/2 - 6 * scaleFactor, // 从-2改为-6
            0
        );
        guiGraphics.pose().scale(numberScale, numberScale, 1.0f);
        int tNumberWidth = font.width(tLivingStr);
        guiGraphics.drawString(font, tLivingStr,
            -tNumberWidth/2,
            -4,
            textTWinnerRoundsColor,
            false);
        guiGraphics.pose().popPose();
        
        // T "存活" 文字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
            tBoxX + boxWidth/2,
            startY + backgroundHeight/2 + 2 * scaleFactor,
            0
        );
        guiGraphics.pose().scale(smallScale, smallScale, 1.0f); // 使用smallScale
        guiGraphics.drawString(font, livingText,
            -smallTextWidth/2, // 使用smallTextWidth
            0,
            textTWinnerRoundsColor,
            false);
        guiGraphics.pose().popPose();

        // 渲染时间
        String roundTime = getRoundTimeString();
        float timeScale = scaleFactor * 1.2f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY + timeBarHeight/2, 0);
        guiGraphics.pose().scale(timeScale, timeScale, 1.0f);
        guiGraphics.drawString(font, roundTime, 
            -font.width(roundTime) / 2,
            -4,
            textRoundTimeColor,
            false);
        guiGraphics.pose().popPose();

        // 渲染比分
        float scoreScale = scaleFactor * 1.2f;
        
        // CT比分
        String ctScore = String.valueOf(ClientData.cTWinnerRounds);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
            centerX - timeAreaWidth/2 - scaleFactor, // 左侧分数栏中心，向左偏移1px
            startY + timeBarHeight + gap + scoreBarHeight/2,
            0
        );
        guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
        int ctScoreWidth = font.width(ctScore);
        guiGraphics.drawString(font, ctScore,
            -ctScoreWidth/2,
            -font.lineHeight/2,
            textCTWinnerRoundsColor,
            false);
        guiGraphics.pose().popPose();
        
        // T比分
        String tScore = String.valueOf(ClientData.tWinnerRounds);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
            centerX + timeAreaWidth/2 + scaleFactor, // 右侧分数栏中心，向右偏移1px
            startY + timeBarHeight + gap + scoreBarHeight/2,
            0
        );
        guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
        int tScoreWidth = font.width(tScore);
        guiGraphics.drawString(font, tScore,
            -tScoreWidth/2,
            -font.lineHeight/2,
            textTWinnerRoundsColor,
            false);
        guiGraphics.pose().popPose();

        // 拆弹进度显示
        if(ClientData.dismantleBombProgress > 0) {
            renderDemolitionProgress(player, guiGraphics);
        }
        this.renderMoneyText(guiGraphics,screenWidth,screenHeight);
    }

    private String getRoundTimeString() {
        if(ClientData.roundTime == -1 && !ClientData.isWaitingWinner) {
            textRoundTimeColor = color(240,40,40);
            return "--:--";
        }
        return getCSGameTime();
    }

    private void renderDemolitionProgress(LocalPlayer player, GuiGraphics guiGraphics) {
        MutableComponent component = Component.empty();
        for (int i = 1; i < 8; i++) {
            boolean flag = getDemolitionProgressTextStyle(i);
            int color = flag ? 5635925 : 16777215;
            component.append(Component.literal(String.valueOf(code.toCharArray()[i - 1]))
                .withStyle(Style.EMPTY.withColor(color).withObfuscated(!flag)));
        }
        player.displayClientMessage(component, true);
    }

    private void renderMoneyText(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(5,screenHeight - 20,0 );
        guiGraphics.pose().scale(2,2,0);
        guiGraphics.drawString(font, "$ "+ClientData.getMoney(), 0,0, ClientData.currentTeam.equals("ct") ? textCTWinnerRoundsColor : textTWinnerRoundsColor);
        guiGraphics.pose().popPose();
    }

    public static boolean getDemolitionProgressTextStyle(int index){
        float i = (float) index / 7;
        return ClientData.dismantleBombProgress >= i;
    }

    private void drawBar(GuiGraphics pGuiGraphics, int pX, int pY, BossEvent pBossEvent, int pWidth, int p_281636_) {
        pGuiGraphics.blit(GUI_BARS_LOCATION, pX, pY, 0, BossEvent.BossBarColor.GREEN.ordinal() * 5 * 2 + p_281636_, pWidth, 5);
        if (pBossEvent.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
            RenderSystem.enableBlend();
            pGuiGraphics.blit(GUI_BARS_LOCATION, pX, pY, 0, 80 + (pBossEvent.getOverlay().ordinal() - 1) * 5 * 2 + p_281636_, pWidth, 5);
            RenderSystem.disableBlend();
        }
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
