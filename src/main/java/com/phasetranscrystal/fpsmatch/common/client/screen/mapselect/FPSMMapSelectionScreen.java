package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class FPSMMapSelectionScreen extends FPSMWidgetScreen {
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

    private DraggableScrollableWidgetGroup scrollGroup;
    private WidgetGroup listContent;
    private ButtonWidget detailButton;
    private ButtonWidget joinButton;
    private ButtonWidget leaveButton;

    public FPSMMapSelectionScreen(MapSelectionSnapshotS2CPacket snapshot, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.title"));
        this.snapshot = snapshot;
        this.parent = parent;
    }

    public void applySnapshot(MapSelectionSnapshotS2CPacket snapshot) {
        this.snapshot = snapshot;
        restoreSelectedIndex();
        rebuildUI();
    }

    @Override
    protected void buildUI() {
        List<MapRoomSummary> maps = maps();
        int centerX = width / 2;

        // 标题
        root.addWidget(new LabelWidget(centerX - font.width(title) / 2, 24, title.getString()).setTextColor(0xFFFFFFFF));
        // 计数
        Component countText = Component.translatable("gui.fpsm.map_select.snapshot_count", maps.size());
        root.addWidget(new LabelWidget(centerX - font.width(countText) / 2, 48, countText.getString()).setTextColor(0xFFB8D4E3));

        // 可滚动列表
        int listLeft = width / 2 - PANEL_WIDTH / 2;
        int listRight = width / 2 + PANEL_WIDTH / 2;
        int listBottom = Math.max(LIST_TOP + ROW_HEIGHT, height - LIST_BOTTOM_PADDING);
        int listWidth = listRight - listLeft;
        int listHeight = listBottom - LIST_TOP;

        scrollGroup = new DraggableScrollableWidgetGroup(listLeft - 6, LIST_TOP - 6, listWidth + 12, listHeight + 12);
        scrollGroup.setBackground(new ColorRectTexture(0x77000000));
        scrollGroup.setYScrollBarWidth(4).setYBarStyle(new ColorRectTexture(0x33000000), new ColorRectTexture(0x88FFFFFF));

        listContent = new WidgetGroup(6, 6, listWidth, maps.size() * (ROW_HEIGHT + ROW_GAP));
        listContent.setClientSideWidget();

        for (int i = 0; i < maps.size(); i++) {
            MapRoomSummary summary = maps.get(i);
            boolean isSelected = i == selectedIndex;
            int rowColor = isSelected ? 0xAA2D5F7D : (i % 2 == 0 ? 0x88212A33 : 0x8834485A);
            int statusColor = statusColor(summary);

            // 行背景
            WidgetGroup row = new WidgetGroup(0, i * (ROW_HEIGHT + ROW_GAP), listWidth, ROW_HEIGHT);
            row.setBackground(new ColorRectTexture(rowColor));

            // 左侧色条
            row.addWidget(new WidgetGroup(0, 0, 3, ROW_HEIGHT)
                    .setBackground(new ColorRectTexture(statusColor)));

            // 地图名称
            row.addWidget(new LabelWidget(10, 7, summary.gameType() + " / " + summary.mapName())
                    .setTextColor(0xFFFFFFFF));

            // 玩家数
            String playersText = summary.joinedPlayers() + "/" + (summary.maxPlayers() < 0 ? "∞" : summary.maxPlayers());
            Component playersLabel = Component.translatable("gui.fpsm.map_select.players", summary.joinedPlayers(), maxPlayersText(summary));
            row.addWidget(new LabelWidget(listWidth - 86, 7, playersLabel.getString()).setTextColor(0xFFE6F2FF));

            // 状态
            row.addWidget(new LabelWidget(10, 22, statusText(summary).getString()).setTextColor(statusColor));

            // 维度/区域
            Component areaLabel = Component.translatable("gui.fpsm.map_select.summary", summary.dimension(), summary.areaText());
            row.addWidget(new LabelWidget(118, 22, areaLabel.getString()).setTextColor(0xFFB8D4E3));

            // 已加入标记
            if (summary.currentPlayerJoined() || summary.currentPlayerSpectating()) {
                row.addWidget(new LabelWidget(listWidth - 58, 22,
                        Component.translatable("gui.fpsm.map_select.joined").getString()).setTextColor(0xFF74E084));
            }

            // 行点击
            final int idx = i;
            row.addWidget(makeRowClicker(listWidth, ROW_HEIGHT, idx));

            listContent.addWidget(row);
        }

        scrollGroup.addWidget(listContent);
        root.addWidget(scrollGroup);

        // 底部按钮
        int btnY = height - 52;
        detailButton = FPSMWidgets.button(centerX - 183, btnY, 82, 20,
                Component.translatable("gui.fpsm.map_select.detail"), () -> sendSelectedAction(MapRoomActionC2SPacket.Action.REQUEST_DETAIL));
        joinButton = FPSMWidgets.button(centerX - 93, btnY, 82, 20,
                Component.translatable("gui.fpsm.map_select.join"), () -> sendSelectedAction(MapRoomActionC2SPacket.Action.JOIN));
        leaveButton = FPSMWidgets.button(centerX - 3, btnY, 82, 20,
                Component.translatable("gui.fpsm.map_select.leave"), () -> sendSelectedAction(MapRoomActionC2SPacket.Action.LEAVE));

        root.addWidget(detailButton);
        root.addWidget(joinButton);
        root.addWidget(leaveButton);

        root.addWidget(FPSMWidgets.button(centerX + 87, btnY, 82, 20,
                Component.translatable("gui.fpsm.map_select.refresh"),
                () -> FPSMatch.sendToServer(new OpenMapSelectionC2SPacket())));

        root.addWidget(FPSMWidgets.button(centerX + 177, btnY, 82, 20,
                Component.translatable("gui.done"), this::onClose));

        updateActionButtons();
    }

    /** 创建透明点击区域用于行选择 */
    private Widget makeRowClicker(int w, int h, int index) {
        Widget clicker = new Widget(0, 0, w, h) {
            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (button == 0) {
                    selectedIndex = index;
                    rememberSelection(selectedSummary());
                    updateActionButtons();
                    rebuildUI();
                    return true;
                }
                return false;
            }
        };
        clicker.setClientSideWidget();
        return clicker;
    }

    private void updateActionButtons() {
        MapRoomSummary summary = selectedSummary();
        boolean joined = summary != null && (summary.currentPlayerJoined() || summary.currentPlayerSpectating());
        detailButton.setActive(summary != null);
        joinButton.setActive(summary != null && !joined && !summary.full() && (!summary.started() || summary.allowJoinInProgress()));
        leaveButton.setActive(joined);
    }

    private void sendSelectedAction(MapRoomActionC2SPacket.Action action) {
        MapRoomSummary summary = selectedSummary();
        if (summary != null) {
            FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, summary.gameType(), summary.mapName(), new UUID(0L, 0L)));
        }
    }

    private MapRoomSummary selectedSummary() {
        List<MapRoomSummary> maps = maps();
        if (selectedGameType != null && selectedMapName != null) {
            for (MapRoomSummary s : maps) {
                if (selectedGameType.equals(s.gameType()) && selectedMapName.equals(s.mapName())) return s;
            }
        }
        if (selectedIndex < 0 || selectedIndex >= maps.size()) return null;
        MapRoomSummary s = maps.get(selectedIndex);
        rememberSelection(s);
        return s;
    }

    private void rememberSelection(MapRoomSummary summary) {
        if (summary == null) { selectedGameType = null; selectedMapName = null; }
        else { selectedGameType = summary.gameType(); selectedMapName = summary.mapName(); }
    }

    private void restoreSelectedIndex() {
        List<MapRoomSummary> maps = maps();
        if (selectedGameType != null && selectedMapName != null) {
            for (int i = 0; i < maps.size(); i++) {
                if (selectedGameType.equals(maps.get(i).gameType()) && selectedMapName.equals(maps.get(i).mapName())) {
                    selectedIndex = i; return;
                }
            }
        }
        selectedIndex = maps.isEmpty() ? -1 : Math.min(Math.max(selectedIndex, 0), maps.size() - 1);
        rememberSelection(selectedIndex >= 0 ? maps.get(selectedIndex) : null);
    }

    private List<MapRoomSummary> maps() { return snapshot == null ? List.of() : snapshot.maps(); }

    private Component statusText(MapRoomSummary s) {
        if (s.debug()) return Component.translatable("gui.fpsm.map_select.status.debug");
        if (s.started()) return Component.translatable(s.allowJoinInProgress() ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private int statusColor(MapRoomSummary s) {
        if (s.debug()) return 0xFFFFC857;
        if (s.started()) return s.allowJoinInProgress() ? 0xFF66D9E8 : 0xFFFF6B6B;
        return 0xFF74E084;
    }

    private String maxPlayersText(MapRoomSummary s) { return s.maxPlayers() < 0 ? "∞" : Integer.toString(s.maxPlayers()); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}