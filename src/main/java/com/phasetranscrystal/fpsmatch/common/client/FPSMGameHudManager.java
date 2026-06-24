package com.phasetranscrystal.fpsmatch.common.client;

import com.google.common.collect.Maps;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.IHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID, value = Dist.CLIENT)
public class FPSMGameHudManager implements GuiLayer {
    public static boolean enable = true;
    public static final FPSMGameHudManager INSTANCE = new FPSMGameHudManager();
    private final Map<String, List<IHudRenderer>> gameHudMap = Maps.newHashMap();

    public FPSMGameHudManager() {
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        String gameType = data.getCurrentGameType();
        boolean isSpectator = data.isSpectator();
        if(enable && INSTANCE.gameHudMap.containsKey(gameType) && !isSpectator){
            INSTANCE.gameHudMap.get(gameType)
                    .forEach(overlay -> overlay.onRenderGuiLayerPre(event));
        }
    }

    public static boolean shouldRender(){
        return enable && FPSMClient.getGlobalData().isInGame();
    }

    public void registerHud(String gameType, IHudRenderer overlay){
        gameHudMap.computeIfAbsent(gameType, k -> new ArrayList<>()).add(overlay);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        String gameType = data.getCurrentGameType();
        boolean isSpectator = data.isSpectator();
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        // 渲染游戏HUD
        if(enable && gameHudMap.containsKey(gameType)){
            gameHudMap.get(gameType)
                    .forEach(overlay ->
                            overlay.render(guiGraphics, deltaTracker, screenWidth, screenHeight, isSpectator));
        }
    }
}
