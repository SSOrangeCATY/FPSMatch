package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.common.client.tab.TabRenderer;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.NotNull;

/**
 * Tab计分板屏幕 - 委托给TabRenderer渲染，不涉及LDLib Widget
 */
public class TabScreen extends FPSMWidgetScreen {
    public final TabRenderer tab;
    public final boolean shouldCloseOnEsc;

    public TabScreen(TabRenderer tab, boolean shouldCloseOnEsc) {
        super(Component.empty());
        this.tab = tab;
        this.shouldCloseOnEsc = shouldCloseOnEsc;
    }

    public TabScreen(TabRenderer tab) {
        this(tab, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Scoreboard scoreboard = RenderUtil.getScoreboard();
        tab.render(guiGraphics, this.width, RenderUtil.getPlayerInfos(), scoreboard, scoreboard.getDisplayObjective(0));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.shouldCloseOnEsc;
    }
}