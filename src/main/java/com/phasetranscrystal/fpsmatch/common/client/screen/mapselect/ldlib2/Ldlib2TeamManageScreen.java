package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMTeamActionScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.phasetranscrystal.fpsmatch.common.client.screen.team.TeamActionModel;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomTeamInfo;
import net.minecraft.client.Minecraft;
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
 * Drag-drop is intentionally omitted: select player + team buttons / right-click actions.
 */
public final class Ldlib2TeamManageScreen extends Ldlib2MapChildScreen {
    private final Label subtitleLabel;
    private final Label readySummaryLabel;
    private final Label selectedLabel;
    private final UIElement teamButtonsPanel;
    private final VirtualScrollerView<Row> playerList;
    private final Button readyButton;
    private final List<Button> teamButtons = new ArrayList<>();
    private final List<Button> adminMoveButtons = new ArrayList<>();
    private final UIElement adminPanel;

    private final Set<UUID> syncedReadyPlayers = new HashSet<>();
    private int syncedCountdownSeconds;
    private UUID selectedPlayer;
    private String selectedPlayerTeam;

    public Ldlib2TeamManageScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2TeamManageScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.team_manage.title"), detail, parent);
        this.subtitleLabel = parts.subtitle();
        this.readySummaryLabel = parts.readySummary();
        this.selectedLabel = parts.selected();
        this.teamButtonsPanel = parts.teamButtons();
        this.playerList = parts.playerList();
        this.readyButton = parts.ready();
        this.adminPanel = parts.adminPanel();
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
        playerList.setItems(buildRows());
        playerList.refreshVisibleItems();
    }

    private void rebuildDynamic() {
        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        rebuildTeamButtons();
        rebuildAdminMoveButtons();
        playerList.setItems(buildRows());
        playerList.refreshVisibleItems();
        updateReadySummary();
        updateReadyButton();
        updateSelectedLabel();
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
        int buttonWidth = Math.min(96, Math.max(64, (int) ((520.0 - (total - 1) * 8) / total)));
        String selfTeam = selfTeamName();
        for (int i = 0; i < teams.size(); i++) {
            MapRoomTeamInfo team = teams.get(i);
            String teamName = team.name();
            boolean current = teamName.equals(selfTeam);
            boolean full = team.isFull();
            boolean active = !current && !full && canSwitchTeam();
            int left = i * (buttonWidth + 8);
            Button button = new Button();
            button.setId("fpsmatch.team_manage.team." + teamName);
            button.setText(Component.literal(teamName.toUpperCase(Locale.ROOT)));
            button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(left).top(0).width(buttonWidth).height(24));
            FPSMLdlib2Theme.button(button, current ? FPSMLdlib2Theme.ButtonKind.PRIMARY : FPSMLdlib2Theme.ButtonKind.SECONDARY);
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
        int left = 12;
        for (String team : targets) {
            String target = team;
            Button move = new Button();
            move.setId("fpsmatch.team_manage.move." + team);
            move.setText(Component.translatable("gui.fpsm.team_manage.move_btn", team.toUpperCase(Locale.ROOT)));
            int l = left;
            move.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(l).bottom(8).width(100).height(22));
            FPSMLdlib2Theme.button(move, FPSMLdlib2Theme.ButtonKind.SECONDARY);
            move.setOnClick(e -> moveSelectedPlayer(target));
            adminMoveButtons.add(move);
            adminPanel.addChild(move);
            left += 108;
        }
        Button cancel = new Button();
            cancel.setId("fpsmatch.team_manage.cancel_sel");
            cancel.setText(Component.translatable("gui.fpsm.team_manage.cancel"));
        int cancelLeft = left;
        cancel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(cancelLeft).bottom(8).width(88).height(22));
        FPSMLdlib2Theme.button(cancel, FPSMLdlib2Theme.ButtonKind.QUIET);
        cancel.setOnClick(e -> clearSelection());
        adminMoveButtons.add(cancel);
        adminPanel.addChild(cancel);
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        List<MapRoomTeamInfo> teams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        for (MapRoomTeamInfo team : teams) {
            rows.add(Row.header(team));
            for (MapRoomPlayerInfo player : playersInTeam(team.name())) {
                rows.add(Row.player(player));
            }
        }
        List<MapRoomPlayerInfo> spectators = detail.players().stream()
                .filter(MapRoomPlayerInfo::spectator)
                .sorted(Comparator.comparing(MapRoomPlayerInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!spectators.isEmpty()) {
            rows.add(Row.header(new MapRoomTeamInfo("spec", spectators.size(), -1, true)));
            for (MapRoomPlayerInfo player : spectators) {
                rows.add(Row.player(player));
            }
        }
        return rows;
    }

    private UIElement buildRow(Row row) {
        if (row.header()) {
            UIElement header = new UIElement().setId("fpsmatch.team_manage.header." + row.team().name());
            header.layout(layout -> layout.widthPercent(100).height(22));
            FPSMLdlib2Theme.elevated(header);
            String limit = row.team().playerLimit() < 0 ? "?" : Integer.toString(row.team().playerLimit());
            Label label = label("fpsmatch.team_manage.header.label." + row.team().name(),
                    Component.literal(row.team().name().toUpperCase(Locale.ROOT)
                            + "  " + row.team().currentPlayers() + "/" + limit));
            label.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).top(4).right(10).height(14));
            FPSMLdlib2Theme.sectionTitle(label);
            header.addChild(label);
            return header;
        }

        MapRoomPlayerInfo player = row.player();
        UIElement item = new UIElement().setId("fpsmatch.team_manage.player." + player.uuid());
        item.layout(layout -> layout.widthPercent(100).height(26));
        boolean selected = selectedPlayer != null && selectedPlayer.equals(player.uuid());
        if (selected) {
            FPSMLdlib2Theme.panel(item);
        } else {
            FPSMLdlib2Theme.elevated(item);
        }

        String readyMark = isReady(player.uuid()) ? " * " + Component.translatable("gui.fpsm.team_manage.ready_mark").getString() : "";
        Label name = label("fpsmatch.team_manage.player.name." + player.uuid(),
                Component.literal(player.name() + readyMark));
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).top(6).width(180).height(14));
        FPSMLdlib2Theme.body(name);

        Label team = label("fpsmatch.team_manage.player.team." + player.uuid(),
                Component.literal(player.teamName().toUpperCase(Locale.ROOT)));
        team.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(200).top(6).width(80).height(14));
        FPSMLdlib2Theme.muted(team);

        Button select = new Button();
        select.setId("fpsmatch.team_manage.select." + player.uuid());
        select.setText(Component.literal(selected ? "*" : " "));
        select.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).top(0).right(0).bottom(0));
        select.buttonStyle(style -> style
                .baseTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                .hoverTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                .pressedTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
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

        item.addChildren(select, name, team);

        if (canActOnPlayer(player)) {
            Button kick = new Button();
            kick.setId("fpsmatch.team_manage.kick." + player.uuid());
            kick.setText(Component.translatable("gui.fpsm.map_select.kick"));
            kick.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(8).top(2).width(64).height(22));
            FPSMLdlib2Theme.button(kick, FPSMLdlib2Theme.ButtonKind.DANGER);
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
                .filter(p -> !p.spectator() && p.teamName().equalsIgnoreCase(teamName))
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
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(10).height(18));
        FPSMLdlib2Theme.title(header);

        Label subtitle = label("fpsmatch.team_manage.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(30).height(14));
        FPSMLdlib2Theme.muted(subtitle);

        UIElement teamButtons = new UIElement().setId("fpsmatch.team_manage.teams");
        teamButtons.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(48).height(28));

        Label readySummary = label("fpsmatch.team_manage.ready_summary", Component.empty());
        readySummary.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(80).height(14));
        FPSMLdlib2Theme.status(readySummary, FPSMLdlib2Theme.SUCCESS);

        UIElement panel = new UIElement().setId("fpsmatch.team_manage.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(98).bottom(88));
        FPSMLdlib2Theme.panel(panel);

        VirtualScrollerView<Row> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.team_manage.list");
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).right(8).top(8).bottom(8));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(26f));
        list.setItemUIProvider(row -> {
            if (Minecraft.getInstance().screen instanceof Ldlib2TeamManageScreen screen) {
                return screen.buildRow(row);
            }
            // Fallback before screen is attached
            UIElement placeholder = new UIElement();
            placeholder.layout(layout -> layout.widthPercent(100).height(22));
            return placeholder;
        });
        panel.addChild(list);

        UIElement admin = new UIElement().setId("fpsmatch.team_manage.admin");
        admin.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).bottom(48).height(36));
        FPSMLdlib2Theme.panel(admin);
        admin.setVisible(false);

        Label selected = label("fpsmatch.team_manage.selected", Component.empty());
        selected.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).top(4).right(12).height(14));
        FPSMLdlib2Theme.muted(selected);
        admin.addChild(selected);

        Button ready = new Button();
            ready.setId("fpsmatch.team_manage.ready");
            ready.setText(Component.translatable("gui.fpsm.team_manage.ready.on"));
        ready.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).bottom(14).width(120).height(24));
        FPSMLdlib2Theme.button(ready, FPSMLdlib2Theme.ButtonKind.PRIMARY);

        Button back = new Button();
        back.setId("fpsmatch.team_manage.back");
        back.setText(Component.translatable("gui.back"));
        back.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(148).bottom(14).width(96).height(24));
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);

        root.addChildren(header, subtitle, teamButtons, readySummary, panel, admin, ready, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                subtitle, readySummary, selected, teamButtons, list, ready, admin, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private record Parts(ModularUI ui, Label subtitle, Label readySummary, Label selected, UIElement teamButtons,
                         VirtualScrollerView<Row> playerList, Button ready, UIElement adminPanel, Button back) {}

    private record Row(boolean header, MapRoomTeamInfo team, MapRoomPlayerInfo player) {
        static Row header(MapRoomTeamInfo team) {
            return new Row(true, team, null);
        }

        static Row player(MapRoomPlayerInfo player) {
            return new Row(false, null, player);
        }
    }
}
