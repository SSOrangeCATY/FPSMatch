package com.phasetranscrystal.fpsmatch.client.screen;

import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import icyllis.arc3d.core.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;


@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = FPSMatch.MODID)
public class FPSMTabOverlay implements IGuiOverlay {
    public static final float TAB_WINDOW_WIDTH_SCALE = 2;
    public static final float TAB_WINDOW_HEIGHT_SCALE = 1.875F;
    public static final float TAB_WINDOW_PLAYER_BAR_WIDTH_SCALE = 1.237F;
    public static final float TAB_WINDOW_PLAYER_BAR_HEIGHT_SCALE = 1.036F;
    public static final float TAB_WINDOW_LINE_Y = 1.305F;
    public static final float TAB_WINDOW_LINE_X = 1.066F;
    public static final float TAB_WINDOW_LINE_X_END = 1.104F;
    public static final float TAB_WINDOW_PLAYER_BAR_START_T = 5F;
    public static final float TAB_WINDOW_PLAYER_BAR_START_CT = 1.684F;

    public static final float TAB_WINDOW_PLAYER_BAR_Y = 1.684F;


    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
       /* int startX = (int) (screenWidth / 2 - ((screenWidth / TAB_WINDOW_WIDTH_SCALE) / 2));
        int startY = (int) (screenHeight / 2 - ((screenHeight / TAB_WINDOW_HEIGHT_SCALE) / 2));
        int endX = (int) (screenWidth / 2 + ((screenWidth / TAB_WINDOW_WIDTH_SCALE) / 2));
        int endY = (int) (screenHeight / 2 + ((screenHeight / TAB_WINDOW_HEIGHT_SCALE) / 2));
        guiGraphics.fillGradient(startX, startY, endX, endY, -1072689136, -1072689136);
        int tabWindowWidth = endX - startX;
        int tabWindowHeight = endY - startY;

        int fixedLineStartX = (int) ((endX + startX) - (tabWindowWidth / TAB_WINDOW_LINE_X)) / 2;
        int fixedLineStartY = (int) ((endY + startY) - (tabWindowHeight / TAB_WINDOW_LINE_Y)) / 2;
        int fixedLineEndX = (int) (fixedLineStartX + (tabWindowWidth / TAB_WINDOW_LINE_X_END));
        guiGraphics.fillGradient(fixedLineStartX, fixedLineStartY, fixedLineEndX, fixedLineStartY + 1, Color.WHITE,Color.WHITE);

        List<PlayerInfo> playerInfos = RenderUtil.getPlayerInfos();
        for (int i = 0 ; i < playerInfos.size() ; i++){
            int playerBarStartY = (int) ((int) startY + (tabWindowHeight - (tabWindowHeight - tabWindowHeight / TAB_WINDOW_PLAYER_BAR_START_T)) / 2);
            int w = (int) ((tabWindowWidth / 1.307));
            int sw = (int) (tabWindowWidth / 9.152);
            int fw =tabWindowWidth+ sw + w;
            guiGraphics.fillGradient((tabWindowWidth + sw )/ 2, playerBarStartY, fw, fixedLineStartY + i * 20 + 1, Color.GREEN,Color.GREEN);
        }*/
    }

    public static void renderPlayerHead(GuiGraphics pGuiGraphics, ResourceLocation pAtlasLocation, int pX, int pY, int pSize, boolean pDrawHat, boolean pUpsideDown){
        PlayerFaceRenderer.draw(pGuiGraphics, pAtlasLocation, pX, pY, pSize, pDrawHat, pUpsideDown);
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            event.setCanceled(true);
        }
    }

}
