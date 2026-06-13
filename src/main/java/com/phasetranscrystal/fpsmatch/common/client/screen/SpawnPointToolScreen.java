package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SpawnPointToolScreen extends FPSMWidgetScreen {
    private static final int PANEL_WIDTH = 326;
    private static final int PANEL_HEIGHT = 220;

    private List<String> availableTypes;
    private List<String> availableMaps;
    private List<String> availableTeams;
    private List<SpawnPointData> spawnPoints;
    private String selectedType;
    private String selectedMap;
    private String selectedTeam;
    private int selectedIndex;

    private LabelWidget typeLabel;
    private LabelWidget mapLabel;
    private LabelWidget teamLabel;
    private LabelWidget countLabel;
    private LabelWidget currentLabel;
    private LabelWidget detailLabel;

    private int panelLeft;
    private int panelTop;

    public SpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket data) {
        super(Component.translatable("gui.fpsm.spawn_point_tool.title"));
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.availableMaps = new ArrayList<>(data.availableMaps());
        this.availableTeams = new ArrayList<>(data.availableTeams());
        this.spawnPoints = new ArrayList<>(data.spawnPoints());
        this.selectedType = data.selectedType();
        this.selectedMap = data.selectedMap();
        this.selectedTeam = data.selectedTeam();
        this.selectedIndex = data.selectedIndex();
    }

    public void applyData(OpenSpawnPointToolScreenS2CPacket data) {
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.availableMaps = new ArrayList<>(data.availableMaps());
        this.availableTeams = new ArrayList<>(data.availableTeams());
        this.spawnPoints = new ArrayList<>(data.spawnPoints());
        this.selectedType = data.selectedType();
        this.selectedMap = data.selectedMap();
        this.selectedTeam = data.selectedTeam();
        this.selectedIndex = data.selectedIndex();
        updateLabels();
    }

    @Override
    protected void buildUI() {
        panelLeft = 18;
        panelTop = Math.max(18, (height - PANEL_HEIGHT) / 2);

        // 半透明背景
        root.addWidget(new WidgetGroup(0, 0, width, height)
                .setBackground(new ColorRectTexture(0x5A000000)));

        // 面板
        root.addWidget(new WidgetGroup(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT)
                .setBackground(new ColorRectTexture(0xD0191D22)));
        root.addWidget(new WidgetGroup(panelLeft, panelTop, PANEL_WIDTH, 1).setBackground(new ColorRectTexture(0xFFB58A42)));
        root.addWidget(new WidgetGroup(panelLeft, panelTop + PANEL_HEIGHT - 1, PANEL_WIDTH, 1).setBackground(new ColorRectTexture(0xFFB58A42)));
        root.addWidget(new WidgetGroup(panelLeft, panelTop, 1, PANEL_HEIGHT).setBackground(new ColorRectTexture(0xFFB58A42)));
        root.addWidget(new WidgetGroup(panelLeft + PANEL_WIDTH - 1, panelTop, 1, PANEL_HEIGHT).setBackground(new ColorRectTexture(0xFFB58A42)));

        // 标题
        root.addWidget(new LabelWidget(panelLeft + 10, panelTop + 8, title.getString()).setTextColor(0xFFFFFFFF));
        root.addWidget(new LabelWidget(panelLeft + 12, panelTop + 30,
                Component.translatable("gui.fpsm.spawn_point_tool.type").getString()).setTextColor(0xFFF1D9B0));
        root.addWidget(new LabelWidget(panelLeft + 12, panelTop + 60,
                Component.translatable("gui.fpsm.spawn_point_tool.map").getString()).setTextColor(0xFFF1D9B0));
        root.addWidget(new LabelWidget(panelLeft + 12, panelTop + 90,
                Component.translatable("gui.fpsm.spawn_point_tool.team").getString()).setTextColor(0xFFF1D9B0));

        // 动态标签
        typeLabel = new LabelWidget(panelLeft + 124, panelTop + 24,
                (selectedType.isBlank() ? "-" : selectedType)).setTextColor(0xFFFFFFFF);
        mapLabel = new LabelWidget(panelLeft + 124, panelTop + 54,
                (selectedMap.isBlank() ? "-" : selectedMap)).setTextColor(0xFFFFFFFF);
        teamLabel = new LabelWidget(panelLeft + 124, panelTop + 84,
                (selectedTeam.isBlank() ? "-" : selectedTeam)).setTextColor(0xFFFFFFFF);
        countLabel = new LabelWidget(panelLeft + 12, panelTop + 120,
                Component.translatable("gui.fpsm.spawn_point_tool.count", spawnPoints.size()).getString()).setTextColor(0xFFFFFFFF);
        currentLabel = new LabelWidget(panelLeft + 160, panelTop + 120, currentPointLabel().getString()).setTextColor(0xFFD7E3EA);
        detailLabel = new LabelWidget(panelLeft + 12, panelTop + 144, currentPointDetail().getString()).setTextColor(0xFFA4C4D3);

        root.addWidget(typeLabel);
        root.addWidget(mapLabel);
        root.addWidget(teamLabel);
        root.addWidget(countLabel);
        root.addWidget(currentLabel);
        root.addWidget(detailLabel);

        // 按钮
        root.addWidget(FPSMWidgets.button(panelLeft + 124, panelTop + 24, 184, 20,
                Component.literal(selectedType.isBlank() ? "-" : selectedType), this::cycleType));
        root.addWidget(FPSMWidgets.button(panelLeft + 124, panelTop + 54, 184, 20,
                Component.literal(selectedMap.isBlank() ? "-" : selectedMap), this::cycleMap));
        root.addWidget(FPSMWidgets.button(panelLeft + 124, panelTop + 84, 184, 20,
                Component.literal(selectedTeam.isBlank() ? "-" : selectedTeam), this::cycleTeam));

        root.addWidget(FPSMWidgets.button(panelLeft + 124, panelTop + 114, 24, 20,
                Component.literal("<"), () -> stepIndex(-1)));
        root.addWidget(FPSMWidgets.button(panelLeft + 284, panelTop + 114, 24, 20,
                Component.literal(">"), () -> stepIndex(1)));

        root.addWidget(FPSMWidgets.button(panelLeft + 18, panelTop + 170, 140, 20,
                Component.translatable("gui.fpsm.spawn_point_tool.delete"),
                () -> sendAction(SpawnPointToolActionC2SPacket.Action.DELETE_SELECTED)));
        root.addWidget(FPSMWidgets.button(panelLeft + 168, panelTop + 170, 140, 20,
                Component.translatable("gui.fpsm.spawn_point_tool.clear"),
                () -> sendAction(SpawnPointToolActionC2SPacket.Action.CLEAR_TEAM)));
        root.addWidget(FPSMWidgets.button(panelLeft + 18, panelTop + 194, 290, 20,
                Component.translatable("gui.fpsm.close"), this::onClose));
    }

    private void cycleType() {
        if (availableTypes.isEmpty()) return;
        int idx = availableTypes.indexOf(selectedType);
        selectedType = availableTypes.get(idx < 0 ? 0 : (idx + 1) % availableTypes.size());
        selectedMap = ""; selectedTeam = "";
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleMap() {
        if (availableMaps.isEmpty()) return;
        int idx = availableMaps.indexOf(selectedMap);
        selectedMap = availableMaps.get(idx < 0 ? 0 : (idx + 1) % availableMaps.size());
        selectedTeam = "";
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleTeam() {
        if (availableTeams.isEmpty()) return;
        int idx = availableTeams.indexOf(selectedTeam);
        selectedTeam = availableTeams.get(idx < 0 ? 0 : (idx + 1) % availableTeams.size());
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void stepIndex(int offset) {
        if (spawnPoints.isEmpty()) { selectedIndex = -1; }
        else { selectedIndex = Math.max(0, Math.min((selectedIndex < 0 ? 0 : selectedIndex) + offset, spawnPoints.size() - 1)); }
        updateLabels();
    }

    private void updateLabels() {
        if (typeLabel == null) return;
        typeLabel.setText(selectedType.isBlank() ? "-" : selectedType);
        mapLabel.setText(selectedMap.isBlank() ? "-" : selectedMap);
        teamLabel.setText(selectedTeam.isBlank() ? "-" : selectedTeam);
        countLabel.setText(Component.translatable("gui.fpsm.spawn_point_tool.count", spawnPoints.size()).getString());
        currentLabel.setText(currentPointLabel().getString());
        detailLabel.setText(currentPointDetail().getString());
    }

    private Component currentPointLabel() {
        if (spawnPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= spawnPoints.size())
            return Component.translatable("gui.fpsm.spawn_point_tool.current", "-");
        return Component.translatable("gui.fpsm.spawn_point_tool.current", (selectedIndex + 1) + "/" + spawnPoints.size());
    }

    private Component currentPointDetail() {
        if (spawnPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= spawnPoints.size())
            return Component.translatable("gui.fpsm.spawn_point_tool.no_point");
        SpawnPointData d = spawnPoints.get(selectedIndex);
        return Component.literal(String.format("X %.1f Y %.1f Z %.1f  Yaw %.1f Pitch %.1f",
                d.getX(), d.getY(), d.getZ(), d.getYaw(), d.getPitch()));
    }

    private void sendAction(SpawnPointToolActionC2SPacket.Action action) {
        FPSMatch.sendToServer(new SpawnPointToolActionC2SPacket(action, selectedType, selectedMap, selectedTeam, selectedIndex));
    }

    @Override
    public void onClose() {
        sendAction(SpawnPointToolActionC2SPacket.Action.SAVE_SELECTIONS);
        super.onClose();
    }
}