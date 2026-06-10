package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

public class FPSMMapSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 46;
    private static final int ROW_GAP = 4;
    private static final int LIST_TOP = 72;
    private static final int LIST_BOTTOM_PADDING = 88;

    private MapSelectionSnapshotS2CPacket snapshot;
    private final Screen parent;
    private int selectedIndex = -1;
    private String selectedGameType;
    private String selectedMapName;
    private int scrollOffset;
    private Button detailButton;
    private Button joinButton;
    private Button leaveButton;

    public FPSMMapSelectionScreen(MapSelectionSnapshotS2CPacket snapshot, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.title"));
        this.snapshot = snapshot;
        this.parent = parent;
    }

    public void applySnapshot(MapSelectionSnapshotS2CPacket snapshot) {
        this.snapshot = snapshot;
        restoreSelectedIndex();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        int centerX = width / 2;
        detailButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.detail"), button -> sendSelectedAction(MapRoomActionC2SPacket.Action.REQUEST_DETAIL))
                .bounds(centerX - 183, height - 52, 82, 20)
                .build());
        joinButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.join"), button -> sendSelectedAction(MapRoomActionC2SPacket.Action.JOIN))
                .bounds(centerX - 93, height - 52, 82, 20)
                .build());
        leaveButton = addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.leave"), button -> sendSelectedAction(MapRoomActionC2SPacket.Action.LEAVE))
                .bounds(centerX - 3, height - 52, 82, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.refresh"), button -> FPSMatch.sendToServer(new OpenMapSelectionC2SPacket()))
                .bounds(centerX + 87, height - 52, 82, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX + 177, height - 52, 82, 20)
                .build());
        updateActionButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        List<MapRoomSummary> maps = maps();
        int count = maps.size();
        graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.snapshot_count", count), width / 2, 48, 0xFFB8D4E3);
        renderList(graphics, maps, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedIndex = indexAt(mouseX, mouseY);
            if (clickedIndex >= 0) {
                selectedIndex = clickedIndex;
                rememberSelection(selectedSummary());
                updateActionButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= LIST_TOP && mouseY <= listBottom()) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScrollOffset());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderList(GuiGraphics graphics, List<MapRoomSummary> maps, int mouseX, int mouseY) {
        int left = listLeft();
        int right = listRight();
        int bottom = listBottom();
        graphics.fill(left - 6, LIST_TOP - 6, right + 6, bottom + 6, 0x77000000);
        if (maps.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.empty"), width / 2, LIST_TOP + 32, 0xFFAAAAAA);
            return;
        }
        int visibleRows = visibleRows();
        int end = Math.min(maps.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < end; i++) {
            MapRoomSummary summary = maps.get(i);
            int rowTop = LIST_TOP + (i - scrollOffset) * (ROW_HEIGHT + ROW_GAP);
            boolean selected = i == selectedIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= rowTop && mouseY <= rowTop + ROW_HEIGHT;
            int color = selected ? 0xAA2D5F7D : hovered ? 0x8834485A : 0x88212A33;
            graphics.fill(left, rowTop, right, rowTop + ROW_HEIGHT, color);
            graphics.fill(left, rowTop, left + 3, rowTop + ROW_HEIGHT, statusColor(summary));

            graphics.drawString(font, Component.literal(summary.gameType() + " / " + summary.mapName()), left + 10, rowTop + 7, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players", summary.joinedPlayers(), maxPlayersText(summary)), right - 76, rowTop + 7, 0xFFE6F2FF, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.status", statusText(summary)), left + 10, rowTop + 22, statusColor(summary), false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.summary", summary.dimension(), summary.areaText()), left + 118, rowTop + 22, 0xFFB8D4E3, false);
            if (summary.currentPlayerJoined() || summary.currentPlayerSpectating()) {
                graphics.drawString(font, Component.translatable("gui.fpsm.map_select.joined"), right - 48, rowTop + 22, 0xFF74E084, false);
            }
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < listLeft() || mouseX > listRight() || mouseY < LIST_TOP || mouseY > listBottom()) {
            return -1;
        }
        int relativeY = (int) mouseY - LIST_TOP;
        int row = relativeY / (ROW_HEIGHT + ROW_GAP);
        if (relativeY % (ROW_HEIGHT + ROW_GAP) > ROW_HEIGHT) {
            return -1;
        }
        int index = scrollOffset + row;
        return index < maps().size() ? index : -1;
    }

    private void sendSelectedAction(MapRoomActionC2SPacket.Action action) {
        MapRoomSummary summary = selectedSummary();
        if (summary != null) {
            FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, summary.gameType(), summary.mapName(), new UUID(0L, 0L)));
        }
    }

    private void updateActionButtons() {
        MapRoomSummary summary = selectedSummary();
        boolean joined = summary != null && (summary.currentPlayerJoined() || summary.currentPlayerSpectating());
        if (detailButton != null) {
            detailButton.active = summary != null;
        }
        if (joinButton != null) {
            joinButton.active = summary != null && !joined && !summary.full() && (!summary.started() || summary.allowJoinInProgress());
        }
        if (leaveButton != null) {
            leaveButton.active = joined;
        }
    }

    private MapRoomSummary selectedSummary() {
        List<MapRoomSummary> maps = maps();
        if (selectedGameType != null && selectedMapName != null) {
            for (MapRoomSummary summary : maps) {
                if (selectedGameType.equals(summary.gameType()) && selectedMapName.equals(summary.mapName())) {
                    return summary;
                }
            }
        }
        if (selectedIndex < 0 || selectedIndex >= maps.size()) {
            return null;
        }
        MapRoomSummary summary = maps.get(selectedIndex);
        rememberSelection(summary);
        return summary;
    }

    private void rememberSelection(MapRoomSummary summary) {
        if (summary == null) {
            selectedGameType = null;
            selectedMapName = null;
            return;
        }
        selectedGameType = summary.gameType();
        selectedMapName = summary.mapName();
    }

    private void restoreSelectedIndex() {
        List<MapRoomSummary> maps = maps();
        if (selectedGameType != null && selectedMapName != null) {
            for (int i = 0; i < maps.size(); i++) {
                MapRoomSummary summary = maps.get(i);
                if (selectedGameType.equals(summary.gameType()) && selectedMapName.equals(summary.mapName())) {
                    selectedIndex = i;
                    return;
                }
            }
        }
        selectedIndex = maps.isEmpty() ? -1 : Math.min(Math.max(selectedIndex, 0), maps.size() - 1);
        rememberSelection(selectedIndex >= 0 ? maps.get(selectedIndex) : null);
    }

    private List<MapRoomSummary> maps() {
        return snapshot == null ? List.of() : snapshot.maps();
    }

    private Component statusText(MapRoomSummary summary) {
        if (summary.debug()) {
            return Component.translatable("gui.fpsm.map_select.status.debug");
        }
        if (summary.started()) {
            return Component.translatable(summary.allowJoinInProgress() ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        }
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private int statusColor(MapRoomSummary summary) {
        if (summary.debug()) {
            return 0xFFFFC857;
        }
        if (summary.started()) {
            return summary.allowJoinInProgress() ? 0xFF66D9E8 : 0xFFFF6B6B;
        }
        return 0xFF74E084;
    }

    private String maxPlayersText(MapRoomSummary summary) {
        return summary.maxPlayers() < 0 ? "∞" : Integer.toString(summary.maxPlayers());
    }

    private int listLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int listRight() {
        return width / 2 + PANEL_WIDTH / 2;
    }

    private int listBottom() {
        return Math.max(LIST_TOP + ROW_HEIGHT, height - LIST_BOTTOM_PADDING);
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - LIST_TOP + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    private int maxScrollOffset() {
        return Math.max(0, maps().size() - visibleRows());
    }
}
