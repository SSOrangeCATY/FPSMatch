package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.FPSMTeamActionScreen;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.client.screen.team.TeamActionModel;
import com.ptcrys.fpsmatch.common.client.screen.team.TeamDragState;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomTeamInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby team management. Server remains authoritative; this UI only requests actions.
 * Operators can drag player rows between team rosters; the server validates every drop.
 */
public final class Ldlib2TeamManageScreen extends Ldlib2MapChildScreen {
    private static final int TEAM_COLUMN_COUNT = 2;
    private static final int TEAM_HEADER_HEIGHT = 23;
    private static final int PLAYER_ROW_HEIGHT = 29;
    private static final int ROW_GAP = 3;
    private static final String TEAM_HEADER_PREFIX = "fpsmatch.team_manage.header.";
    private static final String PLAYER_ROW_PREFIX = "fpsmatch.team_manage.player.";

    private final Label headerLabel;
    private final Label subtitleLabel;
    private final Label readySummaryLabel;
    private final Label selectedLabel;
    private final UIElement teamButtonsPanel;
    private final UIElement rosterPanel;
    private final List<UIElement> teamColumnPanels;
    private final List<VirtualScrollerView<Row>> teamPlayerLists;
    private final UIElement spectatorPanel;
    private final VirtualScrollerView<Row> spectatorList;
    private final AccessibleButton readyButton;
    private final List<AccessibleButton> teamButtons = new ArrayList<>();
    private final List<AccessibleButton> adminMoveButtons = new ArrayList<>();
    private final UIElement adminPanel;
    private final AccessibleButton backButton;
    private final TeamDragState dragState = new TeamDragState();

    private final Set<UUID> syncedReadyPlayers = new HashSet<>();
    private List<List<MapRoomTeamInfo>> columnTeams = List.of(List.of(), List.of());
    private String spectatorTeamName;
    private UIElement fallbackClickTarget;
    private int teamButtonsWidth = 520;
    private int adminPanelWidth = 520;
    private int teamButtonColumns = 4;
    private int adminButtonColumns = 4;
    private int syncedCountdownSeconds;
    private UUID selectedPlayer;
    private String selectedPlayerTeam;
    /** Last roster snapshot that has been materialized into LDLib2 rows. */
    private List<MapRoomTeamInfo> renderedTeams = List.of();
    private List<MapRoomPlayerInfo> renderedPlayers = List.of();
    private boolean renderedStarted;
    private boolean renderedAllowJoinInProgress;
    private boolean renderedCurrentPlayerOp;
    private boolean renderedCurrentPlayerJoined;
    private boolean renderedCurrentPlayerSpectating;

    public Ldlib2TeamManageScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2TeamManageScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.team_manage.title"), detail, parent);
        this.headerLabel = parts.header();
        this.subtitleLabel = parts.subtitle();
        this.readySummaryLabel = parts.readySummary();
        this.selectedLabel = parts.selected();
        this.teamButtonsPanel = parts.teamButtons();
        this.rosterPanel = parts.rosterPanel();
        this.teamColumnPanels = parts.teamColumnPanels();
        this.teamPlayerLists = parts.teamPlayerLists();
        this.spectatorPanel = parts.spectatorPanel();
        this.spectatorList = parts.spectatorList();
        this.readyButton = parts.ready();
        this.adminPanel = parts.adminPanel();
        this.backButton = parts.back();
        this.syncedReadyPlayers.addAll(detail.readyPlayers());
        this.syncedCountdownSeconds = detail.summary().readyCountdownSeconds();

        readyButton.setOnClick(e -> toggleReady());
        parts.back().setOnClick(e -> onClose());
        registerFocusGroup(this::focusTargets);
        // Deferred until init(): ModularUIScreen binds this screen only in init/setScreenAndInit.
        // Building list rows in the constructor would yield empty placeholders.
    }

    @Override
    public void init() {
        super.init();
        applyResponsiveLayout();
        rebuildDynamic();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.drawMapIndex(graphics, width, height);
    }

    @Override
    protected void onDetailApplied() {
        Set<UUID> nextReadyPlayers = detail.readyPlayers();
        boolean readyChanged = !syncedReadyPlayers.equals(nextReadyPlayers);
        syncedReadyPlayers.clear();
        syncedReadyPlayers.addAll(nextReadyPlayers);
        syncedCountdownSeconds = detail.summary().readyCountdownSeconds();

        boolean selectionRemoved = selectedPlayer != null
                && detail.players().stream().noneMatch(p -> p.uuid().equals(selectedPlayer));
        if (selectionRemoved) {
            selectedPlayer = null;
            selectedPlayerTeam = null;
        }

        boolean rosterChanged = readyChanged
                || !detail.teams().equals(renderedTeams)
                || !detail.players().equals(renderedPlayers);
        boolean controlsChanged = !detail.teams().equals(renderedTeams)
                || detail.summary().started() != renderedStarted
                || detail.summary().allowJoinInProgress() != renderedAllowJoinInProgress
                || detail.summary().currentPlayerOp() != renderedCurrentPlayerOp
                || detail.summary().currentPlayerJoined() != renderedCurrentPlayerJoined
                || detail.summary().currentPlayerSpectating() != renderedCurrentPlayerSpectating;

        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        if (width > 0 && height > 0) {
            applyResponsiveLayout();
        }
        if (controlsChanged) {
            rebuildTeamButtons();
        }
        if (controlsChanged || rosterChanged || selectionRemoved) {
            rebuildAdminMoveButtons();
        }
        if (rosterChanged) {
            rebuildRosterLists();
        }
        updateReadySummary();
        updateReadyButton();
        updateSelectedLabel();
        refreshDropTargets();
        rememberRenderedState();
    }

    public void applyReadyState(String gameType, String mapName, int countdownSeconds, Set<UUID> readyPlayers) {
        if (detail == null
                || !detail.summary().gameType().equals(gameType)
                || !detail.summary().mapName().equals(mapName)) {
            return;
        }
        Set<UUID> nextReadyPlayers = readyPlayers == null ? Set.of() : readyPlayers;
        boolean readyChanged = !syncedReadyPlayers.equals(nextReadyPlayers);
        boolean countdownChanged = syncedCountdownSeconds != countdownSeconds;
        if (!readyChanged && !countdownChanged) {
            return;
        }
        this.syncedCountdownSeconds = countdownSeconds;
        this.syncedReadyPlayers.clear();
        this.syncedReadyPlayers.addAll(nextReadyPlayers);
        updateReadySummary();
        updateReadyButton();
        if (readyChanged) {
            rebuildRosterLists();
        }
    }

    private void rebuildDynamic() {
        if (width > 0 && height > 0) {
            applyResponsiveLayout();
        }
        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        rebuildTeamButtons();
        rebuildAdminMoveButtons();
        rebuildRosterLists();
        updateReadySummary();
        updateReadyButton();
        updateSelectedLabel();
        refreshDropTargets();
        rememberRenderedState();
    }

    private void rememberRenderedState() {
        renderedTeams = List.copyOf(detail.teams());
        renderedPlayers = List.copyOf(detail.players());
        renderedStarted = detail.summary().started();
        renderedAllowJoinInProgress = detail.summary().allowJoinInProgress();
        renderedCurrentPlayerOp = detail.summary().currentPlayerOp();
        renderedCurrentPlayerJoined = detail.summary().currentPlayerJoined();
        renderedCurrentPlayerSpectating = detail.summary().currentPlayerSpectating();
    }

    private void rebuildTeamButtons() {
        teamButtonsPanel.clearAllChildren();
        teamButtons.clear();
        List<MapRoomTeamInfo> teams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        if (teams.isEmpty()) {
            return;
        }
        int total = teams.size();
        int columns = Math.max(1, Math.min(teamButtonColumns, total));
        int gap = 5;
        int buttonWidth = Math.max(1,
                (teamButtonsWidth - gap * Math.max(0, columns - 1)) / columns);
        String selfTeam = selfTeamName();
        for (int i = 0; i < teams.size(); i++) {
            MapRoomTeamInfo team = teams.get(i);
            String teamName = team.name();
            boolean current = teamName.equals(selfTeam);
            boolean full = team.isFull();
            boolean active = !current && !full && canSwitchTeam();
            int left = (i % columns) * (buttonWidth + gap);
            int top = (i / columns) * 27;
            AccessibleButton button = new AccessibleButton();
            button.setId("fpsmatch.team_manage.team." + teamName);
            button.setText(Component.literal(teamName));
            button.setAccessibleName(Component.literal(teamName));
            button.setAccessibleState(() -> current
                    ? Component.translatable("gui.fpsm.team_manage.team_count",
                            Component.literal(teamName), team.currentPlayers(),
                            team.playerLimit() < 0 ? "?" : team.playerLimit())
                    : full
                            ? Component.translatable("gui.fpsm.map_select.full")
                            : Component.empty());
            button.setAccessibleHint(() -> Component.translatable(
                    "gui.fpsm.team_manage.move"));
            button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(left).top(top).width(buttonWidth).height(23));
            if (current) {
                FPSMMapSelectTheme.button(button, FPSMMapSelectTheme.ButtonKind.PRIMARY);
                button.setActive(false);
                button.setAllowHitTest(false);
                button.setFocusable(false);
            } else {
                FPSMMapSelectTheme.buttonState(button,
                        FPSMMapSelectTheme.ButtonKind.SECONDARY, active);
            }
            button.textStyle(style -> style.fontSize(9));
            button.setOnClick(e -> {
                if (selectedPlayer != null && detail.summary().currentPlayerOp()) {
                    moveSelectedPlayer(teamName);
                } else {
                    switchSelfTeam(teamName);
                }
            });
            teamButtons.add(button);
            teamButtonsPanel.addChild(button);
        }
    }

    private void rebuildAdminMoveButtons() {
        adminPanel.clearAllChildren();
        adminMoveButtons.clear();
        adminPanel.addChild(selectedLabel);
        boolean show = hasAdminPanel();
        adminPanel.setVisible(show);
        if (!show) {
            return;
        }
        MapRoomPlayerInfo selected = findPlayer(selectedPlayer);
        List<String> targets = TeamActionModel.availableTargetTeams(detail, selectedPlayer);
        boolean canKick = selected != null && TeamActionModel.canKick(detail, selectedPlayer);
        int actionCount = targets.size() + (canKick ? 1 : 0) + 1;
        int columns = Math.max(1, Math.min(adminButtonColumns, actionCount));
        int gap = 5;
        int sidePadding = 8;
        int availableWidth = Math.max(1,
                adminPanelWidth - sidePadding * 2 - gap * Math.max(0, columns - 1));
        int buttonWidth = Math.max(1, availableWidth / columns);
        int index = 0;
        for (String team : targets) {
            String target = team;
            AccessibleButton move = new AccessibleButton();
            move.setId("fpsmatch.team_manage.move." + team);
            move.setText(Component.translatable("gui.fpsm.team_manage.move_btn", team));
            layoutAdminButton(move, index++, columns, buttonWidth, gap, sidePadding);
            FPSMMapSelectTheme.button(move, FPSMMapSelectTheme.ButtonKind.SECONDARY);
            move.textStyle(style -> style.fontSize(9));
            move.setOnClick(e -> moveSelectedPlayer(target));
            adminMoveButtons.add(move);
            adminPanel.addChild(move);
        }

        if (canKick) {
            AccessibleButton kick = new AccessibleButton();
            kick.setId("fpsmatch.team_manage.kick." + selectedPlayer);
            kick.setText(Component.translatable("gui.fpsm.map_select.kick"));
            layoutAdminButton(kick, index++, columns, buttonWidth, gap, sidePadding);
            FPSMMapSelectTheme.button(kick, FPSMMapSelectTheme.ButtonKind.DANGER);
            kick.textStyle(style -> style.fontSize(9));
            kick.setOnClick(event -> {
                UUID target = selectedPlayer;
                if (target != null && TeamActionModel.canKick(detail, target)) {
                    sendAction(MapRoomActionC2SPacket.Action.KICK, target);
                    clearSelection();
                }
            });
            adminMoveButtons.add(kick);
            adminPanel.addChild(kick);
        }

        AccessibleButton cancel = new AccessibleButton();
        cancel.setId("fpsmatch.team_manage.cancel_sel");
        cancel.setText(Component.translatable("gui.fpsm.team_manage.cancel"));
        layoutAdminButton(cancel, index, columns, buttonWidth, gap, sidePadding);
        FPSMMapSelectTheme.button(cancel, FPSMMapSelectTheme.ButtonKind.QUIET);
        cancel.textStyle(style -> style.fontSize(9));
        cancel.setOnClick(e -> clearSelection());
        adminMoveButtons.add(cancel);
        adminPanel.addChild(cancel);
    }

    private static void layoutAdminButton(AccessibleButton button, int index,
                                          int columns, int width, int gap, int padding) {
        int left = padding + (index % columns) * (width + gap);
        int top = 21 + (index / columns) * 27;
        button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top).width(width).height(23));
    }

    private void rebuildRosterLists() {
        List<MapRoomTeamInfo> normalTeams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        columnTeams = splitAcrossColumns(normalTeams);
        for (int i = 0; i < TEAM_COLUMN_COUNT; i++) {
            VirtualScrollerView<Row> list = teamPlayerLists.get(i);
            list.setItems(buildRows(columnTeams.get(i)));
            list.refreshVisibleItems();
            teamColumnPanels.get(i).setVisible(!columnTeams.get(i).isEmpty());
        }

        List<MapRoomTeamInfo> spectatorTeams = detail.teams().stream()
                .filter(MapRoomTeamInfo::spectator)
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        spectatorTeamName = spectatorTeams.stream().map(MapRoomTeamInfo::name).findFirst().orElse(null);
        spectatorList.setItems(buildRows(spectatorTeams));
        spectatorList.refreshVisibleItems();
        spectatorPanel.setVisible(!spectatorTeams.isEmpty());
    }

    private static List<List<MapRoomTeamInfo>> splitAcrossColumns(List<MapRoomTeamInfo> teams) {
        List<List<MapRoomTeamInfo>> columns = new ArrayList<>(TEAM_COLUMN_COUNT);
        List<Integer> playerCounts = new ArrayList<>(TEAM_COLUMN_COUNT);
        for (int i = 0; i < TEAM_COLUMN_COUNT; i++) {
            columns.add(new ArrayList<>());
            playerCounts.add(0);
        }
        for (MapRoomTeamInfo team : teams) {
            int target = 0;
            for (int i = 1; i < TEAM_COLUMN_COUNT; i++) {
                if (playerCounts.get(i) < playerCounts.get(target)) {
                    target = i;
                }
            }
            columns.get(target).add(team);
            playerCounts.set(target, playerCounts.get(target) + Math.max(1, team.currentPlayers()));
        }
        return columns.stream().map(List::copyOf).toList();
    }

    private List<Row> buildRows(List<MapRoomTeamInfo> teams) {
        List<Row> rows = new ArrayList<>();
        for (MapRoomTeamInfo team : teams) {
            rows.add(Row.header(team));
            for (MapRoomPlayerInfo player : playersInTeam(team.name())) {
                rows.add(Row.player(player));
            }
        }
        return rows;
    }

    private UIElement buildRow(Row row) {
        if (row.header()) {
            UIElement header = new UIElement().setId(TEAM_HEADER_PREFIX + row.team().name());
            header.layout(layout -> layout.widthPercent(100).height(TEAM_HEADER_HEIGHT).marginBottom(ROW_GAP));
            FPSMMapSelectTheme.elevated(header);
            String limit = row.team().playerLimit() < 0 ? "?" : Integer.toString(row.team().playerLimit());
            Component teamName = row.team().spectator()
                    ? Component.translatable("gui.fpsm.team_manage.spectators")
                    : Component.literal(row.team().name());
            Label label = label("fpsmatch.team_manage.header.label." + row.team().name(),
                    Component.translatable("gui.fpsm.team_manage.team_count",
                            teamName, row.team().currentPlayers(), limit));
            label.setAllowHitTest(false);
            label.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).top(5).right(6).height(13));
            FPSMMapSelectTheme.sectionTitle(label);
            label.textStyle(style -> style.fontSize(10));
            header.addChild(label);
            return header;
        }

        MapRoomPlayerInfo player = row.player();
        UIElement item = new UIElement().setId(PLAYER_ROW_PREFIX + player.uuid());
        item.layout(layout -> layout.widthPercent(100).height(PLAYER_ROW_HEIGHT).marginBottom(ROW_GAP));
        boolean selected = selectedPlayer != null && selectedPlayer.equals(player.uuid());
        if (selected) {
            FPSMMapSelectTheme.statusSurface(item, FPSMMapSelectTheme.ACCENT);
        } else {
            FPSMMapSelectTheme.elevated(item);
        }

        Component playerText = Component.literal(player.name());
        if (isReady(player.uuid())) {
            playerText = playerText.copy().append("  ")
                    .append(Component.translatable("gui.fpsm.team_manage.ready_mark"));
        }
        Ldlib2PlayerAvatarElement avatar = new Ldlib2PlayerAvatarElement(
                "fpsmatch.team_manage.player.avatar." + player.uuid(), player.uuid(), player.name());
        avatar.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).top(5).width(18).height(18));
        Label name = label("fpsmatch.team_manage.player.name." + player.uuid(), playerText);
        name.setAllowHitTest(false);
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(30).right(7).top(8).height(13));
        if (player.online()) {
            FPSMMapSelectTheme.body(name);
        } else {
            FPSMMapSelectTheme.muted(name);
        }
        name.textStyle(style -> style.fontSize(10));

        AccessibleButton select = new AccessibleButton();
        select.setId("fpsmatch.team_manage.select." + player.uuid());
        select.setAccessibleName(Component.literal(player.name()));
        select.setAccessibleState(() -> {
            Component state = Component.literal(player.teamName()).copy().append(" · ")
                    .append(Component.translatable(player.online()
                            ? "gui.fpsm.map_select.online"
                            : "gui.fpsm.map_select.offline"));
            if (isReady(player.uuid())) {
                state = state.copy().append(" · ").append(Component.translatable(
                        "gui.fpsm.team_manage.ready_mark"));
            }
            return state;
        });
        select.setAccessibleHint(() -> detail.summary().currentPlayerOp()
                ? Component.translatable("gui.fpsm.team_manage.move")
                : Component.empty());
        select.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).top(0).right(0).bottom(0));
        select.buttonStyle(style -> style
                .baseTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                .hoverTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0x205CCFD0))
                .pressedTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0x3045A6A8)));
        select.noText();
        select.setOnClick(e -> {
            if (Minecraft.getInstance().screen instanceof Ldlib2TeamManageScreen screen) {
                if (e.button == 1) {
                    screen.openPlayerActions(player);
                } else {
                    screen.selectPlayer(player);
                }
            }
        });

        item.addChildren(select, avatar, name);
        return item;
    }

    private void openPlayerActions(MapRoomPlayerInfo player) {
        FPSMMapSelectScreens.openChild(new FPSMTeamActionScreen(detail, this, player));
    }

    private void selectPlayer(MapRoomPlayerInfo player) {
        if (!detail.summary().currentPlayerOp() || player.spectator()) {
            return;
        }
        if (selectedPlayer != null && selectedPlayer.equals(player.uuid())) {
            clearSelection();
            return;
        }
        selectedPlayer = player.uuid();
        selectedPlayerTeam = player.teamName();
        rebuildDynamic();
    }

    private void clearSelection() {
        selectedPlayer = null;
        selectedPlayerTeam = null;
        rebuildDynamic();
    }

    private void switchSelfTeam(String teamName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        sendAction(MapRoomActionC2SPacket.Action.SWITCH_TEAM, mc.player.getUUID(), teamName);
    }

    private void moveSelectedPlayer(String targetTeam) {
        if (selectedPlayer == null) {
            return;
        }
        sendAction(MapRoomActionC2SPacket.Action.SWITCH_TEAM, selectedPlayer, targetTeam);
        clearSelection();
    }

    private void toggleReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        sendAction(MapRoomActionC2SPacket.Action.READY, mc.player.getUUID());
    }

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
    }

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target, String data) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target, data));
    }

    private void applyResponsiveLayout() {
        boolean compact = width < 460 || height < 300;
        int margin = Math.min(16, Math.max(8, width / 32));
        int contentWidth = Math.max(1, width - margin * 2);
        int teamCount = (int) detail.teams().stream().filter(team -> !team.spectator()).count();
        teamButtonColumns = Math.max(1, Math.min(teamCount,
                compact ? 2 : Math.max(2, Math.min(5, contentWidth / 92))));
        int teamRows = Math.max(1, (teamCount + teamButtonColumns - 1) / teamButtonColumns);
        int teamsHeight = teamRows * 23 + Math.max(0, teamRows - 1) * 4;

        int headerTop = 8;
        int headerHeight = 21;
        int subtitleTop = 32;
        int subtitleHeight = 13;
        int teamsTop = 50;
        int summaryTop = teamsTop + teamsHeight + 5;
        int summaryHeight = 13;
        int rosterTop = summaryTop + summaryHeight + 5;
        int controlBottom = 8;
        int controlHeight = 26;
        int adminBottom = controlBottom + controlHeight + 6;
        int adminActionCount = adminActionCount();
        adminButtonColumns = Math.max(1, Math.min(adminActionCount,
                Math.max(2, Math.min(5, contentWidth / 82))));
        int adminRows = Math.max(1,
                (adminActionCount + adminButtonColumns - 1) / adminButtonColumns);
        int adminHeight = hasAdminPanel()
                ? 25 + adminRows * 23 + Math.max(0, adminRows - 1) * 4 + 5
                : 0;
        int rosterBottom = adminBottom + (adminHeight > 0 ? adminHeight + 6 : 0);
        int rosterHeight = Math.max(24, height - rosterTop - rosterBottom);

        placeRoot(headerLabel, margin, headerTop, contentWidth, headerHeight);
        placeRoot(subtitleLabel, margin, subtitleTop, contentWidth, subtitleHeight);
        placeRoot(teamButtonsPanel, margin, teamsTop, contentWidth, teamsHeight);
        placeRoot(readySummaryLabel, margin, summaryTop, contentWidth, summaryHeight);
        placeRoot(rosterPanel, margin, rosterTop, contentWidth, rosterHeight);
        placeRoot(adminPanel, margin, height - adminBottom - Math.max(1, adminHeight),
                contentWidth, Math.max(1, adminHeight));

        int readyWidth = Math.min(132, Math.max(82, (contentWidth - 8) * 58 / 100));
        int backWidth = Math.min(104, Math.max(62, contentWidth - readyWidth - 8));
        readyButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().topAuto().left(margin).bottom(controlBottom)
                .width(readyWidth).height(controlHeight));
        backButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().topAuto().left(margin + readyWidth + 8).bottom(controlBottom)
                .width(backWidth).height(controlHeight));
        readyButton.textStyle(style -> style.fontSize(10));
        backButton.textStyle(style -> style.fontSize(10));

        int innerPadding = 7;
        int columnGap = 7;
        int innerWidth = Math.max(1, contentWidth - innerPadding * 2);
        boolean hasSpectators = detail.teams().stream().anyMatch(MapRoomTeamInfo::spectator);
        int spectatorHeight = hasSpectators
                ? Math.min(72, Math.max(30, rosterHeight / (compact ? 3 : 4)))
                : 0;
        int sectionGap = spectatorHeight > 0 ? 6 : 0;
        int columnHeight = Math.max(1, rosterHeight - innerPadding * 2 - spectatorHeight - sectionGap);
        boolean stackColumns = innerWidth < 286;
        if (stackColumns) {
            int stackedHeight = Math.max(1, (columnHeight - columnGap) / 2);
            for (int i = 0; i < teamColumnPanels.size(); i++) {
                placeRoot(teamColumnPanels.get(i), innerPadding,
                        innerPadding + i * (stackedHeight + columnGap),
                        innerWidth, stackedHeight);
            }
        } else {
            int columnWidth = Math.max(1, (innerWidth - columnGap) / TEAM_COLUMN_COUNT);
            for (int i = 0; i < teamColumnPanels.size(); i++) {
                placeRoot(teamColumnPanels.get(i),
                        innerPadding + i * (columnWidth + columnGap), innerPadding,
                        columnWidth, columnHeight);
            }
        }
        spectatorPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(innerPadding).top(innerPadding + columnHeight + sectionGap)
                .width(innerWidth).height(Math.max(1, spectatorHeight)));

        teamButtonsWidth = contentWidth;
        adminPanelWidth = contentWidth;
    }

    private int adminActionCount() {
        if (!hasAdminPanel()) {
            return 1;
        }
        int count = TeamActionModel.availableTargetTeams(detail, selectedPlayer).size() + 1;
        MapRoomPlayerInfo player = findPlayer(selectedPlayer);
        if (player != null && TeamActionModel.canKick(detail, selectedPlayer)) {
            count++;
        }
        return count;
    }

    private static void placeRoot(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top).width(width).height(height));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIElement target = hitElement(mouseX, mouseY);
        if (button == 0 && detail.summary().currentPlayerOp()) {
            MapRoomPlayerInfo player = playerAt(target);
            if (player != null && !TeamActionModel.availableTargetTeams(detail, player.uuid()).isEmpty()) {
                dragState.begin(player.uuid(), player.teamName(), mouseX, mouseY);
            }
        }
        if (target == modularUI.getLastHoveredElement()
                && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (target == null) {
            return false;
        }
        UIEvent event = mouseEvent("mouseDown", target, mouseX, mouseY, button);
        UIEventDispatcher.dispatchEvent(event);
        fallbackClickTarget = event.hasHandler ? target : null;
        return fallbackClickTarget != null;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragState.active()) {
            dragState.update(mouseX, mouseY, teamAt(hitElement(mouseX, mouseY)));
            refreshDropTargets();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragState.active() && dragState.moved()) {
            dragState.update(mouseX, mouseY, teamAt(hitElement(mouseX, mouseY)));
            TeamDragState.Drop drop = dragState.release().orElse(null);
            fallbackClickTarget = null;
            refreshDropTargets();
            if (drop != null && TeamActionModel.canDropTo(detail, drop.player(), drop.targetTeam())) {
                sendAction(MapRoomActionC2SPacket.Action.SWITCH_TEAM, drop.player(), drop.targetTeam());
                if (drop.player().equals(selectedPlayer)) {
                    clearSelection();
                }
            }
            return true;
        }
        dragState.cancel();
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        UIElement pressedTarget = fallbackClickTarget;
        fallbackClickTarget = null;
        if (pressedTarget != null && !handled) {
            UIElement releasedTarget = hitElement(mouseX, mouseY);
            if (releasedTarget != null) {
                UIEvent mouseUp = mouseEvent("mouseUp", releasedTarget, mouseX, mouseY, button);
                UIEventDispatcher.dispatchEvent(mouseUp);
                if (releasedTarget == pressedTarget) {
                    UIEventDispatcher.dispatchEvent(mouseEvent("mouseClick", releasedTarget, mouseX, mouseY, button));
                }
            }
            return true;
        }
        return handled || pressedTarget != null;
    }

    private UIEvent mouseEvent(String type, UIElement target, double mouseX, double mouseY, int button) {
        UIEvent event = UIEvent.create(type);
        event.x = (float) (mouseX - modularUI.getLeftPos());
        event.y = (float) (mouseY - modularUI.getTopPos());
        event.button = button;
        event.target = target;
        return event;
    }

    private UIElement hitElement(double mouseX, double mouseY) {
        var hit = modularUI.ui.rootElement.hitTest(
                mouseX - modularUI.getLeftPos(), mouseY - modularUI.getTopPos());
        return hit == null ? null : hit.getA();
    }

    private MapRoomPlayerInfo playerAt(UIElement target) {
        for (UIElement current = target; current != null; current = current.getParent()) {
            String id = current.getId();
            if (id == null || !id.startsWith(PLAYER_ROW_PREFIX)) {
                continue;
            }
            try {
                return findPlayer(UUID.fromString(id.substring(PLAYER_ROW_PREFIX.length())));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String teamAt(UIElement target) {
        MapRoomPlayerInfo player = playerAt(target);
        if (player != null) {
            return player.teamName();
        }
        for (UIElement current = target; current != null; current = current.getParent()) {
            String id = current.getId();
            if (id == null) {
                continue;
            }
            for (MapRoomTeamInfo team : detail.teams()) {
                if (id.equals(TEAM_HEADER_PREFIX + team.name())
                        || id.equals("fpsmatch.team_manage.team." + team.name())) {
                    return team.name();
                }
            }
            if (current == spectatorPanel) {
                return spectatorTeamName;
            }
            for (int i = 0; i < teamColumnPanels.size(); i++) {
                if (current == teamColumnPanels.get(i) && !columnTeams.get(i).isEmpty()) {
                    return columnTeams.get(i).get(0).name();
                }
            }
        }
        return null;
    }

    private void refreshDropTargets() {
        for (int i = 0; i < teamColumnPanels.size(); i++) {
            boolean hovered = dragState.active() && dragState.moved()
                    && columnTeams.get(i).stream().anyMatch(team -> team.name().equals(dragState.hoverTeam()))
                    && TeamActionModel.canDropTo(detail, dragState.player(), dragState.hoverTeam());
            styleRosterPanel(teamColumnPanels.get(i), hovered);
        }
        boolean spectatorHovered = dragState.active() && dragState.moved()
                && spectatorTeamName != null
                && spectatorTeamName.equals(dragState.hoverTeam())
                && TeamActionModel.canDropTo(detail, dragState.player(), dragState.hoverTeam());
        styleRosterPanel(spectatorPanel, spectatorHovered);
    }

    private static void styleRosterPanel(UIElement panel, boolean dropTarget) {
        panel.style(style -> style.background(FPSMMapSelectTheme.panelTexture(
                FPSMMapSelectTheme.ELEVATED,
                dropTarget ? FPSMMapSelectTheme.ACCENT : FPSMMapSelectTheme.BORDER)));
    }

    private void updateReadySummary() {
        int ready = readyNormalPlayerCount();
        int total = normalPlayerCount();
        Component text = Component.translatable("gui.fpsm.team_manage.ready_summary", ready, total);
        if (syncedCountdownSeconds > 0 && ready == total && total > 0) {
            text = text.copy().append("  ").append(Component.translatable("gui.fpsm.team_manage.countdown", syncedCountdownSeconds));
        }
        readySummaryLabel.setValue(text);
    }

    private void updateReadyButton() {
        Minecraft mc = Minecraft.getInstance();
        boolean joined = detail.summary().currentPlayerJoined();
        boolean ready = mc.player != null && isReady(mc.player.getUUID());
        readyButton.setText(Component.translatable(ready ? "gui.fpsm.team_manage.ready.off" : "gui.fpsm.team_manage.ready.on"));
        FPSMMapSelectTheme.buttonState(readyButton,
                ready ? FPSMMapSelectTheme.ButtonKind.SECONDARY
                        : FPSMMapSelectTheme.ButtonKind.PRIMARY,
                joined && mc.player != null);
    }

    private void updateSelectedLabel() {
        if (selectedPlayer == null) {
            selectedLabel.setValue(Component.empty());
            return;
        }
        MapRoomPlayerInfo player = findPlayer(selectedPlayer);
        if (player == null) {
            selectedLabel.setValue(Component.empty());
            return;
        }
        selectedLabel.setValue(Component.translatable("gui.fpsm.team_manage.selected",
                player.name(), selectedPlayerTeam == null ? "?" : selectedPlayerTeam));
    }

    private boolean hasAdminPanel() {
        return detail.summary().currentPlayerOp() && selectedPlayer != null;
    }

    private boolean canSwitchTeam() {
        return !detail.summary().started() || detail.summary().allowJoinInProgress();
    }

    private boolean isReady(UUID uuid) {
        return syncedReadyPlayers.contains(uuid) || detail.players().stream()
                .anyMatch(p -> p.uuid().equals(uuid) && p.ready());
    }

    private String selfTeamName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "";
        }
        return detail.players().stream()
                .filter(p -> p.uuid().equals(mc.player.getUUID()))
                .map(MapRoomPlayerInfo::teamName)
                .findFirst()
                .orElse("");
    }

    private List<MapRoomPlayerInfo> playersInTeam(String teamName) {
        return detail.players().stream()
                .filter(p -> p.teamName().equalsIgnoreCase(teamName))
                .sorted(Comparator.comparing(MapRoomPlayerInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private MapRoomPlayerInfo findPlayer(UUID uuid) {
        return detail.players().stream().filter(p -> p.uuid().equals(uuid)).findFirst().orElse(null);
    }

    private int normalPlayerCount() {
        return (int) detail.players().stream().filter(p -> !p.spectator()).count();
    }

    private int readyNormalPlayerCount() {
        return (int) detail.players().stream()
                .filter(p -> !p.spectator())
                .filter(p -> isReady(p.uuid()))
                .count();
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.team_manage.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);

        Label header = label("fpsmatch.team_manage.header", Component.translatable("gui.fpsm.team_manage.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(8).height(21));
        FPSMMapSelectTheme.title(header);
        header.textStyle(style -> style.fontSize(16));

        Label subtitle = label("fpsmatch.team_manage.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(26).height(12));
        FPSMMapSelectTheme.mapIdentity(subtitle);
        subtitle.textStyle(style -> style.fontSize(9));

        UIElement teamButtons = new UIElement().setId("fpsmatch.team_manage.teams");
        teamButtons.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(42).height(22));

        Label readySummary = label("fpsmatch.team_manage.ready_summary", Component.empty());
        readySummary.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(68).height(12));
        FPSMMapSelectTheme.status(readySummary, FPSMMapSelectTheme.SUCCESS);
        readySummary.textStyle(style -> style.fontSize(10));

        UIElement roster = new UIElement().setId("fpsmatch.team_manage.roster");
        roster.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(84).bottom(70));
        FPSMMapSelectTheme.panel(roster);

        List<UIElement> columnPanels = new ArrayList<>(TEAM_COLUMN_COUNT);
        List<VirtualScrollerView<Row>> teamLists = new ArrayList<>(TEAM_COLUMN_COUNT);
        for (int i = 0; i < TEAM_COLUMN_COUNT; i++) {
            int index = i;
            UIElement column = new UIElement().setId("fpsmatch.team_manage.column." + i);
            column.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(index == 0 ? 6 : 50).right(index == 0 ? 50 : 6).top(6).bottom(48));
            FPSMMapSelectTheme.elevated(column);
            VirtualScrollerView<Row> list = createRosterList("fpsmatch.team_manage.list." + i);
            column.addChild(list);
            roster.addChild(column);
            columnPanels.add(column);
            teamLists.add(list);
        }

        UIElement spectators = new UIElement().setId("fpsmatch.team_manage.spectators");
        spectators.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(6).right(6).bottom(6).height(38));
        FPSMMapSelectTheme.elevated(spectators);
        VirtualScrollerView<Row> spectatorList = createRosterList("fpsmatch.team_manage.list.spectators");
        spectators.addChild(spectatorList);
        roster.addChild(spectators);

        UIElement admin = new UIElement().setId("fpsmatch.team_manage.admin");
        admin.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).bottom(40).height(28));
        FPSMMapSelectTheme.panel(admin);
        admin.setVisible(false);

        Label selected = label("fpsmatch.team_manage.selected", Component.empty());
        selected.setAllowHitTest(false);
        selected.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).top(2).right(8).height(10));
        FPSMMapSelectTheme.muted(selected);
        selected.textStyle(style -> style.fontSize(9));
        admin.addChild(selected);

        AccessibleButton ready = new AccessibleButton();
        ready.setId("fpsmatch.team_manage.ready");
        ready.setText(Component.translatable("gui.fpsm.team_manage.ready.on"));
        ready.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).bottom(8).width(104).height(20));
        FPSMMapSelectTheme.button(ready, FPSMMapSelectTheme.ButtonKind.PRIMARY);
        ready.textStyle(style -> style.fontSize(10));

        AccessibleButton back = new AccessibleButton();
        back.setId("fpsmatch.team_manage.back");
        back.setText(Component.translatable("gui.back"));
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(128).bottom(8).width(76).height(20));
        FPSMMapSelectTheme.button(back, FPSMMapSelectTheme.ButtonKind.QUIET);
        back.textStyle(style -> style.fontSize(10));

        root.addChildren(header, subtitle, teamButtons, readySummary, roster, admin, ready, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                header, subtitle, readySummary, selected, teamButtons, roster, columnPanels, teamLists,
                spectators, spectatorList, ready, admin, back);
    }

    private static VirtualScrollerView<Row> createRosterList(String id) {
        VirtualScrollerView<Row> list = new VirtualScrollerView<>();
        list.setId(id);
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(4).right(4).top(4).bottom(4));
        FPSMMapSelectTheme.virtualScroller(list);
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(PLAYER_ROW_HEIGHT + ROW_GAP).overscanPixels(48));
        list.setItemUIProvider(row -> {
            if (Minecraft.getInstance().screen instanceof Ldlib2TeamManageScreen screen) {
                return screen.buildRow(row);
            }
            UIElement placeholder = new UIElement();
            placeholder.layout(layout -> layout.widthPercent(100).height(PLAYER_ROW_HEIGHT));
            return placeholder;
        });
        return list;
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private record Parts(ModularUI ui, Label header, Label subtitle, Label readySummary, Label selected,
                         UIElement teamButtons, UIElement rosterPanel, List<UIElement> teamColumnPanels,
                         List<VirtualScrollerView<Row>> teamPlayerLists, UIElement spectatorPanel,
                         VirtualScrollerView<Row> spectatorList, AccessibleButton ready,
                         UIElement adminPanel, AccessibleButton back) {}

    private record Row(boolean header, MapRoomTeamInfo team, MapRoomPlayerInfo player) {
        static Row header(MapRoomTeamInfo team) {
            return new Row(true, team, null);
        }

        static Row player(MapRoomPlayerInfo player) {
            return new Row(false, null, player);
        }
    }
}
