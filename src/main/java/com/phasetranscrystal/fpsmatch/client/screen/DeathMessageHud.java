package com.phasetranscrystal.fpsmatch.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.Config;
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
        if (Config.client.hudEnabled.get() && !messageQueue.isEmpty()) {
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
                    currentTime - messageData.displayStartTime >= Config.client.messageShowTime.get() * 1000);

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
                currentTime - messageData.displayStartTime >= Config.client.messageShowTime.get() * 1000);
            
            // 如果队列已满，移除最旧的消息
            if (messageQueue.size() >= Config.client.maxShowCount.get()) {
                messageQueue.removeFirst();
            }
            
            // 添加新消息
            messageQueue.add(new MessageData(message, currentTime));
        }
    }

    private int getHudPositionYOffset() {
        return switch (Config.client.hudPosition.get()) {
            case 1, 2 -> 10;
            default -> minecraft.getWindow().getGuiScaledHeight() - 10 * 5;
        };
    }

    private int getHudPositionXOffset(int stringWidth) {
        return switch (Config.client.hudPosition.get()) {
            case 2, 4 -> minecraft.getWindow().getGuiScaledWidth() - 10 - stringWidth;
            default -> 10;
        };
    }

    public record MessageData(DeathMessage message, long displayStartTime) {
    }

    public void registerSpecialKillIcon(String id, ResourceLocation texture) {
        specialKillIcons.put(id, texture);
    }

    private void renderKillMessage(GuiGraphics guiGraphics, DeathMessage message, int x, int y) {
        PoseStack poseStack = guiGraphics.pose();
        Font font = minecraft.font;
        boolean isLocalPlayer = minecraft.player != null && 
            message.getKillerUUID().equals(minecraft.player.getUUID());
        
        // 背景
        int bgColor = 0x80000000; // 半透明黑色背景
        int width = calculateMessageWidth(message);
        int height = 16;
        
        // 渲染背景
        guiGraphics.fill(x, y, x + width, y + height, bgColor);
        
        // 如果是本地玩家，渲染红色边框
        if (isLocalPlayer) {
            guiGraphics.fill(x, y, x + width, y + 1, 0xFFFF0000);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFFF0000);
            guiGraphics.fill(x, y, x + 1, y + height, 0xFFFF0000);
            guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFFF0000);
        }
        
        int currentX = x + 5;
        int rightPadding = x + width - 5; // 右侧边界
        
        // 渲染致盲击杀图标
        if (message.isBlinded()) {
            renderIcon(guiGraphics, specialKillIcons.get("blindness"), currentX, y + 2, 12, 12);
            currentX += 14;
        }
        
        // 渲染击杀者名字
        guiGraphics.drawString(font, message.getKiller(), currentX, y + 4, -1, true);
        currentX += font.width(message.getKiller()) + 4;
        
        // 渲染武器图标
        ResourceLocation weaponIcon = message.getWeaponIcon();
        if (weaponIcon != null) {
            poseStack.pushPose();
            float scale = 14.0f / 44.0f;
            float weaponHeight = 44 * scale;
            float weaponWidth = 117 * scale;
            float yOffset = y + (16 - weaponHeight) / 2;
            
            poseStack.translate(currentX, yOffset, 0);
            poseStack.scale(scale, scale, 1.0f);
            renderIcon(guiGraphics, weaponIcon, 0, 0, 117, 44);
            poseStack.popPose();
            
            currentX += (int)weaponWidth + 4;
        }
        
        // 渲染特殊击杀图标
        if (!message.getArg().isEmpty() && specialKillIcons.containsKey(message.getArg())) {
            renderIcon(guiGraphics, specialKillIcons.get(message.getArg()), currentX, y + 2, 12, 12);
            currentX += 14;
        }
        
        if (message.isHeadShot()) {
            renderIcon(guiGraphics, specialKillIcons.get("headshot"), currentX, y + 2, 12, 12);
            currentX += 14;
        }
        
        if (message.isThroughSmoke()) {
            renderIcon(guiGraphics, specialKillIcons.get("throw_smoke"), currentX, y + 2, 12, 12);
            currentX += 14;
        }
        
        if (message.isThroughWall()) {
            renderIcon(guiGraphics, specialKillIcons.get("throw_wall"), currentX, y + 2, 12, 12);
            currentX += 14;
        }
        
        if (message.isNoScope()) {
            renderIcon(guiGraphics, specialKillIcons.get("no_zoom"), currentX, y + 2, 12, 12);
            currentX += 14;
        }
        
        // 确保被击杀者名字不会超出背景
        int deadNameWidth = font.width(message.getDead());
        if (currentX + deadNameWidth > rightPadding) {
            currentX = rightPadding - deadNameWidth;
        }
        
        // 渲染被击杀者名字
        guiGraphics.drawString(font, message.getDead(), currentX, y + 4, -1, true);
    }
    
    private void renderIcon(GuiGraphics guiGraphics, ResourceLocation icon, int x, int y, int width, int height) {
        guiGraphics.blit(icon, x, y, 0, 0, width, height, width, height);
    }
    
    private int calculateMessageWidth(DeathMessage message) {
        int width = 10; // 左右各5px的内边距
        Font font = minecraft.font;
        
        // 添加致盲图标的宽度
        if (message.isBlinded()) width += 14;
        
        width += font.width(message.getKiller()) + 4;
        
        // 计算武器图标的宽度
        ResourceLocation weaponIcon = message.getWeaponIcon();
        if (weaponIcon != null) {
            // 使用武器图标的实际渲染宽度
            width += (int)(117 * (14.0f / 44.0f)) + 4;
        } else if (!message.getWeapon().isEmpty()) {
            // 如果没有武器图标但有武器物品，使用物品图标的宽度
            width += 14 + 4;
        }
        
        // 特殊击杀图标
        if (!message.getArg().isEmpty() && !message.getArg().equals("hand")) width += 14;
        if (message.isHeadShot()) width += 14;
        if (message.isThroughSmoke()) width += 14;
        if (message.isThroughWall()) width += 14;
        if (message.isNoScope()) width += 14;
        
        width += font.width(message.getDead()) + 4;
        
        return width;
    }
}