package com.phasetranscrystal.fpsmatch.client.tab;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TabManager {
    private static final TabManager INSTANCE = new TabManager();
    private final Map<String, TabRenderer> renderers = new HashMap<>();

    public static TabManager getInstance() {
        return INSTANCE;
    }

    public void registerRenderer(TabRenderer renderer) {
        renderers.put(renderer.getGameType(), renderer);
    }

    public void render(GuiGraphics graphics, int windowWidth, List<PlayerInfo> players, Scoreboard scoreboard, Objective objective) {        
        TabRenderer renderer = renderers.getOrDefault(ClientData.currentGameType, null);
        if (renderer != null) {
            renderer.render(graphics, windowWidth, players, scoreboard, objective);
        }
    }
} 