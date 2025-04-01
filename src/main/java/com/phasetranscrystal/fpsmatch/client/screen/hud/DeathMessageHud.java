package com.phasetranscrystal.fpsmatch.client.screen.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.FPSMConfig;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.DeathMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DeathMessageHud implements IGuiOverlay {

    public static final DeathMessageHud INSTANCE = new DeathMessageHud();
    private final Object queueLock = new Object();
    private final LinkedList<MessageData> messageQueue = new LinkedList<>();
    public final Minecraft minecraft;
    private final Map<String, ResourceLocation> specialKillIcons = new HashMap<>();
    public DeathMessageHud() {
        minecraft = Minecraft.getInstance();
        
        // 注册特殊击杀图标
        registerSpecialKillIcon("headshot", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/headshot.png"));
        registerSpecialKillIcon("throw_wall", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/throw_wall.png"));
        registerSpecialKillIcon("throw_smoke", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/throw_smoke.png"));
        registerSpecialKillIcon("explode", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/explode.png"));
        registerSpecialKillIcon("suicide", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/suicide.png"));
        registerSpecialKillIcon("fire", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/fire.png"));
        registerSpecialKillIcon("blindness", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/blindness.png"));
        registerSpecialKillIcon("no_zoom", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/no_zoom.png"));
        registerSpecialKillIcon("ct_incendiary_grenade", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/ct_incendiary_grenade.png"));
        registerSpecialKillIcon("grenade", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/grenade.png"));
        registerSpecialKillIcon("t_incendiary_grenade", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/t_incendiary_grenade.png"));
        registerSpecialKillIcon("flash_bomb", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/flash_bomb.png"));
        registerSpecialKillIcon("smoke_shell", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/smoke_shell.png"));
        registerSpecialKillIcon("hand", new ResourceLocation("fpsmatch", "textures/ui/cs/icon/hand.png"));
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (FPSMConfig.client.hudEnabled.get() && !messageQueue.isEmpty()) {
            if (minecraft.player != null) {
                renderKillTips(gui,guiGraphics,partialTick,screenWidth,screenHeight);
            }
        }
    }

    private void renderKillTips(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        long currentTime = System.currentTimeMillis();
        int yOffset = getHudPositionYOffset();

        synchronized(queueLock) {
            // 移除过期消息
            messageQueue.removeIf(messageData ->
                    currentTime - messageData.displayStartTime >= FPSMConfig.client.messageShowTime.get() * 1000);

            // 渲染剩余消息
            for (MessageData messageData : messageQueue) {
                DeathMessage message = messageData.message;

                // 计算X坐标
                int width = calculateMessageWidth(message);
                int x = getHudPositionXOffset(width);

                // 渲染消息
                renderKillMessage(guiGraphics, message, x, yOffset);

                // 更新Y偏移
                yOffset += 14;
            }
        }
    }

    public void addKillMessage(DeathMessage message) {
        synchronized(queueLock) {
            long currentTime = System.currentTimeMillis();
            
            // 移除过期消息
            messageQueue.removeIf(messageData -> 
                currentTime - messageData.displayStartTime >= FPSMConfig.client.messageShowTime.get() * 1000);
            
            // 如果队列已满，移除最旧的消息
            if (messageQueue.size() >= FPSMConfig.client.maxShowCount.get()) {
                messageQueue.removeFirst();
            }
            
            // 添加新消息
            messageQueue.add(new MessageData(message, currentTime));
        }
    }

    private int getHudPositionYOffset() {
        return switch (FPSMConfig.client.hudPosition.get()) {
            case 1, 2 -> 10;
            default -> minecraft.getWindow().getGuiScaledHeight() - 10 * 5;
        };
    }

    private int getHudPositionXOffset(int stringWidth) {
        return switch (FPSMConfig.client.hudPosition.get()) {
            case 2, 4 -> minecraft.getWindow().getGuiScaledWidth() - 10 - stringWidth;
            default -> 10;
        };
    }

    public void registerSpecialKillIcon(String id, ResourceLocation texture) {
        specialKillIcons.put(id, texture);
    }

    private void renderKillMessage(GuiGraphics guiGraphics, DeathMessage message, int x, int y) {
        PoseStack poseStack = guiGraphics.pose();
        Font font = minecraft.font;
        boolean isLocalPlayer = minecraft.player != null &&
                message.getKillerUUID().equals(minecraft.player.getUUID());

        // 背景尺寸计算
        int width = calculateMessageWidth(message);
        int height = 16;
        int bgColor = 0x80000000;

        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        if (isLocalPlayer) {
            guiGraphics.fill(x, y, x + width, y + 1, 0xFFFF0000);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFFF0000);
            guiGraphics.fill(x, y, x + 1, y + height, 0xFFFF0000);
            guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFFF0000);
        }

        int currentX = x + 5;
        int rightPadding = x + width - 5;
        // 致盲图标
        if (message.isBlinded()) {
            renderIcon(guiGraphics, specialKillIcons.get("blindness"), currentX, y + 2, 12, 12);
            currentX += 14; // 图标12px + 间距2px
        }

        // 击杀者名字
        guiGraphics.drawString(font, message.getKiller(), currentX, y + 4, -1, true);
        currentX += font.width(message.getKiller()) + 2; // 间距调整为2px

        // 武器图标
        ResourceLocation weaponIcon = message.getWeaponIcon();
        if (weaponIcon != null) {
            poseStack.pushPose();
            float scale = 14.0f / 44.0f;
            float weaponWidth = 117 * scale;

            poseStack.translate(currentX, y + (16 - 14) / 2f, 0);
            poseStack.scale(scale, scale, 1.0f);
            renderIcon(guiGraphics, weaponIcon, 0, 0, 117, 44);
            poseStack.popPose();

            currentX += (int)weaponWidth + 2; // 间距调整为2px
        }

        // 特殊击杀图标（统一处理）
        if (!message.getArg().isEmpty() && specialKillIcons.containsKey(message.getArg())) {
            renderIcon(guiGraphics, specialKillIcons.get(message.getArg()), currentX, y + 2, 12, 12);
            currentX += 14;
        }

        // 其他图标（爆头/穿烟等）
        if (message.isHeadShot()) currentX = renderConditionalIcon(guiGraphics, "headshot", currentX, y);
        if (message.isThroughSmoke()) currentX = renderConditionalIcon(guiGraphics, "throw_smoke", currentX, y);
        if (message.isThroughWall()) currentX = renderConditionalIcon(guiGraphics, "throw_wall", currentX, y);
        if (message.isNoScope()) currentX = renderConditionalIcon(guiGraphics, "no_zoom", currentX, y);

        // 被击杀者名字（自动右对齐）
        int deadNameWidth = font.width(message.getDead());
        currentX = Math.min(currentX, rightPadding - deadNameWidth);
        guiGraphics.drawString(font, message.getDead(), currentX, y + 4, -1, true);
    }
    private int renderConditionalIcon(GuiGraphics guiGraphics, String iconKey, int currentX, int y) {
        renderIcon(guiGraphics, specialKillIcons.get(iconKey), currentX, y + 2, 12, 12);
        return currentX + 14; // 统一图标间距
    }
    
    private void renderIcon(GuiGraphics guiGraphics, ResourceLocation icon, int x, int y, int width, int height) {
        guiGraphics.blit(icon, x, y, 0, 0, width, height, width, height);
    }

    private int calculateMessageWidth(DeathMessage message) {
        int width = 10; // 初始边距5px*2

        // 致盲图标
        if (message.isBlinded()) width += 14;

        // 击杀者名字 + 间距
        width += minecraft.font.width(message.getKiller()) + 2;

        // 武器图标
        if (message.getWeaponIcon() != null) {
            width += (int)(117 * (14.0f / 44.0f)) + 2; // 武器宽度 + 间距2px
        }

        // 特殊击杀图标
        if (!message.getArg().isEmpty() && specialKillIcons.containsKey(message.getArg())) {
            width += 14;
        }

        // 其他图标
        if (message.isHeadShot()) width += 14;
        if (message.isThroughSmoke()) width += 14;
        if (message.isThroughWall()) width += 14;
        if (message.isNoScope()) width += 14;

        // 被击杀者名字（不需要额外间距）
        width += minecraft.font.width(message.getDead());

        return width;
    }

    public record MessageData(DeathMessage message, long displayStartTime) {
    }
}