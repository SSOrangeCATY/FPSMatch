package com.phasetranscrystal.fpsmatch.client;

import com.google.common.collect.Maps;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.screen.hud.CSGameHud;
import com.phasetranscrystal.fpsmatch.client.screen.hud.IHudRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE,value = Dist.CLIENT)
public class FPSMGameHudManager implements IGuiOverlay{
    public static final FPSMGameHudManager INSTANCE = new FPSMGameHudManager();
    private final Map<String, List<IHudRenderer>> gameHudMap = Maps.newHashMap();

    public FPSMGameHudManager() {
        // 注册游戏HUD
        this.registerHud("cs", CSGameHud.INSTANCE);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if(INSTANCE.gameHudMap.containsKey(ClientData.currentGameType)){
            INSTANCE.gameHudMap.get(ClientData.currentGameType)
                    .forEach(overlay -> overlay.onRenderGuiOverlayPre(event));
        }
    }

    public void registerHud(String gameType, IHudRenderer overlay){
        gameHudMap.computeIfAbsent(gameType, k -> new ArrayList<>()).add(overlay);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        // 渲染游戏HUD
        if(gameHudMap.containsKey(ClientData.currentGameType)){
            gameHudMap.get(ClientData.currentGameType)
                    .forEach(overlay -> overlay.render(gui, guiGraphics, partialTick, screenWidth, screenHeight));
        }
    }
}
