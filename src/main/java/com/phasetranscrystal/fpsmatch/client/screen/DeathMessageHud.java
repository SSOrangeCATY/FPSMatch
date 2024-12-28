package com.phasetranscrystal.fpsmatch.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.Config;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.DeathMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.LinkedList;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DeathMessageHud implements IGuiOverlay {

    public static final DeathMessageHud INSTANCE = new DeathMessageHud();
    private final LinkedList<MessageData> messageQueue = new LinkedList<>();
    public final Minecraft minecraft;

    public DeathMessageHud() {
        minecraft = Minecraft.getInstance();
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
        Iterator<MessageData> iterator = messageQueue.iterator();
        PoseStack poseStack = guiGraphics.pose();
        Font font = Minecraft.getInstance().font;
        int mX = 999999;
        while (iterator.hasNext()){
            poseStack.pushPose();
            MessageData messageData = iterator.next();
            DeathMessage message = messageData.message;
            if (currentTime - messageData.displayStartTime >= Config.client.messageShowTime.get() * 1000) {
                iterator.remove();
                continue;
            }
            Component fullText = message.getFullText();
            int x = getHudPositionXOffset(this.getTextWidth(fullText));
            if(mX == 999999){
                mX = x - 3;
            }
            mX = Math.min(x - 3,mX);
            guiGraphics.fillGradient(mX, yOffset, getHudPositionXOffset( -3), yOffset+10, -1072689136, -1072689136);
            guiGraphics.drawString(font,fullText,x, yOffset,0xFFFFFFFF,true);
            yOffset += 10;
            poseStack.popPose();
        }
    }
    public int getTextWidth(Component component) {
        return Minecraft.getInstance().font.width(component);
    }
    public void addKillMessage(DeathMessage message) {
        long currentTime = System.currentTimeMillis();
        if (messageQueue.size() >= Config.client.maxShowCount.get()) {
            messageQueue.removeFirst();
        }

        messageQueue.add(new MessageData(message, currentTime));
    }

    private int calculateAlpha(long currentTime, long displayStartTime, int showTime) {
        long timeElapsed = (currentTime - displayStartTime) / 1000;
        if (timeElapsed > showTime) {
            return 0;
        }
        return (int) (255 - ((timeElapsed / (double) showTime) * 255));
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
}