package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapManageScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 440;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 118;

    private MapRoomDetail detail;
    private final Screen parent;

    public FPSMMapManageScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.manage.title"));
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
        int centerX = width / 2;
        Button startButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.debug.start"), button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_START, new UUID(0L, 0L)))
                .bounds(centerX - 218, 74, 84, 20)
                .build());
        Button resetButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.debug.reset"), button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_RESET, new UUID(0L, 0L)))
                .bounds(centerX - 128, 74, 84, 20)
                .build());
        Button newRoundButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.debug.new_round"), button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_NEW_ROUND, new UUID(0L, 0L)))
                .bounds(centerX - 38, 74, 84, 20)
                .build());
        Button cleanupButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.debug.cleanup"), button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_CLEANUP, new UUID(0L, 0L)))
                .bounds(centerX + 52, 74, 84, 20)
                .build());
        Button switchButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.debug.switch"), button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_SWITCH, new UUID(0L, 0L)))
                .bounds(centerX + 142, 74, 84, 20)
                .build());
        startButton.active = detail.summary().currentPlayerOp();
        resetButton.active = detail.summary().currentPlayerOp();
        newRoundButton.active = detail.summary().currentPlayerOp();
        cleanupButton.active = detail.summary().currentPlayerOp();
        switchButton.active = detail.summary().currentPlayerOp();
        int left = centerX - PANEL_WIDTH / 2;
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.players().size(), visibleRows()); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            Button kickButton = Button.builder(Component.translatable("gui.fpsm.map_select.kick"), button -> sendAction(MapRoomActionC2SPacket.Action.KICK, player.uuid()))
                    .bounds(left + 356, y + 2, 68, 20)
                    .build();
            kickButton.active = detail.summary().currentPlayerOp();
            addRenderableWidget(kickButton);
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(centerX - 50, height - 52, 100, 20)
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
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.title"), left, LIST_TOP - 22, 0xFFFFFFFF, false);
        if (detail.players().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.players.empty"), width / 2, LIST_TOP + 32, 0xFFAAAAAA);
        }
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.players().size(), visibleRows()); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            graphics.drawString(font, Component.literal(player.name()), left, y + 8, player.online() ? 0xFFE6F2FF : 0xFF8F9AA3, false);
            graphics.drawString(font, Component.literal(player.teamName()), left + 132, y + 8, player.spectator() ? 0xFFBBA7FF : 0xFF74E084, false);
            graphics.drawString(font, Component.translatable(player.online() ? "gui.fpsm.map_select.online" : "gui.fpsm.map_select.offline"), left + 252, y + 8, player.online() ? 0xFF74E084 : 0xFF8F9AA3, false);
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

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 92) / ROW_HEIGHT);
    }
}
