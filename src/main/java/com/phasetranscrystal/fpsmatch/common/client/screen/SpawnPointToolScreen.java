package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SpawnPointToolScreen extends Screen {
    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_HEIGHT = 212;
    private static final int PANEL_BACKGROUND = 0xD0191D22;
    private static final int PANEL_BORDER = 0xFFB58A42;

    private List<String> availableTypes;
    private List<String> availableMaps;
    private List<String> availableTeams;
    private List<SpawnPointData> spawnPoints;
    private String selectedType;
    private String selectedMap;
    private String selectedTeam;
    private int selectedIndex;

    private Button typeButton;
    private Button mapButton;
    private Button teamButton;
    private Button prevButton;
    private Button nextButton;

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

    @Override
    protected void init() {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);

        this.typeButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleType())
                .pos(left + 112, top + 20)
                .size(156, 20)
                .build());
        this.mapButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleMap())
                .pos(left + 112, top + 46)
                .size(156, 20)
                .build());
        this.teamButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleTeam())
                .pos(left + 112, top + 72)
                .size(156, 20)
                .build());

        this.prevButton = this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> stepIndex(-1))
                .pos(left + 112, top + 98)
                .size(24, 20)
                .build());
        this.nextButton = this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> stepIndex(1))
                .pos(left + 244, top + 98)
                .size(24, 20)
                .build());

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.spawn_point_tool.preview_map"), button -> sendAction(SpawnPointToolActionC2SPacket.Action.PREVIEW_MAP))
                .pos(left + 18, top + 144)
                .size(120, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.spawn_point_tool.preview_points"), button -> sendAction(SpawnPointToolActionC2SPacket.Action.PREVIEW_POINTS))
                .pos(left + 148, top + 144)
                .size(120, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.spawn_point_tool.delete"), button -> sendAction(SpawnPointToolActionC2SPacket.Action.DELETE_SELECTED))
                .pos(left + 18, top + 168)
                .size(120, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.spawn_point_tool.clear"), button -> sendAction(SpawnPointToolActionC2SPacket.Action.CLEAR_TEAM))
                .pos(left + 148, top + 168)
                .size(120, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.close"), button -> onClose())
                .pos(left + 18, top + 192)
                .size(250, 20)
                .build());

        updateButtonLabels();
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
        updateButtonLabels();
    }

    @Override
    public void onClose() {
        sendAction(SpawnPointToolActionC2SPacket.Action.SAVE_SELECTIONS);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BACKGROUND);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 1, PANEL_BORDER);
        guiGraphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, top + PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BORDER);

        guiGraphics.drawString(this.font, this.title, left + 10, top + 6, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.spawn_point_tool.type"), left + 12, top + 26, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.spawn_point_tool.map"), left + 12, top + 52, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.spawn_point_tool.team"), left + 12, top + 78, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.spawn_point_tool.count", this.spawnPoints.size()), left + 12, top + 104, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, currentPointLabel(), left + 142, top + 104, 0xD7E3EA, false);
        guiGraphics.drawString(this.font, currentPointDetail(), left + 12, top + 124, 0xA4C4D3, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void cycleType() {
        if (availableTypes.isEmpty()) {
            return;
        }
        int currentIndex = availableTypes.indexOf(selectedType);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableTypes.size();
        this.selectedType = availableTypes.get(nextIndex);
        this.selectedMap = "";
        this.selectedTeam = "";
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleMap() {
        if (availableMaps.isEmpty()) {
            return;
        }
        int currentIndex = availableMaps.indexOf(selectedMap);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableMaps.size();
        this.selectedMap = availableMaps.get(nextIndex);
        this.selectedTeam = "";
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleTeam() {
        if (availableTeams.isEmpty()) {
            return;
        }
        int currentIndex = availableTeams.indexOf(selectedTeam);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableTeams.size();
        this.selectedTeam = availableTeams.get(nextIndex);
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void stepIndex(int offset) {
        if (spawnPoints.isEmpty()) {
            selectedIndex = -1;
        } else {
            int base = selectedIndex < 0 ? 0 : selectedIndex;
            selectedIndex = Math.max(0, Math.min(base + offset, spawnPoints.size() - 1));
        }
        updateButtonLabels();
    }

    private void updateButtonLabels() {
        if (this.typeButton == null) {
            return;
        }
        this.typeButton.setMessage(Component.literal(selectedType.isBlank() ? "-" : selectedType));
        this.mapButton.setMessage(Component.literal(selectedMap.isBlank() ? "-" : selectedMap));
        this.teamButton.setMessage(Component.literal(selectedTeam.isBlank() ? "-" : selectedTeam));
        boolean hasPoints = !spawnPoints.isEmpty();
        this.prevButton.active = hasPoints;
        this.nextButton.active = hasPoints;
    }

    private Component currentPointLabel() {
        if (spawnPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= spawnPoints.size()) {
            return Component.translatable("gui.fpsm.spawn_point_tool.current", "-");
        }
        return Component.translatable("gui.fpsm.spawn_point_tool.current", (selectedIndex + 1) + "/" + spawnPoints.size());
    }

    private Component currentPointDetail() {
        if (spawnPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= spawnPoints.size()) {
            return Component.translatable("gui.fpsm.spawn_point_tool.no_point");
        }
        SpawnPointData data = spawnPoints.get(selectedIndex);
        return Component.literal(String.format("X %.1f Y %.1f Z %.1f  Yaw %.1f Pitch %.1f",
                data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch()));
    }

    private void sendAction(SpawnPointToolActionC2SPacket.Action action) {
        FPSMatch.sendToServer(new SpawnPointToolActionC2SPacket(
                action,
                this.selectedType,
                this.selectedMap,
                this.selectedTeam,
                this.selectedIndex
        ));
    }
}
