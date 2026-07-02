package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 队伍管理界面，支持OP对队员进行队伍分配操作。
 * 点击玩家后会在底部显示变更队伍面板，无需重建界面。
 */
public class FPSMTeamManageScreen extends Screen {
    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int COLUMN_WIDTH = 200;

    private final String mapName;
    private final List<ClientTeam> teams = new ArrayList<>();
    private PlayerData selectedPlayer;
    private String selectedPlayerTeam;

    public FPSMTeamManageScreen(String mapName) {
        super(Component.translatable("gui.fpsm.team_manage.title"));
        this.mapName = mapName;
    }

    @Override
    protected void init() {
        super.init();
        rebuildTeamData();
        buildWidgets();
    }

    private void rebuildTeamData() {
        teams.clear();
        teams.addAll(FPSMClient.getGlobalData().getTeams());
    }

    private void buildWidgets() {
        this.clearWidgets();

        // 顶部按钮栏
        this.addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.team_manage.refresh"), btn -> {
            selectedPlayer = null;
            selectedPlayerTeam = null;
            rebuildTeamData();
            buildWidgets();
        }).pos(10, 5).size(50, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.team_manage.close"), btn -> {
            this.onClose();
        }).pos(width - 60, 5).size(50, 20).build());

        if (teams.isEmpty()) {
            return;
        }

        // 队伍列
        int columnSpacing = COLUMN_WIDTH + PADDING * 2;
        int totalColumns = teams.size();
        int totalWidth = totalColumns * columnSpacing + PADDING;
        int startX = (this.width - totalWidth) / 2;
        int startY = 40;

        for (int ci = 0; ci < teams.size(); ci++) {
            ClientTeam team = teams.get(ci);
            int colX = startX + ci * columnSpacing;

            // 队伍名称
            this.addRenderableWidget(Button.builder(Component.literal("§l" + team.name.toUpperCase()), btn -> {})
                    .pos(colX, startY).size(COLUMN_WIDTH, BUTTON_HEIGHT).build());

            // 玩家列表
            List<Map.Entry<UUID, PlayerData>> entries = new ArrayList<>(team.players.entrySet());
            int playerY = startY + BUTTON_HEIGHT + PADDING;
            for (int i = 0; i < Math.min(entries.size(), 20); i++) {
                PlayerData data = entries.get(i).getValue();
                boolean isSelected = selectedPlayer != null && selectedPlayer.getOwner().equals(data.getOwner());

                Component label = isSelected
                        ? Component.literal("▶ " + data.name().getString())
                        : data.name().copy();

                this.addRenderableWidget(Button.builder(label, btn -> {
                    selectedPlayer = data;
                    selectedPlayerTeam = team.name;
                    buildWidgets(); // 刷新高亮和底部面板
                }).pos(colX, playerY + i * (BUTTON_HEIGHT + 2)).size(COLUMN_WIDTH, BUTTON_HEIGHT).build());
            }
        }

        // 底部变更面板（选中玩家后显示）
        if (selectedPlayer != null) {
            renderMovePanel();
        }
    }

    private void renderMovePanel() {
        int panelTop = this.height - 72;
        int panelLeft = this.width / 4;
        int panelRight = this.width * 3 / 4;
        int centerX = this.width / 2;

        // 选中提示
        Component hint = Component.translatable("gui.fpsm.team_manage.selected",
                selectedPlayer.name().getString(), selectedPlayerTeam.toUpperCase());

        // 移动按钮
        List<String> otherTeams = teams.stream()
                .map(t -> t.name)
                .filter(t -> !t.equals(selectedPlayerTeam))
                .toList();

        if (otherTeams.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.fpsm.team_manage.no_other_team"), btn -> {})
                    .pos(centerX - 80, panelTop + 24).size(160, BUTTON_HEIGHT).build());
        } else {
            int totalW = otherTeams.size() * (BUTTON_WIDTH + 4) - 4;
            int startX = centerX - totalW / 2;
            for (int i = 0; i < otherTeams.size(); i++) {
                final String target = otherTeams.get(i);
                Component btnText = Component.translatable("gui.fpsm.team_manage.move_btn", target.toUpperCase());
                this.addRenderableWidget(Button.builder(btnText, btn -> movePlayer(target))
                        .pos(startX + i * (BUTTON_WIDTH + 4), panelTop + 24)
                        .size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
            }
        }

        // 取消按钮
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.fpsm.team_manage.cancel"), btn -> {
                    selectedPlayer = null;
                    selectedPlayerTeam = null;
                    buildWidgets();
                }).pos(centerX - 40, panelTop + 48).size(80, BUTTON_HEIGHT).build());
    }

    private void movePlayer(String targetTeam) {
        FPSMatch.sendToServer(new TeamManageActionC2SPacket(
                mapName,
                selectedPlayer.getOwner(),
                targetTeam
        ));
        selectedPlayer = null;
        selectedPlayerTeam = null;
        buildWidgets();
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // 底部面板背景框
        if (selectedPlayer != null) {
            int panelTop = this.height - 72;
            int panelLeft = this.width / 4;
            int panelRight = this.width * 3 / 4;
            graphics.fill(panelLeft, panelTop - 2, panelRight, panelTop, 0xFF666666);
            graphics.fill(panelLeft, panelTop, panelRight, this.height - 10, 0xAA000000);

            Component hint = Component.translatable("gui.fpsm.team_manage.selected",
                    selectedPlayer.name().getString(), selectedPlayerTeam.toUpperCase());
            graphics.centeredText(this.font, hint, this.width / 2, panelTop + 6, 0xFFE6F2FF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
