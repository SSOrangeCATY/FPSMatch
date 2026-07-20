package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapDetailChildScreen;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Shared lifecycle for LDLib2 map-room child pages. */
public abstract class Ldlib2MapChildScreen extends ModularUIScreen implements FPSMMapDetailChildScreen {
    protected final Screen parent;
    protected MapRoomDetail detail;

    protected Ldlib2MapChildScreen(ModularUI ui, Component title, MapRoomDetail detail, Screen parent) {
        super(ui, title);
        this.detail = Objects.requireNonNull(detail, "detail");
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = Objects.requireNonNull(detail, "detail");
        onDetailApplied();
    }

    protected void onDetailApplied() {
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        modularUI.onRemoved();
        super.removed();
    }
}
