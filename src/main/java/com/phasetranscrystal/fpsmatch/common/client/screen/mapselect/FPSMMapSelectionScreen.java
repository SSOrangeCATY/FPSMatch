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

public class FPSMMapSelectionScreen extends FPSMMapScreenBase {
    // 列表项布局：左侧缩略图 + 右侧信息
    private static final int THUMB_SIZE = 38;
    private static final int THUMB_MARGIN = 4;

    private MapSelectionSnapshotS2CPacket snapshot;
    private final Screen parent;
    private int selectedIndex = -1;
    private String selectedGameType;
    private String selectedMapName;
    private int scrollOffset;
    private Button detailButton;
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
        // 底部 4 按钮：详情/离开/刷新/完成，统一中按钮宽度 90，间距 8
        int total = 4;
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;
        int[] xs = new int[total];
        for (int i = 0; i < total; i++) {
            xs[i] = buttonX(centerX, bw, gap, i, total);
        }
        detailButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.detail"), xs[0], height - 52,
                button -> sendSelectedAction(MapRoomActionC2SPacket.Action.REQUEST_DETAIL)));
        leaveButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.leave"), xs[1], height - 52,
                button -> sendSelectedAction(MapRoomActionC2SPacket.Action.LEAVE)));
        addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.refresh"), xs[2], height - 52,
                button -> FPSMatch.sendToServer(new OpenMapSelectionC2SPacket())));
        addRenderableWidget(createMediumButton(Component.translatable("gui.done"), xs[3], height - 52,
                button -> onClose()));
        updateActionButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, FPSMGuiTheme.TEXT_TITLE);
        List<MapRoomSummary> maps = maps();
        int count = maps.size();
        graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.snapshot_count", count), width / 2, 48, FPSMGuiTheme.TEXT_SUB);
        renderList(graphics, maps, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedIndex = indexAt(mouseX, mouseY);
            if (clickedIndex >= 0) {
                selectedIndex = clickedIndex;
                MapRoomSummary summary = maps().get(selectedIndex);
                rememberSelection(summary);
                updateActionButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= FPSMGuiTheme.LIST_TOP && mouseY <= listBottom()) {
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
        // 列表面板背景（统一）
        drawListBackground(graphics, left - 6, FPSMGuiTheme.LIST_TOP - 6, right + 6, bottom + 6);
        if (maps.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.empty"), width / 2, FPSMGuiTheme.LIST_TOP + 32, FPSMGuiTheme.TEXT_MUTED);
            return;
        }
        int visibleRows = visibleRows();
        int end = Math.min(maps.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < end; i++) {
            MapRoomSummary summary = maps.get(i);
            int rowTop = FPSMGuiTheme.LIST_TOP + (i - scrollOffset) * (FPSMGuiTheme.ROW_HEIGHT_CARD + FPSMGuiTheme.ROW_GAP);
            boolean selected = i == selectedIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= rowTop && mouseY <= rowTop + FPSMGuiTheme.ROW_HEIGHT_CARD;
            int color = selected ? FPSMGuiTheme.ROW_SELECTED : hovered ? FPSMGuiTheme.ROW_HOVER : FPSMGuiTheme.ROW_NORMAL;
            graphics.fill(left, rowTop, right, rowTop + FPSMGuiTheme.ROW_HEIGHT_CARD, color);
            // 状态色条加粗到 4px
            graphics.fill(left, rowTop, left + FPSMGuiTheme.STATUS_BAR_WIDTH, rowTop + FPSMGuiTheme.ROW_HEIGHT_CARD, statusColor(summary));

            // 左侧缩略图（38x38）：优先 iconTexture，无则色块兜底
            int thumbX = left + FPSMGuiTheme.STATUS_BAR_WIDTH + THUMB_MARGIN;
            int thumbY = rowTop + (FPSMGuiTheme.ROW_HEIGHT_CARD - THUMB_SIZE) / 2;
            // 缩略图无 Setting 通道下发到 summary，这里用 gameType+mapName 生成色块即可
            // （iconTexture 仅在详情页 detail 中下发，列表项用色块兜底保证一致性）
            MapThumbnailRenderer.render(graphics, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE,
                    "", summary.mapName(), summary.gameType(), "", false);

            // 右侧信息区
            int infoX = thumbX + THUMB_SIZE + THUMB_MARGIN;
            graphics.drawString(font, Component.literal(summary.displayName()), infoX, rowTop + 6, FPSMGuiTheme.TEXT_TITLE, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players", summary.joinedPlayers(), maxPlayersText(summary)), right - 76, rowTop + 6, FPSMGuiTheme.TEXT_HIGHLIGHT, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.status", statusText(summary)), infoX, rowTop + 24, statusColor(summary), false);
            Component gameTypeName = Component.translatable("fpsm.game_type." + summary.gameType());
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.game_info", gameTypeName), infoX + 118, rowTop + 24, FPSMGuiTheme.TEXT_SUB, false);
            if (summary.currentPlayerJoined() || summary.currentPlayerSpectating()) {
                graphics.drawString(font, Component.translatable("gui.fpsm.map_select.joined"), right - 48, rowTop + 24, FPSMGuiTheme.ST_ONLINE, false);
            }
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < listLeft() || mouseX > listRight() || mouseY < FPSMGuiTheme.LIST_TOP || mouseY > listBottom()) {
            return -1;
        }
        int relativeY = (int) mouseY - FPSMGuiTheme.LIST_TOP;
        int row = relativeY / (FPSMGuiTheme.ROW_HEIGHT_CARD + FPSMGuiTheme.ROW_GAP);
        if (relativeY % (FPSMGuiTheme.ROW_HEIGHT_CARD + FPSMGuiTheme.ROW_GAP) > FPSMGuiTheme.ROW_HEIGHT_CARD) {
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
        return maps.get(selectedIndex);
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
        // 如果之前的选中地图已不在列表中，重置选择以防止跳转到错误地图
        selectedIndex = -1;
        selectedGameType = null;
        selectedMapName = null;
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
            return FPSMGuiTheme.ST_DEBUG;
        }
        if (summary.started()) {
            return summary.allowJoinInProgress() ? FPSMGuiTheme.ST_RUNNING_JOINABLE : FPSMGuiTheme.ST_RUNNING_CLOSED;
        }
        return FPSMGuiTheme.ST_WAITING;
    }

    private String maxPlayersText(MapRoomSummary summary) {
        return summary.maxPlayers() < 0 ? "∞" : Integer.toString(summary.maxPlayers());
    }

    private int listLeft() {
        return width / 2 - FPSMGuiTheme.PANEL_WIDTH_LIST / 2;
    }

    private int listRight() {
        return width / 2 + FPSMGuiTheme.PANEL_WIDTH_LIST / 2;
    }

    private int listBottom() {
        return Math.max(FPSMGuiTheme.LIST_TOP + FPSMGuiTheme.ROW_HEIGHT_CARD, height - FPSMGuiTheme.LIST_BOTTOM_PADDING);
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - FPSMGuiTheme.LIST_TOP + FPSMGuiTheme.ROW_GAP) / (FPSMGuiTheme.ROW_HEIGHT_CARD + FPSMGuiTheme.ROW_GAP));
    }

    private int maxScrollOffset() {
        return Math.max(0, maps().size() - visibleRows());
    }

}
