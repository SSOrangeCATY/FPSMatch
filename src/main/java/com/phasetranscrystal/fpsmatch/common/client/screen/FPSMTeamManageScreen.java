package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 队伍管理界面，支持OP在比赛前或准备阶段对队员进行队伍分配和位置交换操作
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
        buildButtons();
    }

    private void rebuildTeamData() {
        teams.clear();
        teams.addAll(FPSMClient.getGlobalData().getTeams());
    }

    private void buildButtons() {
        this.clearWidgets();
        int startY = 40;
        int columnSpacing = COLUMN_WIDTH + PADDING * 2;

        // 标题行按钮
        int titleX = this.width / 2 - 60;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.team_manage.refresh"), btn -> {
            rebuildTeamData();
            buildButtons();
        }).pos(titleX - 60, 5).size(50, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.team_manage.close"), btn -> {
            this.onClose();
        }).pos(titleX + 100, 5).size(50, 20).build());

        if (teams.isEmpty()) {
            return;
        }

        // 队伍列
        int totalColumns = teams.size();
        int totalWidth = totalColumns * columnSpacing + PADDING;
        int startX = (this.width - totalWidth) / 2;

        for (int columnIndex = 0; columnIndex < teams.size(); columnIndex++) {
            ClientTeam team = teams.get(columnIndex);
            int colX = startX + columnIndex * columnSpacing;

            // 队伍名称标题
            Component teamTitle = Component.literal(team.name.toUpperCase());
            this.addRenderableWidget(Button.builder(teamTitle, btn -> {}).pos(colX, startY).size(COLUMN_WIDTH, BUTTON_HEIGHT).build());

            // 玩家列表
            List<Map.Entry<UUID, PlayerData>> playerEntries = new ArrayList<>(team.players.entrySet());
            int playerY = startY + BUTTON_HEIGHT + PADDING;
            int maxPlayers = Math.min(playerEntries.size(), 20);
            for (int i = 0; i < maxPlayers; i++) {
                PlayerData playerData = playerEntries.get(i).getValue();
                Component playerComponent = playerData.name().copy();

                this.addRenderableWidget(Button.builder(playerComponent, btn -> {
                    selectedPlayer = playerData;
                    selectedPlayerTeam = team.name;
                    showMoveOptions();
                }).pos(colX, playerY + i * (BUTTON_HEIGHT + 2)).size(COLUMN_WIDTH, BUTTON_HEIGHT).build());
            }
        }
    }

    private void showMoveOptions() {
        int startY = this.height / 2 + 50;
        int centerX = this.width / 2;

        this.clearWidgets();
        buildButtons();

        List<String> otherTeams = teams.stream()
                .map(t -> t.name)
                .filter(t -> !t.equals(selectedPlayerTeam))
                .collect(Collectors.toList());

        int optionY = startY;
        for (String targetTeam : otherTeams) {
            Component moveText = Component.translatable("gui.fpsm.team_manage.move_to",
                    selectedPlayer.name().getString(), targetTeam.toUpperCase());
            final String finalTarget = targetTeam;
            this.addRenderableWidget(Button.builder(moveText, btn -> {
                movePlayer(finalTarget);
            }).pos(centerX - BUTTON_WIDTH / 2, optionY).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
            optionY += BUTTON_HEIGHT + 5;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.team_manage.cancel"), btn -> {
            selectedPlayer = null;
            selectedPlayerTeam = null;
            this.clearWidgets();
            buildButtons();
        }).pos(centerX - BUTTON_WIDTH / 2, optionY + 5).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void movePlayer(String targetTeam) {
        FPSMatch.sendToServer(new TeamManageActionC2SPacket(
                mapName,
                selectedPlayer.getOwner(),
                targetTeam
        ));

        selectedPlayer = null;
        selectedPlayerTeam = null;
        this.clearWidgets();
        buildButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}