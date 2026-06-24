package com.phasetranscrystal.fpsmatch.common.client.tab;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;

public interface TabRenderer {
    void render(GuiGraphicsExtractor graphics, int windowWidth, List<PlayerInfo> players,Scoreboard scoreboard, Objective objective);
    String getGameType();
} 