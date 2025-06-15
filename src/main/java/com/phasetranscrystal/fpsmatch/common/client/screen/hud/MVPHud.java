package com.phasetranscrystal.fpsmatch.common.client.screen.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.core.data.MvpReason;
import com.phasetranscrystal.fpsmatch.common.item.FPSMSoundRegister;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public class MVPHud implements IGuiOverlay {
    private static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt((p_253306_) -> {
        return p_253306_.getGameMode() == GameType.SPECTATOR ? 1 : 0;
    }).thenComparing((p_269613_) -> {
        return Optionull.mapOrDefault(p_269613_.getTeam(), PlayerTeam::getName, "");
    }).thenComparing((p_253305_) -> {
        return p_253305_.getProfile().getName();
    }, String::compareToIgnoreCase);
    public static final MVPHud INSTANCE = new MVPHud();
    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final Font font = minecraft.font;
    private static final int ROUND_BANNER_DURATION = 300;
    private static final int MVP_PANEL_DURATION = 500;
    private static final int COLOR_TRANSITION_DURATION = 150;
    private static final int CLOSING_ANIMATION_DURATION = 300;
    private static final float CLOSING_SPEED_FACTOR = 2.0f;
    // 动画状态
    private long roundBannerStartTime = -1;
    private long mvpInfoStartTime = -1;
    private long colorTransitionStartTime = -1;
    private long mvpColorTransitionStartTime = -1;
    private boolean animationPlaying = false;
    private long closeAnimationStartTime = -1;
    private boolean isClosing = false;

    // 玩家信息
    private UUID player;
    private Component currentPlayerName = Component.empty();
    private Component currentTeamName = Component.empty();
    private Component extraInfo1 = Component.empty();
    private Component extraInfo2 = Component.empty();
    private Component mvpReason = Component.empty();

    // 配置参数
    private static final int BASE_WIDTH = 1920;
    private static final int BASE_HEIGHT = 1080;
    private static final float MIN_SCALE = 0.7f;
    private static final int ROUND_BANNER_WIDTH = 400;
    private static final int ROUND_BANNER_HEIGHT = 80;
    private static final int MVP_PANEL_WIDTH = 580;
    private static final int MVP_PANEL_HEIGHT = 90;
    private static final int AVATAR_SIZE = 74;
    private static final int COLOR_BAR_HEIGHT = 20;

    public void triggerAnimation(MvpReason reason) {
        this.player = reason.uuid;
        this.currentTeamName = ((MutableComponent) reason.getTeamName()).append(Component.translatable("cs.game.winner.mvpNameSub"));
        this.currentPlayerName = reason.getPlayerName();
        this.mvpReason = reason.getMvpReason();
        this.extraInfo1 = reason.getExtraInfo1();
        this.extraInfo2 = reason.getExtraInfo2();
        this.roundBannerStartTime = System.currentTimeMillis();
        this.mvpInfoStartTime = -1;
        this.colorTransitionStartTime = -1;
        this.mvpColorTransitionStartTime = -1;
        this.animationPlaying = true;

        //TODO MVP音效先暂时放这里了
        boolean flag = reason.getTeamName().getString().equals("CT");
        if (minecraft.level != null) {
            if (minecraft.player != null) {
                minecraft.level.playLocalSound(minecraft.player.getOnPos().above().above(), flag ? FPSMSoundRegister.voice_ct_win.get() : FPSMSoundRegister.voice_t_win.get(), SoundSource.VOICE, 1.0f, 1.0f,false);
            }
        }
    }


    public long getMvpInfoStartTime() {
        return mvpInfoStartTime;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (isClosing) {
            renderCloseAnimation(guiGraphics, screenWidth, screenHeight);
            return;
        }

        if (!animationPlaying) return;

        long currentTime = System.currentTimeMillis();
        PoseStack pose = guiGraphics.pose();
        float scaleFactor = Math.max((float) screenWidth / BASE_WIDTH, MIN_SCALE);

        // 回合横幅动画
        if (roundBannerStartTime != -1) {
            float bannerProgress = Math.min((currentTime - roundBannerStartTime) / (float)ROUND_BANNER_DURATION, 1f);
            renderRoundVictoryBanner(guiGraphics, pose, bannerProgress, scaleFactor,
                    screenWidth, screenHeight, currentTime);

            if (bannerProgress >= 1f && mvpInfoStartTime == -1) {
                mvpInfoStartTime = currentTime + 500;
                colorTransitionStartTime = currentTime;
            }
        }

        // MVP面板动画
        if (mvpInfoStartTime != -1 && currentTime > mvpInfoStartTime && !this.currentPlayerName.getString().isEmpty()) {
            float mvpProgress = Math.min((currentTime - mvpInfoStartTime) / (float)MVP_PANEL_DURATION, 1f);
            renderMVPInfoPanel(guiGraphics, pose, mvpProgress, scaleFactor, screenWidth, screenHeight, currentTime);
        }
    }

    private void renderRoundVictoryBanner(GuiGraphics guiGraphics, PoseStack pose, float bannerProgress,
                                          float scaleFactor, int screenWidth, int screenHeight, long currentTime) {
        int scaledWidth = (int)(ROUND_BANNER_WIDTH * scaleFactor);
        int scaledHeight = (int)(ROUND_BANNER_HEIGHT * scaleFactor);
        int yPos = (int)(190 * ((float)screenHeight / BASE_HEIGHT));
        int animatedWidth = (int)(scaledWidth * bannerProgress);
        int x = (screenWidth - animatedWidth) / 2;
        int y = yPos;

        // 颜色过渡逻辑
        int bgColor = 0xFFFFFFFF;
        if (colorTransitionStartTime != -1) {
            float colorProgress = Math.min((currentTime - colorTransitionStartTime) / (float)COLOR_TRANSITION_DURATION, 1f);
            bgColor = lerpColor(0xFFFFFFFF, 0xAA000000, colorProgress);
        }

        // 背景面板
        guiGraphics.fill(x, y, x + animatedWidth, y + scaledHeight, bgColor);

        // 队伍颜色条
        int teamColor = 0xFF3366FF;
        int sideBarWidth = (int)(5 * scaleFactor);
        guiGraphics.fill(x, y, x + sideBarWidth, y + scaledHeight, teamColor);
        guiGraphics.fill(x + animatedWidth - sideBarWidth, y, x + animatedWidth, y + scaledHeight, teamColor);

        // 文本内容
        if (bannerProgress >= 1f) {
            Component leftArrow = Component.literal("»");
            Component rightArrow = Component.literal("«");
            final float ARROW_SCALE = 3.0f;
            final float TEXT_SCALE = 3.0f;
            float combinedArrowScale = scaleFactor * ARROW_SCALE;
            float combinedTextScale = scaleFactor * TEXT_SCALE;
            int rightArrowWidth = (int)(font.width(rightArrow) * scaleFactor);
            int rightX = x + animatedWidth - sideBarWidth - (int)(10 * scaleFactor) - rightArrowWidth;
            int leftX = x + sideBarWidth + (int)(10 * scaleFactor);
            // 计算垂直居中位置（新增高度差补偿）
            int textTotalHeight = (int)(font.lineHeight * combinedTextScale);
            int arrowTotalHeight = (int)(font.lineHeight * combinedArrowScale);
            int verticalOffset = (textTotalHeight - arrowTotalHeight) / 2;
            int textY = y + (scaledHeight - textTotalHeight) / 2 + verticalOffset;
            int textColor = 0x00FFFFFF;
            // 文字透明度过渡
            if (colorTransitionStartTime != -1) {
                float alpha = Math.min((currentTime - colorTransitionStartTime) / 250f, 1f);
                textColor = (int)(alpha * 255) << 24 | 0x00FFFFFF;
            }

            // 左侧箭头渲染（应用垂直补偿）
            pose.pushPose();
            pose.scale(combinedArrowScale, combinedArrowScale, 1f);
            guiGraphics.drawString(font, leftArrow,
                    (int)((leftX / scaleFactor) / ARROW_SCALE),
                    (int)((textY / scaleFactor) / ARROW_SCALE) - verticalOffset/2,
                    textColor, false);
            pose.popPose();

            // 右侧箭头渲染（应用垂直补偿）
            pose.pushPose();
            pose.scale(combinedArrowScale, combinedArrowScale, 1f);
            guiGraphics.drawString(font, rightArrow,
                    (int)((rightX / scaleFactor) / ARROW_SCALE),
                    (int)((textY / scaleFactor) / ARROW_SCALE) - verticalOffset/2,
                    textColor, false);
            pose.popPose();

            // 中间文本
            int middleWidth = (int)(font.width(currentTeamName) * combinedTextScale);
            renderScaledText(guiGraphics, pose, currentTeamName,
                    x + (animatedWidth - middleWidth) / 2,
                    textY, textColor, combinedTextScale);
        }
    }

    private void renderMVPInfoPanel(GuiGraphics guiGraphics, PoseStack pose, float progress,
                                    float scaleFactor, int screenWidth, int screenHeight, long currentTime) {
        int scaledPanelWidth = (int)(MVP_PANEL_WIDTH * scaleFactor);
        int scaledPanelHeight = (int)(MVP_PANEL_HEIGHT * scaleFactor);
        int yOffset = (int)((ROUND_BANNER_HEIGHT + 16) * scaleFactor);
        int yPosition = (int)(190 * ((float)screenHeight / BASE_HEIGHT)) + yOffset;

        int animatedWidth = (int) (scaledPanelWidth * progress);
        int x = (screenWidth - animatedWidth) / 2;

        // 背景颜色过渡
        int bgColor = 0xFFFFFFFF;
        if (progress >= 1f) {
            if (mvpColorTransitionStartTime == -1) {
                mvpColorTransitionStartTime = currentTime;
            }
            float colorProgress = Math.min((currentTime - mvpColorTransitionStartTime) / (float)COLOR_TRANSITION_DURATION, 1f);
            bgColor = lerpColor(0xFFFFFFFF, 0xAA000000, colorProgress);
        }

        guiGraphics.fill(x, yPosition, x + animatedWidth, yPosition + scaledPanelHeight, bgColor);

        if(progress < 1) return;

        // 头像
        int avatarX = x + (int)(110 * scaleFactor);
        int avatarY = yPosition + (scaledPanelHeight - (int)(AVATAR_SIZE * scaleFactor)) / 2;
        renderAvatar(guiGraphics, avatarX, avatarY, scaleFactor);

        // 信息区域
        int infoStartX = avatarX + (int)((AVATAR_SIZE + 8) * scaleFactor);

        // MVP原因条
        int reasonWidth = (int)(font.width(mvpReason) * scaleFactor);
        int padding = (int)(8 * scaleFactor);
        int colorBarWidth = reasonWidth + padding * 2;

        guiGraphics.fill(infoStartX, avatarY,
                infoStartX + colorBarWidth,
                avatarY + (int)(COLOR_BAR_HEIGHT * scaleFactor),
                0x773366FF);

        renderScaledText(guiGraphics, pose, mvpReason,
                infoStartX + padding,
                avatarY + (int)(6 * scaleFactor),
                -1, scaleFactor);

        float nameScale = scaleFactor * 2.0f;
        int nameY = avatarY + (int)(COLOR_BAR_HEIGHT * scaleFactor) + (int)(10 * scaleFactor) - 6;
        renderScaledText(guiGraphics, pose, currentPlayerName,
                infoStartX, // 直接使用头像右侧位置
                nameY,      // 已减少2px
                -1, nameScale);

        // 统计数据（放大到原始尺寸并下移2px）
        renderScaledText(guiGraphics, pose, extraInfo1,
                infoStartX,
                nameY + (int)(font.lineHeight * scaleFactor * 1.4f) + 8,
                -1,
                scaleFactor * 1.1f);

        // 新增额外信息渲染（放大到原始尺寸并下移2px）
        int currentY = nameY + (int)(font.lineHeight * nameScale) + (int)(4 * scaleFactor) + 12;
        renderScaledText(guiGraphics, pose, extraInfo2,
                infoStartX,
                currentY,
                0xFFFFFFFF,
                scaleFactor * 1.1f);
    }

    private void renderScaledText(GuiGraphics guiGraphics, PoseStack pose, Component text,
                                  int x, int y, int color, float scale) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private void renderAvatar(GuiGraphics guiGraphics, int x, int y, float scaleFactor) {
        int scaledSize = (int)(AVATAR_SIZE * scaleFactor);
        ResourceLocation avatarTexture = new ResourceLocation("fpsmatch", "textures/ui/avatar.png");
        PlayerInfo info = getPlayerInfoByUUID(this.player);
        if(info != null){
            PlayerFaceRenderer.draw(guiGraphics, info.getSkinLocation(), x, y, scaledSize);
        }else{
            guiGraphics.blit(avatarTexture, x, y, scaledSize, scaledSize, 0, 0, 64, 64, 64, 64);
        }
    }

    private int lerpColor(int startColor, int endColor, float progress) {
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        return ((int)(startA + (endA - startA) * progress) << 24) |
                ((int)(startR + (endR - startR) * progress) << 16) |
                ((int)(startG + (endG - startG) * progress) << 8) |
                (int)(startB + (endB - startB) * progress);
    }

    private void renderCloseAnimation(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        long currentTime = System.currentTimeMillis();
        float progress = Math.min((currentTime - closeAnimationStartTime) / (float)CLOSING_ANIMATION_DURATION, 1f);
        PoseStack pose = guiGraphics.pose();
        float scaleFactor = getCurrentScaleFactor();

        // 颜色过渡（更快变白）
        int bgColor = lerpColor(0xAA000000, 0xFFFFFFFF, Math.min(progress * CLOSING_SPEED_FACTOR, 1f));

        // 计算收缩比例（使用缓动函数让动画更自然）
        float closingRatio = (float)Math.pow(progress, 0.8);

        // 渲染横幅关闭动画
        renderClosingBanner(guiGraphics, screenWidth, screenHeight, closingRatio, bgColor, scaleFactor);

        // 渲染MVP面板关闭动画
        renderClosingPanel(guiGraphics, screenWidth, screenHeight, closingRatio, bgColor, scaleFactor);

        if (progress >= 1f) {
            resetAnimation();
        }
    }

    private void renderClosingBanner(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                     float closingRatio, int color, float scaleFactor) {
        int originalWidth = (int)(ROUND_BANNER_WIDTH * scaleFactor);
        int currentWidth = (int)(originalWidth * (1 - closingRatio));
        int yPos = (int)(190 * ((float)screenHeight / BASE_HEIGHT));
        int centerX = screenWidth / 2;
        // 修正2：对称收缩逻辑
        int leftStart = centerX - currentWidth/2;
        int rightEnd = centerX + currentWidth/2;

        guiGraphics.fill(leftStart, yPos,
                rightEnd,
                yPos + (int)(ROUND_BANNER_HEIGHT * scaleFactor),
                color);
    }

    private void renderClosingPanel(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                    float closingRatio, int color, float scaleFactor) {
        int originalWidth = (int)(MVP_PANEL_WIDTH * scaleFactor);
        int currentWidth = (int)(originalWidth * (1 - closingRatio));
        int bannerY = (int)(190 * ((float)screenHeight / BASE_HEIGHT));
        int panelY = bannerY + (int)((ROUND_BANNER_HEIGHT + 16) * scaleFactor);

        // 计算中心点
        int centerX = screenWidth / 2;
        int leftStart = centerX - currentWidth/2;
        int rightEnd = centerX + currentWidth/2;

        // 整体向中心收缩
        guiGraphics.fill(leftStart, panelY,
                rightEnd,
                panelY + (int)(MVP_PANEL_HEIGHT * scaleFactor),
                color);
    }


    private float getCurrentScaleFactor() {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        return Math.max((float) screenWidth / BASE_WIDTH, MIN_SCALE);
    }

    // 新增手动触发关闭动画方法
    public void triggerCloseAnimation() {
        if (!animationPlaying) return;
        CSGameHud.INSTANCE.stopKillAnim();
        isClosing = true;
        closeAnimationStartTime = System.currentTimeMillis();
    }
    public void resetAnimation() {
        closeAnimationStartTime = -1;
        isClosing = false;
        roundBannerStartTime = -1;
        mvpInfoStartTime = -1;
        colorTransitionStartTime = -1;
        mvpColorTransitionStartTime = -1;
        animationPlaying = false;
        currentPlayerName = Component.empty();
        currentTeamName = Component.empty();
        extraInfo1 = Component.empty();
        extraInfo2 = Component.empty();
        player = null;
    }
    public PlayerInfo getPlayerInfoByUUID(UUID uuid) {
        Optional<PlayerInfo> playerInfo = minecraft.player.connection.getListedOnlinePlayers().stream().filter((playerInfo1 -> playerInfo1.getProfile().getId().equals(uuid))).findFirst();
        return playerInfo.orElse(null);
    }
}