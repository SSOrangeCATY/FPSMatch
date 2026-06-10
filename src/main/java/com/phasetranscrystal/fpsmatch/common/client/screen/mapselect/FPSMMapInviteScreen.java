package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FPSMMapInviteScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;

    public FPSMMapInviteScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.invite.title"));
        this.detail = detail;
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        int left = width / 2 - PANEL_WIDTH / 2;
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.availableInviteTargets().size(), visibleRows()); i++) {
            MapRoomPlayerInfo player = detail.availableInviteTargets().get(i);
            addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.invite"), button -> FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.INVITE, detail.summary().gameType(), detail.summary().mapName(), player.uuid())))
                    .bounds(left + 260, y + 2, 78, 20)
                    .build());
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 52, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 48, 0xFFB8D4E3);
        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = Math.min(height - 84, LIST_TOP + visibleRows() * ROW_HEIGHT);
        graphics.fill(left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6, 0x77000000);
        if (detail.availableInviteTargets().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.invite.empty"), width / 2, LIST_TOP + 32, 0xFFAAAAAA);
        }
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.availableInviteTargets().size(), visibleRows()); i++) {
            MapRoomPlayerInfo player = detail.availableInviteTargets().get(i);
            graphics.drawString(font, Component.literal(player.name()), left, y + 8, 0xFFE6F2FF, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.online"), left + 160, y + 8, 0xFF74E084, false);
            y += ROW_HEIGHT;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 92) / ROW_HEIGHT);
    }
}
