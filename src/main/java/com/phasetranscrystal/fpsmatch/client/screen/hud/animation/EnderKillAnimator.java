package com.phasetranscrystal.fpsmatch.client.screen.hud.animation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import java.util.ArrayList;
import java.util.List;

public class EnderKillAnimator implements KillAnimator {
    // 资源常量
    private static final ResourceLocation ENDER_EYE = new ResourceLocation("minecraft", "textures/item/ender_eye.png");
    private static final ResourceLocation ENDER_PEARL = new ResourceLocation("minecraft", "textures/item/ender_pearl.png");

    // 动画参数
    private static final int ANIMATION_DURATION = 3500;
    private static final float ITEM_SCALE = 3f;
    private static final int ARC_SEGMENTS = 24; // 圆弧分段数
    private static final int INNER_RADIUS = 32;
    private static final int BASE_RADIUS = 33;
    private static final int OUTER_RADIUS = 34;

    // 动画状态
    private long startTime = -1;
    private final List<Float> arcRotations = new ArrayList<>();
    private boolean rotationStopped;

    @Override
    public void start() {
        startTime = System.currentTimeMillis();
        arcRotations.add(270f);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.05F,1));
        rotationStopped = false;
    }

    @Override
    public void reset() {
        startTime = -1;
        arcRotations.clear();
    }

    @Override
    public boolean isActive() {
        return startTime != -1 && getProgress() < 1.0f;
    }

    @Override
    public void addKill() {
        startTime = System.currentTimeMillis();

        if (arcRotations.size() < 6){
            arcRotations.add(0F);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, (float) (1.05 + 0.1F * arcRotations.size() - 1),1));
        }else{
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.95F,1));
        }

        for(int i = 0; i < arcRotations.size(); i++){
            int r = 360 / arcRotations.size();
            arcRotations.set(i, (float) (r * i));
        }
        rotationStopped = false;
    }

    @Override
    public void render(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int centerX, int baseY) {
        if (!isActive()) return;

        final int renderY = baseY - 75;
        final float progress = getProgress();


        if (progress > 0 && !isExiting(progress)) {
            renderCirclePhase(guiGraphics, centerX, renderY, progress);
            renderItemPhase(guiGraphics, centerX, renderY, progress);
        }else{
            if (isExiting(progress)) {
                renderExitEffect(guiGraphics, centerX, renderY, progress);
            }
        }
    }

    private void renderItemPhase(GuiGraphics guiGraphics, int centerX, int centerY, float progress) {
        if (progress < 0.05f) {
            renderCenteredItem(guiGraphics, ENDER_PEARL, centerX, centerY, ITEM_SCALE);
        } else if (progress < 0.1f) {
            renderCenteredItem(guiGraphics, ENDER_EYE, centerX, centerY, ITEM_SCALE);
        }else if (progress <= 0.15f) {
            renderCenteredItem(guiGraphics, ENDER_PEARL, centerX, centerY, ITEM_SCALE);
        }else if (progress < 0.17f) {
            renderCenteredItem(guiGraphics, ENDER_EYE, centerX, centerY, ITEM_SCALE);
        } else {
            renderCenteredItem(guiGraphics, ENDER_PEARL, centerX, centerY, ITEM_SCALE);
        }
    }

    private void renderCenteredItem(GuiGraphics guiGraphics, ResourceLocation texture,
                                    int centerX, int centerY, float scale) {
        guiGraphics.pose().pushPose();
        float offset = 8 * scale;
        guiGraphics.pose().translate(
                centerX - offset - 1,
                centerY - offset - 1,
                0
        );
        guiGraphics.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        guiGraphics.blit(texture, 0, 0, 0, 0, 16, 16, 16, 16);
        guiGraphics.pose().popPose();
    }

    private void renderCirclePhase(GuiGraphics guiGraphics, int centerX, int centerY, float progress) {
        // 动态半径计算
        float radiusProgress = Math.min((progress - 0.1f) / 0.15f, 1.0f);
        // 动态半径计算
        int currentInner = (int)(INNER_RADIUS * (0.8f + radiusProgress * 0.1f));
        int currentCenter = (int)(BASE_RADIUS * (0.9f + radiusProgress * 0.1f));
        int currentOuter = (int)(OUTER_RADIUS * (1 + radiusProgress * 0.1f));

        drawSmoothCircle(guiGraphics, centerX, centerY, currentOuter, RenderUtil.color(255,255,255, 80));

        drawArcSegments(guiGraphics, centerX, centerY, currentCenter, progress);

        drawSmoothCircle(guiGraphics, centerX, centerY, currentInner, 0x80FFFFFF);

    }

    private void drawSmoothCircle(GuiGraphics guiGraphics, int x, int y, int radius, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        for (int i = 0; i < 360; i++) {
            double radian = Math.toRadians(i);
            int px = (int) (radius * Math.cos(radian));
            int py = (int) (radius * Math.sin(radian));
            guiGraphics.fill(px-1, py-1, px+1, py+1, color);
        }
        guiGraphics.pose().popPose();
    }

    private void drawArcSegments(GuiGraphics guiGraphics, int centerX, int centerY,
                                 int radius, float progress) {
        float angleStep = 360.0f / arcRotations.size();
        for (int i = 0; i < arcRotations.size(); i++) {
            float currentRotation = arcRotations.get(i);

            // 更新旋转状态
            if (!rotationStopped && arcRotations.size() != 1) {
                currentRotation += 2f;
                if (currentRotation >= arcRotations.size() * angleStep * 2 && i == 1) {
                    rotationStopped = true;
                }
                arcRotations.set(i, currentRotation);
            }else if(arcRotations.size() == 1){
                currentRotation = 270f;
            }

            // 计算圆弧起始和结束角度
            float startAngle = currentRotation - 10;
            float endAngle = currentRotation + 10;

            // 绘制圆弧
            drawArc(guiGraphics, centerX, centerY, radius, startAngle, endAngle,RenderUtil.color(255,255,20, 255));
        }
    }

    private void drawArc(GuiGraphics guiGraphics, int centerX, int centerY,
                         int radius, float startAngle, float endAngle,int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0);

        float angleIncrement = (endAngle - startAngle) / ARC_SEGMENTS;
        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            float angle = startAngle + i * angleIncrement;
            double radian = Math.toRadians(angle);
            int x = (int) (radius * Math.cos(radian));
            int y = (int) (radius * Math.sin(radian));
            guiGraphics.fill(x-1, y-1, x+1, y+1, color);
        }
        guiGraphics.pose().popPose();
    }

    private void renderExitEffect(GuiGraphics guiGraphics, int centerX, int centerY, float progress) {
        float exitProgress = (progress - 0.75f) / 0.25f;
        float innerProgress = exitProgress * 1.1f;
        float centerProgress = exitProgress * 1;
        float outerProgress = exitProgress * 1.5f;
        int innerAlpha = (int)(255 * (0.25 - innerProgress));
        int centerAlpha = (int)(255 * (0.25 - centerProgress));
        int OuterAlpha = (int)(80 * (0.25 - outerProgress));
        int currentInner = (int)(INNER_RADIUS * (1 - innerProgress * 2f));
        int currentCenter = (int)(BASE_RADIUS * (1 + centerProgress * 2f));
        int currentOuter = (int)(OUTER_RADIUS * (1 - outerProgress * 2f));

        // 仅渲染末影之眼
        float scale = ITEM_SCALE * (1 - exitProgress);
        if(scale >= 2.5f){
            renderCenteredItem(guiGraphics, ENDER_PEARL, centerX, centerY, scale);
        }

        // 扩散效果
        if(innerAlpha > 25){
            drawSmoothCircle(guiGraphics, centerX, centerY, currentInner, (innerAlpha << 24) | 0xFFFFFF);
        }
        if(centerAlpha > 25){
            for (float currentRotation : arcRotations) {
                if(arcRotations.size() == 1){
                    currentRotation = 270f;
                }
                currentRotation -= 10f;
                // 计算圆弧起始和结束角度
                float endAngle = currentRotation + 20f;
                // 绘制圆弧
                drawArc(guiGraphics, centerX, centerY, currentCenter, currentRotation, endAngle, RenderUtil.color(255,255,20, centerAlpha));
            }
        }
        if(OuterAlpha > 25){
            drawSmoothCircle(guiGraphics, centerX, centerY, currentOuter, RenderUtil.color(255,255,255, OuterAlpha));
        }
    }

    private float getProgress() {
        return (System.currentTimeMillis() - startTime) / (float)ANIMATION_DURATION;
    }

    private boolean isExiting(float progress) {
        return progress > 0.75f;
    }
}