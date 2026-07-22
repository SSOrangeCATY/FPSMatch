package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.math.Size;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMTeamActionScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.phasetranscrystal.fpsmatch.common.client.screen.team.TeamActionModel;
import com.phasetranscrystal.fpsmatch.common.client.screen.team.TeamDragState;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomTeamInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby team management. Server remains authoritative; this UI only requests actions.
 * Operators can drag player rows between team rosters; the server validates every drop.
 */
public final class Ldlib2TeamManageScreen extends Ldlib2MapChildScreen {
    private static final int TEAM_COLUMN_COUNT = 2;
    private static final int TEAM_HEADER_HEIGHT = 19;
    private static final int PLAYER_ROW_HEIGHT = 23;
    private static final int ROW_GAP = 2;
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
    private final Button readyButton;
    private final List<Button> teamButtons = new ArrayList<>();
    private final List<Button> adminMoveButtons = new ArrayList<>();
    private final UIElement adminPanel;
    private final Button backButton;
    private final TeamDragState dragState = new TeamDragState();

    private final Set<UUID> syncedReadyPlayers = new HashSet<>();
    private List<List<MapRoomTeamInfo>> columnTeams = List.of(List.of(), List.of());
    private String spectatorTeamName;
    private UIElement fallbackClickTarget;
    private int teamButtonsWidth = 520;
    private int adminPanelWidth = 520;
    private int syncedCountdownSeconds;
    private UUID selectedPlayer;
    private String selectedPlayerTeam;

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
    protected void onDetailApplied() {
        syncedReadyPlayers.clear();
        syncedReadyPlayers.addAll(detail.readyPlayers());
        syncedCountdownSeconds = detail.summary().readyCountdownSeconds();
        if (selectedPlayer != null && detail.players().stream().noneMatch(p -> p.uuid().equals(selectedPlayer))) {
            clearSelection();
        }
        rebuildDynamic();
    }

    public void applyReadyState(String gameType, String mapName, int countdownSeconds, Set<UUID> readyPlayers) {
        if (detail == null
                || !detail.summary().gameType().equals(gameType)
                || !detail.summary().mapName().equals(mapName)) {
            return;
        }
        this.syncedCountdownSeconds = countdownSeconds;
        this.syncedReadyPlayers.clear();
        this.syncedReadyPlayers.addAll(readyPlayers);
        updateReadySummary();
        updateReadyButton();
        rebuildRosterLists();
    }

    private void rebuildDynamic() {
        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        rebuildTeamButtons();
        rebuildAdminMoveButtons();
        rebuildRosterLists();
        updateReadySummary();
        updateReadyButton();
        updateSelectedLabel();
        refreshDropTargets();
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
        int gap = 6;
        int availableWidth = Math.max(1, teamButtonsWidth - gap * Math.max(0, total - 1));
        int buttonWidth = Math.max(36, Math.min(84, availableWidth / total));
        int totalWidth = buttonWidth * total + gap * Math.max(0, total - 1);
        int startLeft = Math.max(0, (teamButtonsWidth - totalWidth) / 2);
        String selfTeam = selfTeamName();
        for (int i = 0; i < teams.size(); i++) {
            MapRoomTeamInfo team = teams.get(i);
            String teamName = team.name();
            boolean current = teamName.equals(selfTeam);
            boolean full = team.isFull();
            boolean active = !current && !full && canSwitchTeam();
            int left = startLeft + i * (buttonWidth + gap);
            Button button = new Button();
            button.setId("fpsmatch.team_manage.team." + teamName);
            button.setText(Component.literal(teamName.toUpperCase(Locale.ROOT)));
            button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(left).top(0).width(buttonWidth).height(20));
            FPSMLdlib2Theme.button(button, current ? FPSMLdlib2Theme.ButtonKind.PRIMARY : FPSMLdlib2Theme.ButtonKind.SECONDARY);
            button.textStyle(style -> style.fontSize(8));
            button.setActive(active);
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
        List<String> targets = TeamActionModel.availableTargetTeams(detail, selectedPlayer);
        int actionCount = targets.size() + 1;
        int gap = 4;
        int sidePadding = 8;
        int availableWidth = Math.max(1, adminPanelWidth - sidePadding * 2 - gap * Math.max(0, actionCount - 1));
        int buttonWidth = Math.max(24, Math.min(82, availableWidth / actionCount));
        int totalWidth = actionCount * buttonWidth + gap * Math.max(0, actionCount - 1);
        int left = Math.max(sidePadding, (adminPanelWidth - totalWidth) / 2);
        for (String team : targets) {
            String target = team;
            Button move = new Button();
            move.setId("fpsmatch.team_manage.move." + team);
            move.setText(Component.translatable("gui.fpsm.team_manage.move_btn", team.toUpperCase(Locale.ROOT)));
            int l = left;
            move.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(l).bottom(4).width(buttonWidth).height(17));
            FPSMLdlib2Theme.button(move, FPSMLdlib2Theme.ButtonKind.SECONDARY);
            move.textStyle(style -> style.fontSize(8));
            move.setOnClick(e -> moveSelectedPlayer(target));
            adminMoveButtons.add(move);
            adminPanel.addChild(move);
            left += buttonWidth + gap;
        }
        Button cancel = new Button();
            cancel.setId("fpsmatch.team_manage.cancel_sel");
            cancel.setText(Component.translatable("gui.fpsm.team_manage.cancel"));
        int cancelLeft = left;
        cancel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(cancelLeft).bottom(4).width(buttonWidth).height(17));
        FPSMLdlib2Theme.button(cancel, FPSMLdlib2Theme.ButtonKind.QUIET);
        cancel.textStyle(style -> style.fontSize(8));
        cancel.setOnClick(e -> clearSelection());
        adminMoveButtons.add(cancel);
        adminPanel.addChild(cancel);
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
            FPSMLdlib2Theme.elevated(header);
            String limit = row.team().playerLimit() < 0 ? "?" : Integer.toString(row.team().playerLimit());
            Component teamName = row.team().spectator()
                    ? Component.translatable("gui.fpsm.team_manage.spectators")
                    : Component.literal(row.team().name().toUpperCase(Locale.ROOT));
            Label label = label("fpsmatch.team_manage.header.label." + row.team().name(),
                    Component.translatable("gui.fpsm.team_manage.team_count",
                            teamName, row.team().currentPlayers(), limit));
            label.setAllowHitTest(false);
            label.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(7).top(3).right(5).height(12));
            FPSMLdlib2Theme.sectionTitle(label);
            label.textStyle(style -> style.fontSize(10));
            header.addChild(label);
            return header;
        }

        MapRoomPlayerInfo player = row.player();
        UIElement item = new UIElement().setId(PLAYER_ROW_PREFIX + player.uuid());
        item.layout(layout -> layout.widthPercent(100).height(PLAYER_ROW_HEIGHT).marginBottom(ROW_GAP));
        boolean selected = selectedPlayer != null && selectedPlayer.equals(player.uuid());
        if (selected) {
            FPSMLdlib2Theme.panel(item);
        } else {
            FPSMLdlib2Theme.elevated(item);
        }

        boolean canAct = canActOnPlayer(player);
        Component playerText = Component.literal(player.name());
        if (isReady(player.uuid())) {
            playerText = playerText.copy().append("  ")
                    .append(Component.translatable("gui.fpsm.team_manage.ready_mark"));
        }
        Ldlib2PlayerAvatarElement avatar = new Ldlib2PlayerAvatarElement(
                "fpsmatch.team_manage.player.avatar." + player.uuid(), player.uuid(), player.name());
        avatar.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(4).top(3).width(16).height(16));
        Label name = label("fpsmatch.team_manage.player.name." + player.uuid(), playerText);
        name.setAllowHitTest(false);
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(24).right(canAct ? 47 : 5).top(5).height(12));
        if (player.online()) {
            FPSMLdlib2Theme.body(name);
        } else {
            FPSMLdlib2Theme.muted(name);
        }
        name.textStyle(style -> style.fontSize(9));

        Button select = new Button();
        select.setId("fpsmatch.team_manage.select." + player.uuid());
        select.setText(Component.literal(selected ? "*" : " "));
        select.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).top(0).right(0).bottom(0));
        select.buttonStyle(style -> style
                .baseTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                .hoverTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0x204BB3FD))
                .pressedTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0x30246C9D)));
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

        if (canAct) {
            Button kick = new Button();
            kick.setId("fpsmatch.team_manage.kick." + player.uuid());
            kick.setText(Component.translatable("gui.fpsm.map_select.kick"));
            kick.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(3).top(2).width(40).height(18));
            FPSMLdlib2Theme.button(kick, FPSMLdlib2Theme.ButtonKind.DANGER);
            kick.textStyle(style -> style.fontSize(8));
            kick.setOnClick(e -> {
                if (Minecraft.getInstance().screen instanceof Ldlib2TeamManageScreen screen) {
                    screen.sendAction(MapRoomActionC2SPacket.Action.KICK, player.uuid());
                }
            });
            item.addChild(kick);
        }
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
        boolean compact = width < 420 || height < 280;
        int margin = Math.min(16, Math.max(8, width / 32));
        int contentWidth = Math.max(1, width - margin * 2);
        int headerTop = compact ? 6 : 8;
        int headerHeight = compact ? 14 : 16;
        int subtitleTop = headerTop + headerHeight + 2;
        int subtitleHeight = 11;
        int teamsTop = subtitleTop + subtitleHeight + 3;
        int teamsHeight = 20;
        int summaryTop = teamsTop + teamsHeight + 3;
        int summaryHeight = 10;
        int rosterTop = summaryTop + summaryHeight + 4;
        int controlBottom = 8;
        int controlHeight = 20;
        int adminHeight = 28;
        int adminBottom = controlBottom + controlHeight + 4;
        int rosterBottom = adminBottom + adminHeight + 4;
        int rosterHeight = Math.max(24, height - rosterTop - rosterBottom);

        placeRoot(headerLabel, margin, headerTop, contentWidth, headerHeight);
        placeRoot(subtitleLabel, margin, subtitleTop, contentWidth, subtitleHeight);
        placeRoot(teamButtonsPanel, margin, teamsTop, contentWidth, teamsHeight);
        placeRoot(readySummaryLabel, margin, summaryTop, contentWidth, summaryHeight);
        placeRoot(rosterPanel, margin, rosterTop, contentWidth, rosterHeight);
        placeRoot(adminPanel, margin, height - adminBottom - adminHeight, contentWidth, adminHeight);

        int readyWidth = Math.min(104, Math.max(62, (contentWidth - 6) * 58 / 100));
        int backWidth = Math.min(76, Math.max(48, contentWidth - readyWidth - 6));
        readyButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().topAuto().left(margin).bottom(controlBottom)
                .width(readyWidth).height(controlHeight));
        backButton.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().topAuto().left(margin + readyWidth + 6).bottom(controlBottom)
                .width(backWidth).height(controlHeight));

        int innerPadding = 6;
        int columnGap = 6;
        int innerWidth = Math.max(1, contentWidth - innerPadding * 2);
        int columnWidth = Math.max(1, (innerWidth - columnGap) / TEAM_COLUMN_COUNT);
        boolean hasSpectators = detail.teams().stream().anyMatch(MapRoomTeamInfo::spectator);
        int spectatorHeight = hasSpectators
                ? Math.min(64, Math.max(30, rosterHeight / (compact ? 3 : 4)))
                : 0;
        int sectionGap = spectatorHeight > 0 ? 6 : 0;
        int columnHeight = Math.max(1, rosterHeight - innerPadding * 2 - spectatorHeight - sectionGap);
        for (int i = 0; i < teamColumnPanels.size(); i++) {
            int left = innerPadding + i * (columnWidth + columnGap);
            teamColumnPanels.get(i).layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .rightAuto().bottomAuto().left(left).top(innerPadding)
                    .width(columnWidth).height(columnHeight));
        }
        spectatorPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(innerPadding).top(innerPadding + columnHeight + sectionGap)
                .width(innerWidth).height(Math.max(1, spectatorHeight)));

        teamButtonsWidth = contentWidth;
        adminPanelWidth = contentWidth;
    }

    private static void placeRoot(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top).width(width).height(height));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        modularUI.getWidget().mouseMoved(mouseX, mouseY);
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
        panel.style(style -> style.background(FPSMLdlib2Theme.panelTexture(
                FPSMLdlib2Theme.ELEVATED,
                dropTarget ? FPSMLdlib2Theme.ACCENT : FPSMLdlib2Theme.BORDER)));
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
        readyButton.setActive(joined && mc.player != null);
        boolean ready = mc.player != null && isReady(mc.player.getUUID());
        readyButton.setText(Component.translatable(ready ? "gui.fpsm.team_manage.ready.off" : "gui.fpsm.team_manage.ready.on"));
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
                player.name(), selectedPlayerTeam == null ? "?" : selectedPlayerTeam.toUpperCase(Locale.ROOT)));
    }

    private boolean hasAdminPanel() {
        return detail.summary().currentPlayerOp() && selectedPlayer != null;
    }

    private boolean canActOnPlayer(MapRoomPlayerInfo player) {
        return TeamActionModel.canKick(detail, player.uuid());
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
        FPSMLdlib2Theme.root(root);

        Label header = label("fpsmatch.team_manage.header", Component.translatable("gui.fpsm.team_manage.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(8).height(16));
        FPSMLdlib2Theme.title(header);
        header.textStyle(style -> style.fontSize(13));

        Label subtitle = label("fpsmatch.team_manage.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(26).height(12));
        FPSMLdlib2Theme.muted(subtitle);
        subtitle.textStyle(style -> style.fontSize(8));

        UIElement teamButtons = new UIElement().setId("fpsmatch.team_manage.teams");
        teamButtons.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(42).height(22));

        Label readySummary = label("fpsmatch.team_manage.ready_summary", Component.empty());
        readySummary.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(68).height(12));
        FPSMLdlib2Theme.status(readySummary, FPSMLdlib2Theme.SUCCESS);
        readySummary.textStyle(style -> style.fontSize(8));

        UIElement roster = new UIElement().setId("fpsmatch.team_manage.roster");
        roster.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(84).bottom(70));
        FPSMLdlib2Theme.panel(roster);

        List<UIElement> columnPanels = new ArrayList<>(TEAM_COLUMN_COUNT);
        List<VirtualScrollerView<Row>> teamLists = new ArrayList<>(TEAM_COLUMN_COUNT);
        for (int i = 0; i < TEAM_COLUMN_COUNT; i++) {
            int index = i;
            UIElement column = new UIElement().setId("fpsmatch.team_manage.column." + i);
            column.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(index == 0 ? 6 : 50).right(index == 0 ? 50 : 6).top(6).bottom(48));
            FPSMLdlib2Theme.elevated(column);
            VirtualScrollerView<Row> list = createRosterList("fpsmatch.team_manage.list." + i);
            column.addChild(list);
            roster.addChild(column);
            columnPanels.add(column);
            teamLists.add(list);
        }

        UIElement spectators = new UIElement().setId("fpsmatch.team_manage.spectators");
        spectators.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(6).right(6).bottom(6).height(38));
        FPSMLdlib2Theme.elevated(spectators);
        VirtualScrollerView<Row> spectatorList = createRosterList("fpsmatch.team_manage.list.spectators");
        spectators.addChild(spectatorList);
        roster.addChild(spectators);

        UIElement admin = new UIElement().setId("fpsmatch.team_manage.admin");
        admin.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).bottom(40).height(28));
        FPSMLdlib2Theme.panel(admin);
        admin.setVisible(false);

        Label selected = label("fpsmatch.team_manage.selected", Component.empty());
        selected.setAllowHitTest(false);
        selected.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).top(2).right(8).height(10));
        FPSMLdlib2Theme.muted(selected);
        selected.textStyle(style -> style.fontSize(8));
        admin.addChild(selected);

        Button ready = new Button();
        ready.setId("fpsmatch.team_manage.ready");
        ready.setText(Component.translatable("gui.fpsm.team_manage.ready.on"));
        ready.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).bottom(8).width(104).height(20));
        FPSMLdlib2Theme.button(ready, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        ready.textStyle(style -> style.fontSize(8));

        Button back = new Button();
        back.setId("fpsmatch.team_manage.back");
        back.setText(Component.translatable("gui.back"));
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(128).bottom(8).width(76).height(20));
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);
        back.textStyle(style -> style.fontSize(8));

        root.addChildren(header, subtitle, teamButtons, readySummary, roster, admin, ready, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                header, subtitle, readySummary, selected, teamButtons, roster, columnPanels, teamLists,
                spectators, spectatorList, ready, admin, back);
    }

    private static VirtualScrollerView<Row> createRosterList(String id) {
        VirtualScrollerView<Row> list = new VirtualScrollerView<>();
        list.setId(id);
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(4).right(4).top(4).bottom(4));
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
                         VirtualScrollerView<Row> spectatorList, Button ready, UIElement adminPanel, Button back) {}

    private record Row(boolean header, MapRoomTeamInfo team, MapRoomPlayerInfo player) {
        static Row header(MapRoomTeamInfo team) {
            return new Row(true, team, null);
        }

        static Row player(MapRoomPlayerInfo player) {
            return new Row(false, null, player);
        }
    }
}
