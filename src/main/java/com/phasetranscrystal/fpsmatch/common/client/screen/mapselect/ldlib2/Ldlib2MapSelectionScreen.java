package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMTeamManageScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapDetailChildScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.CloseMapViewC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** LDLib2 map-room browser and detail view. */
public final class Ldlib2MapSelectionScreen extends ModularUIScreen implements FPSMMapDetailChildScreen {
    private final Screen parent;
    private final VirtualScrollerView<MapRoomSummary> roomList;
    private final UIElement detailPanel;
    private final Label detailLabel;
    private final VirtualScrollerView<MapRoomPlayerInfo> playerList;
    private final TextField search;
    private final UIElement filters;
    private final UIElement actions;
    private final UIElement toast;
    private final List<Button> actionButtons;
    private final List<Button> filterButtons;
    private MapSelectionSnapshotS2CPacket snapshot;
    private MapRoomDetail detail;
    private MapRoomSummary selected;
    private String query = "";
    private String stateFilter = "all";

    public Ldlib2MapSelectionScreen(MapSelectionSnapshotS2CPacket snapshot, Screen parent) {
        this(build(snapshot), snapshot, parent);
    }

    private Ldlib2MapSelectionScreen(Parts parts, MapSelectionSnapshotS2CPacket snapshot, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.title"));
        this.parent = parent;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.roomList = parts.roomList();
        this.detailPanel = parts.detailPanel();
        this.detailLabel = parts.detailLabel();
        this.playerList = parts.playerList();
        this.search = parts.search();
        this.filters = parts.filters();
        this.actions = parts.actions();
        this.toast = parts.toast();
        this.actionButtons = parts.actionButtons();
        this.filterButtons = parts.filterButtons();
        parts.refresh().setOnClick(event -> refresh());
        parts.close().setOnClick(event -> onClose());
        parts.detail().setOnClick(event -> requestDetail());
        parts.join().setOnClick(event -> joinOrOpenManage());
        parts.leave().setOnClick(event -> leave());
        parts.manage().setOnClick(event -> openTeamManage());
        parts.filterAll().setOnClick(event -> setStateFilter("all"));
        parts.filterWaiting().setOnClick(event -> setStateFilter("waiting"));
        parts.filterRunning().setOnClick(event -> setStateFilter("running"));
        parts.filterOpen().setOnClick(event -> setStateFilter("open"));
        refreshList();
        refreshDetail();
        refreshActionState();
        refreshFilterState();
        refreshToast();
    }

    @Override
    public void init() {
        super.init();
        // Element IDs are registered only after ModularUI.setScreenAndInit (super.init).
        bindRequiredWidgets();
        applyResponsiveLayout();
    }

    @Override
    public void tick() {
        super.tick();
        modularUI.tick();
        refreshToast();
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        if (selected == null || !sameRoom(selected, detail.summary())) {
            selected = summaryFor(detail.summary());
        }
        refreshDetail();
        refreshActionState();
    }

    public void applySnapshot(MapSelectionSnapshotS2CPacket snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (selected != null) {
            selected = summaryFor(selected);
        }
        if (selected == null || (detail != null && summaryFor(detail.summary()) == null)) {
            detail = null;
        }
        refreshList();
        refreshDetail();
        refreshActionState();
        refreshFilterState();
        refreshToast();
    }

    @Override
    public void onClose() {
        FPSMatch.sendToServer(new CloseMapViewC2SPacket());
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // ModularUIScreen leaves the world undimmed; map select needs a readable backdrop.
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        modularUI.onRemoved();
        super.removed();
    }

    private void refreshList() {
        roomList.setItems(snapshot.maps().stream()
                .filter(this::matchesQuery)
                .filter(this::matchesStateFilter)
                .toList());
        roomList.refreshVisibleItems();
    }

    private void refreshDetail() {
        if (selected == null && detail == null) {
            detailPanel.setVisible(true);
            detailLabel.setValue(Component.translatable("gui.fpsm.map_select.empty"));
            playerList.setItems(List.of());
            return;
        }
        MapRoomSummary summary = detail == null ? selected : detail.summary();
        String text = summary.displayName() + "\n" + summary.gameType().toUpperCase(java.util.Locale.ROOT) + " / " + summary.mapName()
                + "\n" + Component.translatable("gui.fpsm.map_select.players", summary.joinedPlayers(), maxPlayers(summary)).getString();
        if (detail != null) {
            text += "\n" + Component.translatable("gui.fpsm.map_select.players.title").getString() + ": " + detail.players().size();
        }
        detailLabel.setValue(Component.literal(text));
        playerList.setItems(detail == null ? List.of() : detail.players());
        playerList.refreshVisibleItems();
    }

    private void applyResponsiveLayout() {
        int canvasWidth = Math.max(320, width - 24);
        int canvasHeight = Math.max(240, height - 20);
        MapSelectionLayoutModel layout = MapSelectionLayoutModel.responsive(canvasWidth, canvasHeight);
        int originX = 12;
        int originY = 10;
        place(filters, layout.filters(), originX, originY, 8);
        place(roomList, layout.roomList(), originX, originY, 8);
        place(detailPanel, layout.detail(), originX, originY, 8);
        place(actions, layout.actions(), originX, originY, 4);
        if (layout.compact()) {
            search.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                    .left(originX + 8).top(originY + layout.filters().y() + 7)
                    .width(Math.max(120, layout.filters().width() - 16)).height(24));
            layoutActionsCompact();
        } else {
            search.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                    .left(originX + layout.filters().x() + 8)
                    .top(originY + layout.filters().y() + 8)
                    .width(Math.max(120, layout.filters().width() - 16)).height(24));
            layoutActionsDesktop();
        }
    }

    private static void place(UIElement element, MapSelectionLayoutModel.Rect rect, int originX, int originY, int inset) {
        element.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .left(originX + rect.x() + inset)
                .top(originY + rect.y() + inset)
                .width(Math.max(1, rect.width() - inset * 2))
                .height(Math.max(1, rect.height() - inset * 2)));
    }

    private void layoutActionsDesktop() {
        int width = 88;
        int gap = 6;
        for (int i = 0; i < actionButtons.size(); i++) {
            int index = i;
            actionButtons.get(i).layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                    .left(8 + index * (width + gap)).top(5).width(width).height(24));
        }
    }

    private void layoutActionsCompact() {
        int columns = 3;
        for (int i = 0; i < actionButtons.size(); i++) {
            int index = i;
            actionButtons.get(i).layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                    .left(6 + (index % columns) * 96).top(3 + (index / columns) * 25).width(90).height(22));
        }
    }

    private void select(MapRoomSummary summary) {
        selected = summary;
        detail = null;
        refreshDetail();
        refreshActionState();
    }

    private void requestDetail() {
        if (selected == null) return;
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.REQUEST_DETAIL,
                selected.gameType(), selected.mapName(), new UUID(0L, 0L)));
    }

    private void leave() {
        MapRoomSummary summary = selected;
        if (summary == null) return;
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.LEAVE,
                summary.gameType(), summary.mapName(), new UUID(0L, 0L)));
    }

    private void refresh() {
        FPSMatch.sendToServer(new OpenMapSelectionC2SPacket());
    }

    private void joinOrOpenManage() {
        if (detail != null && currentPlayerJoined()) {
            openTeamManage();
            return;
        }
        if (selected == null) return;
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.JOIN,
                selected.gameType(), selected.mapName(), new UUID(0L, 0L)));
    }

    private boolean matchesQuery(MapRoomSummary summary) {
        if (query.isBlank()) return true;
        String normalized = query.toLowerCase(java.util.Locale.ROOT);
        return summary.displayName().toLowerCase(java.util.Locale.ROOT).contains(normalized)
                || summary.mapName().toLowerCase(java.util.Locale.ROOT).contains(normalized)
                || summary.gameType().toLowerCase(java.util.Locale.ROOT).contains(normalized);
    }

    private boolean matchesStateFilter(MapRoomSummary summary) {
        return switch (stateFilter) {
            case "waiting" -> !summary.started();
            case "running" -> summary.started();
            case "open" -> !summary.full() && (!summary.started() || summary.allowJoinInProgress());
            default -> true;
        };
    }

    private void setStateFilter(String filter) {
        stateFilter = filter;
        refreshList();
        refreshFilterState();
    }

    private void refreshFilterState() {
        String[] filterIds = {"all", "waiting", "running", "open"};
        for (int i = 0; i < filterButtons.size() && i < filterIds.length; i++) {
            boolean selected = filterIds[i].equals(stateFilter);
            Button button = filterButtons.get(i);
            button.setActive(true);
            FPSMLdlib2Theme.button(button, selected ? FPSMLdlib2Theme.ButtonKind.PRIMARY : FPSMLdlib2Theme.ButtonKind.QUIET);
        }
    }

    private boolean currentPlayerJoined() {
        return detail != null && (detail.summary().currentPlayerJoined() || detail.summary().currentPlayerSpectating());
    }

    private void refreshActionState() {
        MapRoomSummary summary = selected == null ? null : summaryFor(selected);
        boolean hasSelection = summary != null;
        boolean joined = summary != null && (summary.currentPlayerJoined() || summary.currentPlayerSpectating());
        boolean canJoin = hasSelection && !joined && !summary.full()
                && (!summary.started() || summary.allowJoinInProgress());
        boolean canManage = hasSelection && joined && snapshot.viewerOp()
                && !"csdm".equalsIgnoreCase(summary.gameType());
        // detail, join, leave, manage, refresh, close
        actionButtons.get(0).setActive(hasSelection);
        actionButtons.get(1).setActive(canJoin || (joined && detail != null));
        actionButtons.get(2).setActive(joined);
        actionButtons.get(3).setActive(canManage && detail != null);
        actionButtons.get(4).setActive(true);
        actionButtons.get(5).setActive(true);
    }

    public void applyToast() {
        refreshToast();
    }

    private void refreshToast() {
        FPSMClient.getGlobalData().getMapRoomToast().ifPresentOrElse(packet -> {
            toast.setVisible(true);
            if (toast.getChildren().isEmpty()) {
                Label label = label(MapSelectionWidgetCatalog.TOAST + ".text", packet.message());
                label.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).right(8).top(5).height(18));
                FPSMLdlib2Theme.status(label, packet.error() ? FPSMLdlib2Theme.DANGER : FPSMLdlib2Theme.SUCCESS);
                toast.addChild(label);
            } else if (toast.getChildren().get(0) instanceof Label label) {
                label.setValue(packet.message());
                FPSMLdlib2Theme.status(label, packet.error() ? FPSMLdlib2Theme.DANGER : FPSMLdlib2Theme.SUCCESS);
            }
        }, () -> toast.setVisible(false));
    }

    private void openTeamManage() {
        if (detail != null && !"csdm".equals(detail.summary().gameType())) {
            FPSMMapSelectScreens.openChild(new FPSMTeamManageScreen(detail, this));
        }
    }

    private MapRoomSummary summaryFor(MapRoomSummary source) {
        return snapshot.maps().stream().filter(summary -> sameRoom(summary, source)).findFirst().orElse(null);
    }

    private static boolean sameRoom(MapRoomSummary first, MapRoomSummary second) {
        return first != null && second != null && first.gameType().equals(second.gameType()) && first.mapName().equals(second.mapName());
    }

    private static String maxPlayers(MapRoomSummary summary) {
        return summary.maxPlayers() < 0 ? "?" : Integer.toString(summary.maxPlayers());
    }

    private static Parts build(MapSelectionSnapshotS2CPacket snapshot) {
        UIElement root = new UIElement().setId(MapSelectionWidgetCatalog.ROOT);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        Label header = label(MapSelectionWidgetCatalog.HEADER, Component.translatable("gui.fpsm.map_select.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(10).height(22));
        FPSMLdlib2Theme.title(header);

        TextField search = new TextField();
        search.setId(MapSelectionWidgetCatalog.SEARCH);
        search.setAnyString();
        search.setText("");
        search.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).top(38).width(180).height(22));
        FPSMLdlib2Theme.input(search, Component.translatable("gui.fpsm.map_select.search"));

        VirtualScrollerView<MapRoomSummary> roomList = new VirtualScrollerView<>();
        roomList.setId(MapSelectionWidgetCatalog.ROOM_LIST);
        roomList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).top(68).width(320).height(220));
        FPSMLdlib2Theme.panel(roomList);
        roomList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(54).overscanPixels(108));

        UIElement detailPanel = new UIElement().setId(MapSelectionWidgetCatalog.ROOM_DETAIL);
        detailPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(350).top(68).width(260).height(220));
        FPSMLdlib2Theme.panel(detailPanel);
        Label detailLabel = label("fpsmatch.map_selection.detail.text", Component.translatable("gui.fpsm.map_select.empty"));
        detailLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).right(12).top(12).height(86));
        FPSMLdlib2Theme.body(detailLabel);
        VirtualScrollerView<MapRoomPlayerInfo> playerList = new VirtualScrollerView<>();
        playerList.setId(MapSelectionWidgetCatalog.PLAYERS);
        playerList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).right(12).top(104).bottom(12));
        FPSMLdlib2Theme.elevated(playerList);
        playerList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(24).overscanPixels(48));
        playerList.setItemUIProvider(info -> {
            Label row = new Label();
            row.setId(MapSelectionWidgetCatalog.PLAYERS + "." + info.uuid());
            row.setValue(Component.literal(info.name() + "  " + info.teamName()));
            row.layout(layout -> layout.height(24).widthPercent(100).paddingLeft(8).paddingRight(8));
            FPSMLdlib2Theme.muted(row);
            row.style(style -> style.background(FPSMLdlib2Theme.panelTexture(0xFF182029, FPSMLdlib2Theme.BORDER_SOFT)));
            return row;
        });
        detailPanel.addChild(playerList);
        UIElement filters = new UIElement().setId(MapSelectionWidgetCatalog.FILTERS);
        filters.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).top(52).width(180).height(220));
        FPSMLdlib2Theme.panel(filters);
        UIElement toast = new UIElement().setId(MapSelectionWidgetCatalog.TOAST).setVisible(false);
        toast.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(38).height(28));
        FPSMLdlib2Theme.panel(toast);
        detailPanel.addChild(detailLabel);

        Button filterAll = button(MapSelectionWidgetCatalog.FILTERS + ".all", "gui.fpsm.map_select.filter.all", 8, event -> {});
        Button filterWaiting = button(MapSelectionWidgetCatalog.FILTERS + ".waiting", "gui.fpsm.map_select.filter.waiting", 8, event -> {});
        Button filterRunning = button(MapSelectionWidgetCatalog.FILTERS + ".running", "gui.fpsm.map_select.filter.running", 8, event -> {});
        Button filterOpen = button(MapSelectionWidgetCatalog.FILTERS + ".open", "gui.fpsm.map_select.filter.open", 8, event -> {});
        List<Button> filterButtons = List.of(filterAll, filterWaiting, filterRunning, filterOpen);
        for (int i = 0; i < filterButtons.size(); i++) {
            int index = i;
            Button filter = filterButtons.get(i);
            filter.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).right(8).top(10 + index * 27).height(22));
            FPSMLdlib2Theme.button(filter, FPSMLdlib2Theme.ButtonKind.QUIET);
        }
        filters.addChildren(filterAll, filterWaiting, filterRunning, filterOpen);

        Button detail = button(MapSelectionWidgetCatalog.ACTIONS + ".detail", "gui.fpsm.map_select.detail", 18, event -> {});
        Button join = button(MapSelectionWidgetCatalog.ACTIONS + ".join", "gui.fpsm.map_select.join", 104, event -> {});
        Button leave = button(MapSelectionWidgetCatalog.ACTIONS + ".leave", "gui.fpsm.map_select.leave", 190, event -> {});
        Button manage = button(MapSelectionWidgetCatalog.ACTIONS + ".manage", "gui.fpsm.map_select.manage", 276, event -> {});
        Button refresh = button(MapSelectionWidgetCatalog.ACTIONS + ".refresh", "gui.fpsm.map_select.refresh", 362, event -> {});
        Button close = button(MapSelectionWidgetCatalog.ACTIONS + ".close", "gui.done", 448, event -> {});
        for (Button action : List.of(detail, join, leave, manage, refresh, close)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).bottom(14).width(78).height(22));
        }
        FPSMLdlib2Theme.button(detail, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        FPSMLdlib2Theme.button(join, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        FPSMLdlib2Theme.button(leave, FPSMLdlib2Theme.ButtonKind.DANGER);
        FPSMLdlib2Theme.button(manage, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        FPSMLdlib2Theme.button(refresh, FPSMLdlib2Theme.ButtonKind.QUIET);
        FPSMLdlib2Theme.button(close, FPSMLdlib2Theme.ButtonKind.QUIET);
        UIElement actions = new UIElement().setId(MapSelectionWidgetCatalog.ACTIONS);
        FPSMLdlib2Theme.panel(actions);
        actions.addChildren(detail, join, leave, manage, refresh, close);
        root.addChildren(header, filters, search, roomList, detailPanel, toast, actions);

        List<Button> actionButtons = List.of(detail, join, leave, manage, refresh, close);
        List<Button> filterButtonList = List.of(filterAll, filterWaiting, filterRunning, filterOpen);
        Parts parts = new Parts(ModularUI.of(UI.of(root, screenSize -> Size.of(screenSize.getWidth(), screenSize.getHeight()))), roomList, detailPanel, detailLabel, playerList,
                search, filters, actions, toast, actionButtons, filterButtonList, refresh, close, detail, join, leave, manage,
                filterAll, filterWaiting, filterRunning, filterOpen);
        roomList.setItemUIProvider((UIElementProvider<MapRoomSummary>) summary -> {
            Button row = new Button().noText();
        row.setId(MapSelectionWidgetCatalog.ROOM_LIST + "." + summary.gameType() + "." + summary.mapName());
            row.setOnClick(event -> parts.screenSelect(summary));
            row.layout(layout -> layout.height(54).widthPercent(100).marginBottom(5));
            FPSMLdlib2Theme.roomRow(row, roomStatusColor(summary), parts.isSelected(summary));
            UIElement status = new UIElement();
            status.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).top(0).bottom(0).width(3));
            status.style(style -> style.background(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(roomStatusColor(summary))));
            Ldlib2MapThumbnailElement thumbnail = new Ldlib2MapThumbnailElement(
                    row.getElementName() + ".preview", "", summary.mapName(), summary.gameType(), summary.displayName());
            thumbnail.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(9).top(7).width(40).height(40));
            Label name = label(row.getElementName() + ".name", Component.literal(summary.displayName()));
            name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(58).right(72).top(8).height(16));
            FPSMLdlib2Theme.sectionTitle(name);
            Label meta = label(row.getElementName() + ".meta",
                    Component.literal(summary.gameType().toUpperCase(java.util.Locale.ROOT) + "  " + summary.mapName()));
            meta.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(58).right(72).top(29).height(14));
            FPSMLdlib2Theme.muted(meta);
            Label players = label(row.getElementName() + ".players",
                    Component.literal(summary.joinedPlayers() + "/" + maxPlayers(summary)));
            players.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(10).top(9).width(52).height(14));
            FPSMLdlib2Theme.status(players, summary.full() ? FPSMLdlib2Theme.DANGER : FPSMLdlib2Theme.TEXT);
            Label state = label(row.getElementName() + ".status", statusText(summary));
            state.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(10).top(29).width(62).height(14));
            FPSMLdlib2Theme.status(state, roomStatusColor(summary));
            row.addChildren(status, thumbnail, name, meta, players, state);
            return row;
        });
        search.setTextResponder(value -> {
            if (parts.ui().getScreen() instanceof Ldlib2MapSelectionScreen screen) {
                screen.query = value == null ? "" : value.trim();
                screen.refreshList();
            }
        });
        return parts;
    }

    private static int roomStatusColor(MapRoomSummary summary) {
        if (summary.full()) return FPSMLdlib2Theme.DANGER;
        if (summary.debug()) return FPSMLdlib2Theme.ACCENT;
        if (summary.started()) return summary.allowJoinInProgress() ? FPSMLdlib2Theme.WARNING : FPSMLdlib2Theme.DANGER;
        return FPSMLdlib2Theme.SUCCESS;
    }

    private static Component statusText(MapRoomSummary summary) {
        if (summary.full()) return Component.translatable("gui.fpsm.map_select.full");
        if (summary.debug()) return Component.translatable("gui.fpsm.map_select.status.debug");
        if (summary.started()) return Component.translatable(summary.allowJoinInProgress()
                ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private void bindRequiredWidgets() {
        if (!modularUI.hasElementWithId(MapSelectionWidgetCatalog.ROOT)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.ROOM_LIST)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.ROOM_DETAIL)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.PLAYERS)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.FILTERS)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.TOAST)) {
            throw new IllegalStateException("Incomplete LDLib2 map selection UI");
        }
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private static Button button(String id, String key, int left, com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener listener) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(key));
        button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(left));
        button.setOnClick(listener);
        return button;
    }

    private record Parts(ModularUI ui, VirtualScrollerView<MapRoomSummary> roomList, UIElement detailPanel,
                         Label detailLabel, VirtualScrollerView<MapRoomPlayerInfo> playerList, TextField search,
                         UIElement filters, UIElement actions, UIElement toast, List<Button> actionButtons,
                         List<Button> filterButtons,
                         Button refresh, Button close, Button detail,
                         Button join, Button leave, Button manage, Button filterAll, Button filterWaiting,
                         Button filterRunning, Button filterOpen) {
        private void screenSelect(MapRoomSummary summary) {
            if (ui.getScreen() instanceof Ldlib2MapSelectionScreen screen) screen.select(summary);
        }

        private boolean isSelected(MapRoomSummary summary) {
            return ui.getScreen() instanceof Ldlib2MapSelectionScreen screen && sameRoom(screen.selected, summary);
        }
    }
}
