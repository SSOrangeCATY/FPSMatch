package com.phasetranscrystal.fpsmatch.common.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.common.client.data.TabData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;

import java.util.*;

import static com.phasetranscrystal.fpsmatch.common.client.FPSMClient.PLAYER_COMPARATOR;
import static com.phasetranscrystal.fpsmatch.util.RenderUtil.color;

public class CSGameOverlay implements IGuiOverlay {
    private static final ResourceLocation GUI_BARS_LOCATION = new ResourceLocation("textures/gui/bars.png");
    public static int textCTWinnerRoundsColor = color(182, 210, 240);
    public static int textTWinnerRoundsColor = color(253,217,141);
    public static int noColor = color(0,0,0,0);
    public static int textRoundTimeColor = color(255,255,255);
    public static final String code = "7355608";

    private static final int[] BG_COLORS = new int[]{
            RenderUtil.color(216,130,44), // Blue
            RenderUtil.color(238,228,75), // Yellow
            RenderUtil.color(66,185,131), // Purple
            RenderUtil.color(7,156,130), // Green
            RenderUtil.color(145,203,234)  // Orange
    };

    private final Map<UUID,Integer> playerColorIndex = new HashMap<>();
    private final Map<UUID,String> cachedName = new HashMap<>();
    private int nextColorIndex = 0;

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
            ctBoxX + (float) boxWidth /2,
            startY + (float) backgroundHeight /2 - 6 * scaleFactor, // 从-2改为-6，向上移动4px
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
            ctBoxX + (float) boxWidth /2,
            startY + (float) backgroundHeight /2 + 2 * scaleFactor,
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
            tBoxX + (float) boxWidth /2,
            startY + (float) backgroundHeight /2 - 6 * scaleFactor, // 从-2改为-6
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
            tBoxX + (float) boxWidth /2,
            startY + (float) backgroundHeight /2 + 2 * scaleFactor,
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
        guiGraphics.pose().translate(centerX, startY + (float) timeBarHeight /2, 0);
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
            centerX - (float) timeAreaWidth /2 - scaleFactor, // 左侧分数栏中心，向左偏移1px
            startY + timeBarHeight + gap + (float) scoreBarHeight /2,
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
            centerX + (float) timeAreaWidth /2 + scaleFactor, // 右侧分数栏中心，向右偏移1px
            startY + timeBarHeight + gap + (float) scoreBarHeight /2,
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
            renderDemolitionProgress(player, guiGraphics,screenWidth,screenHeight);
        }

        this.renderMoneyText(guiGraphics,screenWidth,screenHeight);

        int avatarSize = (int)(24.0F * scaleFactor);
        int avatarGap  = (int)(3 * scaleFactor);
        int offset     = (int)(26.0F * scaleFactor);

        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getCSTeamsPlayerInfo();

        String localTeam = ClientData.currentTeam;

        boolean showInfo = ClientData.isWaiting || ClientData.getLocalTabData().isLiving();

        renderAvatarRow(guiGraphics, teamPlayers.get("ct"),
                ctBoxX - offset, startY, boxWidth,
                avatarSize, avatarGap, true,showInfo,
                localTeam, "ct",scaleFactor);

        renderAvatarRow(guiGraphics, teamPlayers.get("t"),
                tBoxX + offset, startY, boxWidth,
                avatarSize, avatarGap, false,showInfo,
                localTeam, "t",scaleFactor);
    }

    private String getRoundTimeString() {
        if(ClientData.time == -1 && !ClientData.isWaitingWinner) {
            textRoundTimeColor = color(240,40,40);
            return "--:--";
        }
        return getCSGameTime();
    }

    private void renderDemolitionProgress(LocalPlayer player, GuiGraphics guiGraphics,int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        MutableComponent component = Component.empty();
        for (int i = 1; i < 8; i++) {
            boolean flag = getDemolitionProgressTextStyle(i);
            int color = flag ? 5635925 : 16777215;
            component.append(Component.literal(String.valueOf(code.toCharArray()[i - 1]))
                .withStyle(Style.EMPTY.withColor(color).withObfuscated(!flag)));
        }
        float xStart = screenWidth / 2F - ((font.width(component) * 1.5F) / 2F);
        float yStart = screenHeight / 2F + 65 + (font.lineHeight * 1.5F / 2F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xStart, yStart, 0);
        guiGraphics.pose().scale(1.5F,1.5F,0);
        guiGraphics.drawString(font, component, 0, 0, -1,true);
        guiGraphics.pose().popPose();
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

    private int getColorIndexForPlayer(UUID uuid) {
        if (!playerColorIndex.containsKey(uuid)) {
            playerColorIndex.put(uuid, nextColorIndex);
            nextColorIndex = (nextColorIndex + 1) % BG_COLORS.length;
        }
        return playerColorIndex.get(uuid);
    }

    private void renderAvatarRow(GuiGraphics guiGraphics,
                                 List<PlayerInfo> players,
                                 int boxStartX,
                                 int rowY,
                                 int boxWidth,
                                 int avatarSize,
                                 int gap,
                                 boolean leftSide,
                                 boolean showInfo,
                                 String localTeam,
                                 String rowTeam,
                                 float scaleFactor
    )
    {
        boolean isSameTeam = isSameTeam(localTeam, rowTeam);
        boolean fullDrawTeamInfo = isSameTeam && showInfo;
        boolean isCT = rowTeam.equals("ct");
        if (showInfo) {
            rowY += 6;
        }
        Font font = Minecraft.getInstance().font;
        for (int i=0; i<players.size(); i++) {
            PlayerInfo player = players.get(i);
            UUID uuid = player.getProfile().getId();
            int drawX = leftSide
                    ? (boxStartX + boxWidth - avatarSize - 2 - i*(avatarSize+gap))
                    : (boxStartX + 2 + i*(avatarSize+gap));

            // 1) 血量 =>0=死/观战
            float actualRatio = fetchEntityHealthRatio(uuid);

            // 2) 对非本地阵营 => 不显示血条
            float barRatio = (!isSameTeam) ? 0f : actualRatio;

            // 3) 背景框: 每玩家固定颜色
            int colorIndex = getColorIndexForPlayer(uuid);
            int bgColor = BG_COLORS[colorIndex];
            guiGraphics.fill(drawX, rowY, drawX + avatarSize, rowY + avatarSize, bgColor);

            // 4) 灰度头像(dead)
            float r=1f,g=1f,b=1f,a=1f;
            if (actualRatio<=0.001f) {
                r=g=b=0.3f;
            }
            RenderSystem.setShaderColor(r,g,b,a);

            int margin      = 1;
            int avX         = drawX + margin;
            int avY         = rowY + margin;
            int smallAvSize = avatarSize - margin*2;

            PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), avX, avY, smallAvSize);

            RenderSystem.setShaderColor(1f,1f,1f,1f);
            int startY = rowY + avatarSize + margin;
            if (barRatio>0f) {
                int barHeight = !fullDrawTeamInfo ? 2 : 4;
                drawSmoothHealthBar(guiGraphics, barRatio, drawX, startY, drawX+avatarSize,startY + barHeight);
                startY += barHeight + margin;
            }

            int killCount = ClientData.getTabDataByUUID(uuid).roundKills();
            if(killCount > 0){
                drawPlayerKills(guiGraphics,font,killCount,avX + (smallAvSize/2),startY,scaleFactor);
                startY += 5 + margin;
            }

            if(showInfo){
                drawPlayerName(guiGraphics, font, uuid, avX, avY + 1, smallAvSize,avatarSize,isCT,scaleFactor);
            }

            if(fullDrawTeamInfo) {
                drawPlayerMoney(guiGraphics, font, uuid, avX + (smallAvSize/2), startY,scaleFactor);
            }
        }
    }

    private void drawPlayerName(GuiGraphics guiGraphics, Font font, UUID uuid,
                                int avX, int avY, int smallAvSize,int width,boolean isCT,float scale)
    {
        String nameStr = getNameFromUUID(uuid);
        float textScale = 0.8f * scale;
        int xCenter = avX + (smallAvSize/2);
        int nameY = avY - 8;
        int maxWidth = avX+width - 1;
        guiGraphics.fill(avX-1, nameY, maxWidth,nameY+6, -1072689136);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xCenter, nameY - 1, 0);
        guiGraphics.pose().scale(textScale, textScale, 1f);
        int nameWidth = font.width(nameStr);
        if(nameWidth < width) {
            guiGraphics.drawString(font, nameStr, -nameWidth/2, 0, isCT ? textCTWinnerRoundsColor:textTWinnerRoundsColor, false);
        }else{
            StringBuilder modified = new StringBuilder();
            for (char c : nameStr.toCharArray()){
                int fw = font.width(modified.toString());
                if(font.width(modified.toString()) + 2 < width) {
                    modified.append(c);
                }else{
                    modified.append("..");
                    guiGraphics.drawString(font, modified.toString(), -fw/2, 0, isCT ? textCTWinnerRoundsColor:textTWinnerRoundsColor, false);
                    break;
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private String getNameFromUUID(UUID uuid) {
        Player p = Minecraft.getInstance().level.getPlayerByUUID(uuid);
        if (p != null) {
            String name = p.getName().getString();
            cachedName.put(uuid,name);
            return p.getName().getString();
        }
        return cachedName.getOrDefault(uuid,uuid.toString().substring(0,8));
    }

    private void drawPlayerKills(GuiGraphics guiGraphics, Font font,int count,
                                 int centerX, int startY,float scaleFactor){

        float textScale = 0.6f * scaleFactor;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY, 0);
        guiGraphics.pose().scale(textScale, textScale, 1f);

        String str = "\uD83D\uDC80".repeat(Math.max(0, count));
        int w = font.width(str);
        guiGraphics.drawString(font, str, -w/2, 0, 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private void drawPlayerMoney(GuiGraphics guiGraphics, Font font, UUID uuid,
                                 int centerX, int startY ,float scaleFactor)
    {
        int moneyValue = ClientData.playerMoney.getOrDefault(uuid, 0);
        String moneyStr = "$" + moneyValue;

        float textScale = 0.8f * scaleFactor;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY, 0);
        guiGraphics.pose().scale(textScale, textScale, 1f);

        int w = font.width(moneyStr);
        guiGraphics.drawString(font, moneyStr, -w/2, 0, 0xFFFFFFFF, false);

        guiGraphics.pose().popPose();
    }

    private boolean isSameTeam(@Nullable String localTeam, String rowTeam) {
        if (localTeam==null || localTeam.isEmpty()
                || "spectator".equalsIgnoreCase(localTeam)) {
            return true;
        }
        return localTeam.equalsIgnoreCase(rowTeam);
    }

    // 读血量 =>0=dead
    private float fetchEntityHealthRatio(UUID uuid){
        Player p = Minecraft.getInstance().level.getPlayerByUUID(uuid);
        if (p==null || !p.isAlive() || p.isSpectator()) {
            return 0f;
        }
        float hp = p.getHealth();
        float mx = p.getMaxHealth();
        return (mx<=0f)?0f:(hp/mx)*100f;
    }

    // 平滑血条
    private void drawSmoothHealthBar(GuiGraphics gg, float ratio,
                                     int startX, int startY, int endX, int endY)
    {
        int total = endX - startX;
        for (int i = 0; i < total; i++) {
            // 计算当前alpha值（从255到0线性变化）
            float progress = (float)i / total;
            int alpha = 255 - (int)(progress * 255);

            // 确保alpha在0-255范围内
            alpha = Math.max(0, Math.min(255, alpha));

            gg.fill(startX, startY, startX + i, endY,
                    RenderUtil.color(166, 42, 39, alpha));
        }

        int fillW = (int)(total*(ratio/100f));
        gg.fill(startX, startY, startX+fillW, endY, RenderUtil.color(255,255,255));
    }

    public static String getCSGameTime(){
        return formatTime(ClientData.time / 20);
    }

    /**
     * 将总秒数和过去秒数转换为分钟和秒的字符串表示。
     *
     * @param totalSeconds 总秒数
     * @return 格式化的时间字符串，如 "01:00"
     */
    public static String formatTime(int totalSeconds) {
        // 计算剩余的分钟和秒
        int remainingMinutes = totalSeconds / 60;
        int remainingSecondsPart = totalSeconds % 60;

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
