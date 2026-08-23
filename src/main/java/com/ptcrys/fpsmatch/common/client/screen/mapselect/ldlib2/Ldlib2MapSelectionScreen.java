package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapDetailChildScreen;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.packet.mapselect.CloseMapViewC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** LDLib2 map-room browser and detail view. */
public final class Ldlib2MapSelectionScreen extends ModularUIScreen implements FPSMMapDetailChildScreen {
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 3;
    private static final int PANEL_HORIZONTAL_INSET = 3;
    private static final int PANEL_VERTICAL_INSET = 6;
    private static final int DETAIL_INFO_HEIGHT = 56;
    private static final int DETAIL_PLAYERS_MIN_HEIGHT = 24;

    private final Screen parent;
    private final VirtualScrollerView<MapRoomSummary> roomList;
    private final UIElement detailPanel;
    private final Label detailLabel;
    private final VirtualScrollerView<MapRoomPlayerInfo> playerList;
    private final TextField search;
    private final UIElement filters;
    private final Selector<String> stateSelector;
    private final Selector<String> modeSelector;
    private final UIElement actions;
    private final UIElement browserActions;
    private final UIElement toast;
    private final List<Button> actionButtons;
    private final List<Button> browserActionButtons;
    private boolean compactLayout;
    private boolean manageVisible;
    private UIElement fallbackClickTarget;
    private int actionPanelWidth;
    private int actionPanelHeight;
    private MapSelectionSnapshotS2CPacket snapshot;
    private MapRoomDetail detail;
    private MapRoomSummary selected;
    private String query = "";
    private String stateFilter = "all";
    private String gameModeFilter = "all";
    private PendingOpen pendingOpen = PendingOpen.NONE;

    private enum PendingOpen {
        NONE,
        MANAGE,
        TEAM
    }

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
        this.stateSelector = parts.stateSelector();
        this.modeSelector = parts.modeSelector();
        this.actions = parts.actions();
        this.browserActions = parts.browserActions();
        this.toast = parts.toast();
        this.actionButtons = parts.actionButtons();
        this.browserActionButtons = parts.browserActionButtons();
        parts.refresh().setOnClick(event -> refresh());
        parts.close().setOnClick(event -> onClose());
        parts.join().setOnClick(event -> joinOrOpenManage());
        parts.manage().setOnClick(event -> openMapManage());
        stateSelector.setOnValueChanged(value -> setStateFilter(value == null ? "all" : value));
        modeSelector.setOnValueChanged(value -> setGameModeFilter(value == null ? "all" : value));
        refreshModeSelector();
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
        refreshModeSelector();
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
        // ModularUI derives hover state during rendering, while the vanilla screen event
        // chain may not forward mouseMoved every frame. Synchronize before Screen starts
        // iterating its renderables because LDLib may update its child widget set in response.
        modularUI.getWidget().mouseMoved(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIElement target = hitElement(mouseX, mouseY);
        if (target == modularUI.getLastHoveredElement()
                && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // A process-local click can arrive before the first mouseMoved callback. Use LDLib's
        // own hit-test/event dispatcher as a narrow fallback instead of guessing widget bounds.
        if (target == null) {
            return false;
        }
        UIEvent event = UIEvent.create("mouseDown");
        event.x = (float) (mouseX - modularUI.getLeftPos());
        event.y = (float) (mouseY - modularUI.getTopPos());
        event.button = button;
        event.target = target;
        UIEventDispatcher.dispatchEvent(event);
        fallbackClickTarget = event.hasHandler ? target : null;
        return fallbackClickTarget != null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        UIElement pressedTarget = fallbackClickTarget;
        fallbackClickTarget = null;
        if (pressedTarget != null && !handled) {
            UIElement releasedTarget = hitElement(mouseX, mouseY);
            if (releasedTarget != null) {
                UIEvent mouseUp = UIEvent.create("mouseUp");
                mouseUp.x = (float) (mouseX - modularUI.getLeftPos());
                mouseUp.y = (float) (mouseY - modularUI.getTopPos());
                mouseUp.button = button;
                mouseUp.target = releasedTarget;
                UIEventDispatcher.dispatchEvent(mouseUp);
                if (releasedTarget == pressedTarget) {
                    UIEvent mouseClick = UIEvent.create("mouseClick");
                    mouseClick.x = mouseUp.x;
                    mouseClick.y = mouseUp.y;
                    mouseClick.button = button;
                    mouseClick.target = releasedTarget;
                    UIEventDispatcher.dispatchEvent(mouseClick);
                }
            }
            return true;
        }
        return handled || pressedTarget != null;
    }

    private UIElement hitElement(double mouseX, double mouseY) {
        var hit = modularUI.ui.rootElement.hitTest(
                mouseX - modularUI.getLeftPos(), mouseY - modularUI.getTopPos());
        return hit == null ? null : hit.getA();
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
                .filter(this::matchesModeFilter)
                .toList());
        roomList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(ROW_HEIGHT + ROW_GAP)
                .overscanPixels(ROW_HEIGHT * 2));
        roomList.refreshVisibleItems();
    }

    private void refreshDetail() {
        if (selected == null && detail == null) {
            detailPanel.setVisible(!compactLayout);
            detailLabel.setValue(Component.translatable("gui.fpsm.map_select.preview.none"));
            playerList.setItems(List.of());
            playerList.refreshVisibleItems();
            return;
        }
        MapRoomSummary summary = detail == null ? selected : detail.summary();
        String text = String.join("\n",
                Component.translatable("gui.fpsm.map_select.info.map", summary.displayName(), summary.mapName()).getString(),
                Component.translatable("gui.fpsm.map_select.info.mode",
                        summary.gameType().toUpperCase(java.util.Locale.ROOT)).getString(),
                Component.translatable("gui.fpsm.map_select.info.players",
                        summary.joinedPlayers(), maxPlayers(summary)).getString(),
                Component.translatable("gui.fpsm.map_select.info.status", statusText(summary).getString()).getString());
        detailLabel.setValue(Component.literal(text));
        playerList.setItems(detail == null ? List.of() : detail.players());
        playerList.refreshVisibleItems();
    }

    private void applyResponsiveLayout() {
        int horizontalMargin = Math.min(15, Math.max(2, width / 24));
        int verticalMargin = Math.min(10, Math.max(2, height / 24));
        int canvasWidth = Math.max(1, width - horizontalMargin * 2);
        int canvasHeight = Math.max(1, height - verticalMargin * 2);
        MapSelectionLayoutModel layout = MapSelectionLayoutModel.responsive(canvasWidth, canvasHeight);
        compactLayout = layout.compact();
        detailPanel.setVisible(!compactLayout);
        int originX = horizontalMargin;
        int originY = verticalMargin;
        place(filters, layout.filters(), originX, originY);
        place(roomList, layout.roomList(), originX, originY);
        if (!compactLayout) {
            place(detailPanel, layout.detail(), originX, originY);
        }
        place(actions, layout.actions(), originX, originY);
        place(browserActions, layout.browserActions(), originX, originY);

        int filterInsetX = insetFor(layout.filters().width(), PANEL_HORIZONTAL_INSET);
        int filterInsetY = insetFor(layout.filters().height(), PANEL_VERTICAL_INSET);
        int filterPanelX = originX + layout.filters().x() + filterInsetX;
        int filterPanelY = originY + layout.filters().y() + filterInsetY;
        int filterPanelWidth = placedDimension(layout.filters().width(), PANEL_HORIZONTAL_INSET);
        int filterPanelHeight = placedDimension(layout.filters().height(), PANEL_VERTICAL_INSET);
        int searchPadding = Math.min(8, Math.max(2, (filterPanelWidth - 1) / 8));
        int searchTop = Math.min(layout.compact() ? 4 : 8, Math.max(0, filterPanelHeight - 1));
        int searchHeight = Math.max(1, Math.min(18, filterPanelHeight - searchTop));
        search.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(filterPanelX + searchPadding)
                .top(filterPanelY + searchTop)
                .width(Math.max(1, filterPanelWidth - searchPadding * 2))
                .height(searchHeight));

        int selectorsTop = searchTop + searchHeight + 4;
        layoutFilterSelectors(filterPanelWidth, filterPanelHeight, selectorsTop, layout.compact());
        actionPanelWidth = placedDimension(layout.actions().width(), PANEL_HORIZONTAL_INSET);
        actionPanelHeight = placedDimension(layout.actions().height(), PANEL_VERTICAL_INSET);
        layoutActionsInPanel(actionPanelWidth, actionPanelHeight);
        layoutBrowserActionsInPanel(
                placedDimension(layout.browserActions().width(), PANEL_HORIZONTAL_INSET),
                placedDimension(layout.browserActions().height(), PANEL_VERTICAL_INSET));
        if (!compactLayout) {
            layoutDetailContents(
                    placedDimension(layout.detail().width(), PANEL_HORIZONTAL_INSET),
                    placedDimension(layout.detail().height(), PANEL_VERTICAL_INSET));
        } else {
            playerList.setVisible(false);
        }
        roomList.refreshVisibleItems();
    }

    private static void place(UIElement element, MapSelectionLayoutModel.Rect rect, int originX, int originY) {
        int horizontalInset = insetFor(rect.width(), PANEL_HORIZONTAL_INSET);
        int verticalInset = insetFor(rect.height(), PANEL_VERTICAL_INSET);
        element.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(originX + rect.x() + horizontalInset)
                .top(originY + rect.y() + verticalInset)
                .width(placedDimension(rect.width(), PANEL_HORIZONTAL_INSET))
                .height(placedDimension(rect.height(), PANEL_VERTICAL_INSET)));
    }

    private static int insetFor(int dimension, int requestedInset) {
        return Math.min(requestedInset, Math.max(0, (dimension - 1) / 2));
    }

    private static int placedDimension(int dimension, int inset) {
        return Math.max(1, dimension - insetFor(dimension, inset) * 2);
    }

    private void layoutFilterSelectors(int panelWidth, int panelHeight, int selectorsTop, boolean compact) {
        int sidePadding = compact ? 4 : Math.min(8, Math.max(2, (panelWidth - 1) / 8));
        int gap = compact ? 3 : 6;
        int selectorWidth = Math.max(1, panelWidth - sidePadding * 2);
        int availableHeight = Math.max(1, panelHeight - selectorsTop - sidePadding);
        int selectorHeight = Math.max(1, Math.min(compact ? 16 : 20,
                Math.max(1, (availableHeight - gap) / 2)));
        stateSelector.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(sidePadding).top(selectorsTop)
                .width(selectorWidth).height(selectorHeight));
        modeSelector.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(sidePadding).top(selectorsTop + selectorHeight + gap)
                .width(selectorWidth).height(selectorHeight));
    }

    private void layoutActionsInPanel(int panelWidth, int panelHeight) {
        int count = manageVisible ? actionButtons.size() : 1;
        int columns = count > 1 && panelWidth >= 120 ? 2 : 1;
        int rows = (count + columns - 1) / columns;
        int horizontalPadding = Math.min(10, Math.max(2, (panelWidth - 1) / 10));
        int verticalPadding = Math.min(8, Math.max(2, panelHeight / 12));
        int gap = Math.min(6, Math.max(1, panelWidth / 24));
        int availableWidth = Math.max(1, panelWidth - horizontalPadding * 2 - gap * (columns - 1));
        int buttonWidth = Math.max(1, Math.min(88, availableWidth / columns));
        int availableHeight = Math.max(1, panelHeight - verticalPadding * 2 - gap * (rows - 1));
        int buttonHeight = Math.max(1, Math.min(18, availableHeight / rows));
        int totalWidth = columns * buttonWidth + Math.max(0, columns - 1) * gap;
        int startLeft = Math.max(0, (panelWidth - totalWidth) / 2);
        int totalHeight = rows * buttonHeight + Math.max(0, rows - 1) * gap;
        int startTop = Math.max(0, (panelHeight - totalHeight) / 2);
        for (int i = 0; i < count; i++) {
            int index = i;
            int column = index % columns;
            int row = index / columns;
            actionButtons.get(i).layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                    .rightAuto().bottomAuto()
                    .left(startLeft + column * (buttonWidth + gap))
                    .top(startTop + row * (buttonHeight + gap))
                    .width(buttonWidth)
                    .height(buttonHeight));
        }
    }

    private void layoutBrowserActionsInPanel(int panelWidth, int panelHeight) {
        int count = browserActionButtons.size();
        int sidePadding = compactLayout ? 4 : Math.min(8, Math.max(2, (panelWidth - 1) / 8));
        int gap = 4;
        int buttonWidth = Math.max(1, panelWidth - sidePadding * 2);
        int availableHeight = Math.max(1, panelHeight - gap * Math.max(0, count - 1));
        int buttonHeight = Math.max(1, Math.min(16, availableHeight / count));
        int totalHeight = count * buttonHeight + gap * Math.max(0, count - 1);
        int startTop = Math.max(0, (panelHeight - totalHeight) / 2);
        for (int i = 0; i < count; i++) {
            int index = i;
            browserActionButtons.get(i).layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                    .rightAuto().bottomAuto()
                    .left(sidePadding)
                    .top(startTop + index * (buttonHeight + gap))
                    .width(buttonWidth).height(buttonHeight));
        }
    }

    private void layoutDetailContents(int panelWidth, int panelHeight) {
        int shortestSide = Math.min(panelWidth, panelHeight);
        int padding = Math.min(8, Math.max(2, (shortestSide - 1) / 8));
        int gap = Math.min(6, Math.max(2, panelHeight / 24));
        int availableHeight = Math.max(1, panelHeight - padding * 2);
        boolean showPlayers = availableHeight >= DETAIL_INFO_HEIGHT + gap + DETAIL_PLAYERS_MIN_HEIGHT;
        int labelHeight;
        int playerHeight;
        if (showPlayers) {
            labelHeight = Math.min(DETAIL_INFO_HEIGHT, Math.max(1, availableHeight - gap - DETAIL_PLAYERS_MIN_HEIGHT));
            playerHeight = Math.max(DETAIL_PLAYERS_MIN_HEIGHT, availableHeight - labelHeight - gap);
        } else {
            // At very small heights the four-line summary gets the whole rail. Hiding the
            // roster is preferable to letting text paint over its placeholder frame.
            labelHeight = availableHeight;
            playerHeight = 1;
        }
        int contentWidth = Math.max(1, panelWidth - padding * 2);
        detailLabel.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(padding).top(padding).width(contentWidth).height(labelHeight));
        playerList.setVisible(showPlayers);
        playerList.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(padding).top(padding + labelHeight + gap)
                .width(contentWidth).height(playerHeight));
    }

    private void select(MapRoomSummary summary) {
        selected = summary;
        detail = null;
        pendingOpen = PendingOpen.NONE;
        refreshDetail();
        refreshActionState();
        roomList.refreshVisibleItems();
        requestDetail();
    }

    private void requestDetail() {
        if (selected == null) {
            return;
        }
        pendingOpen = PendingOpen.NONE;
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.REQUEST_DETAIL,
                selected.gameType(), selected.mapName(), new UUID(0L, 0L)));
    }

    private void requestDetailFor(PendingOpen next) {
        if (selected == null) {
            return;
        }
        pendingOpen = next;
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.REQUEST_DETAIL,
                selected.gameType(), selected.mapName(), new UUID(0L, 0L)));
    }

    public boolean consumePendingManageOpen() {
        if (pendingOpen != PendingOpen.MANAGE) {
            return false;
        }
        pendingOpen = PendingOpen.NONE;
        return true;
    }

    public boolean consumePendingTeamOpen() {
        if (pendingOpen != PendingOpen.TEAM) {
            return false;
        }
        pendingOpen = PendingOpen.NONE;
        return true;
    }

    private void refresh() {
        FPSMatch.sendToServer(new OpenMapSelectionC2SPacket());
    }

    private void joinOrOpenManage() {
        if (selected == null) {
            return;
        }
        MapRoomSummary summary = summaryFor(selected);
        if (summary == null) {
            return;
        }
        boolean joined = summary.currentPlayerJoined() || summary.currentPlayerSpectating();
        if (joined) {
            // Free-for-all has no team lobby; non-FFA opens team management.
            if (!"csdm".equalsIgnoreCase(summary.gameType())) {
                openTeamManage();
            }
            return;
        }
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.JOIN,
                summary.gameType(), summary.mapName(), new UUID(0L, 0L)));
        // After joining a team-based room, open lobby once detail is ready.
        if (!"csdm".equalsIgnoreCase(summary.gameType())) {
            requestDetailFor(PendingOpen.TEAM);
        }
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

    private boolean matchesModeFilter(MapRoomSummary summary) {
        return "all".equals(gameModeFilter)
                || gameModeFilter.equals(normalizeMode(summary.gameType()));
    }

    private void setStateFilter(String filter) {
        stateFilter = Set.of("all", "waiting", "running", "open").contains(filter) ? filter : "all";
        stateSelector.setSelected(stateFilter, false);
        refreshList();
    }

    private void setGameModeFilter(String filter) {
        gameModeFilter = normalizeMode(filter);
        if (gameModeFilter.isBlank()) {
            gameModeFilter = "all";
        }
        refreshModeSelector();
        refreshList();
    }

    private void refreshFilterState() {
        stateSelector.setSelected(stateFilter, false);
        refreshModeSelector();
    }

    private void refreshModeSelector() {
        List<String> modes = snapshot.maps().stream()
                .map(MapRoomSummary::gameType)
                .map(Ldlib2MapSelectionScreen::normalizeMode)
                .filter(mode -> !mode.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> candidates = new ArrayList<>(modes.size() + 1);
        candidates.add("all");
        candidates.addAll(modes);
        if (!candidates.contains(gameModeFilter)) {
            gameModeFilter = "all";
        }
        modeSelector.setCandidates(candidates);
        modeSelector.setSelected(gameModeFilter, false);
    }

    private static String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private void refreshActionState() {
        MapRoomSummary summary = selected == null ? null : summaryFor(selected);
        boolean hasSelection = summary != null;
        boolean joined = summary != null && (summary.currentPlayerJoined() || summary.currentPlayerSpectating());
        boolean canJoin = hasSelection && !joined && !summary.full()
                && (!summary.started() || summary.allowJoinInProgress());
        // Map manage is room-admin tooling (settings/shop/minimap/debug), not team lobby.
        // Allow OP viewers even if not currently joined; do not exclude free-for-all maps.
        boolean canMapManage = hasSelection && (snapshot.viewerOp()
                || (summary != null && summary.currentPlayerOp()));
        // Team lobby entry only for non-FFA rooms after join (join button doubles as team manage).
        boolean canTeamLobby = joined && !"csdm".equalsIgnoreCase(summary.gameType());
        actionButtons.get(0).setActive(canJoin || canTeamLobby);
        manageVisible = canMapManage;
        actionButtons.get(1).setVisible(manageVisible);
        actionButtons.get(1).setActive(manageVisible);
        browserActionButtons.forEach(button -> button.setActive(true));
        if (actionPanelWidth > 0 && actionPanelHeight > 0) {
            layoutActionsInPanel(actionPanelWidth, actionPanelHeight);
        }
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
        if (selected == null) {
            return;
        }
        MapRoomSummary summary = summaryFor(selected);
        if (summary != null && "csdm".equalsIgnoreCase(summary.gameType())) {
            return;
        }
        if (detail != null && sameRoom(detail.summary(), selected) && !"csdm".equalsIgnoreCase(detail.summary().gameType())) {
            FPSMMapSelectScreens.openChild(new Ldlib2TeamManageScreen(detail, this));
            return;
        }
        requestDetailFor(PendingOpen.TEAM);
    }

    private void openMapManage() {
        if (selected == null) {
            return;
        }
        if (detail != null && sameRoom(detail.summary(), selected)) {
            FPSMMapSelectScreens.openChild(new Ldlib2MapManageScreen(detail, this));
            return;
        }
        requestDetailFor(PendingOpen.MANAGE);
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
        search.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).top(38).width(180).height(18));
        FPSMLdlib2Theme.input(search, Component.translatable("gui.fpsm.map_select.search"));
        search.textFieldStyle(style -> style.fontSize(8));

        VirtualScrollerView<MapRoomSummary> roomList = new VirtualScrollerView<>();
        roomList.setId(MapSelectionWidgetCatalog.ROOM_LIST);
        roomList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).top(68).width(320).height(220));
        FPSMLdlib2Theme.panel(roomList);
        roomList.viewContainer(container -> container.layout(layout -> layout
                .paddingHorizontal(4).paddingVertical(3)));
        roomList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(ROW_HEIGHT).overscanPixels(ROW_HEIGHT * 2));

        UIElement detailPanel = new UIElement().setId(MapSelectionWidgetCatalog.ROOM_DETAIL);
        detailPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(350).top(68).width(260).height(220));
        FPSMLdlib2Theme.panel(detailPanel);
        Label detailLabel = label("fpsmatch.map_selection.detail.text", Component.translatable("gui.fpsm.map_select.empty"));
        detailLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).right(12).top(12).height(120));
        FPSMLdlib2Theme.body(detailLabel);
        detailLabel.setOverflowVisible(false);
        detailLabel.textStyle(style -> style.fontSize(9).lineSpacing(1).textWrap(TextWrap.WRAP));
        VirtualScrollerView<MapRoomPlayerInfo> playerList = new VirtualScrollerView<>();
        playerList.setId(MapSelectionWidgetCatalog.PLAYERS);
        playerList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).right(12).top(138).bottom(12));
        FPSMLdlib2Theme.elevated(playerList);
        playerList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(24).overscanPixels(48));
        playerList.setItemUIProvider(info -> {
            Label row = new Label();
            row.setId(MapSelectionWidgetCatalog.PLAYERS + "." + info.uuid());
            row.setValue(Component.literal(info.name() + "  " + info.teamName()));
            row.layout(layout -> layout.height(24).widthPercent(100).paddingLeft(8).paddingRight(8));
            FPSMLdlib2Theme.muted(row);
            row.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HIDE));
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

        Selector<String> stateSelector = new Selector<>();
        stateSelector.setId(MapSelectionWidgetCatalog.STATE_FILTER);
        stateSelector.setCandidateUIProvider(value -> selectorLabel(stateFilterText(value)));
        stateSelector.setCandidates(List.of("all", "waiting", "running", "open"));
        stateSelector.setSelected("all", false);
        FPSMLdlib2Theme.selector(stateSelector);

        Selector<String> modeSelector = new Selector<>();
        modeSelector.setId(MapSelectionWidgetCatalog.MODE_FILTER);
        modeSelector.setCandidateUIProvider(value -> selectorLabel(modeFilterText(value)));
        modeSelector.setCandidates(List.of("all"));
        modeSelector.setSelected("all", false);
        FPSMLdlib2Theme.selector(modeSelector);
        filters.addChildren(stateSelector, modeSelector);

        Button join = button(MapSelectionWidgetCatalog.ACTIONS + ".join", "gui.fpsm.map_select.join", 8, event -> {});
        Button manage = button(MapSelectionWidgetCatalog.ACTIONS + ".manage", "gui.fpsm.map_select.manage", 8, event -> {});
        for (Button action : List.of(join, manage)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).top(8).width(72).height(18));
        }
        FPSMLdlib2Theme.button(join, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        FPSMLdlib2Theme.button(manage, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        join.textStyle(style -> style.fontSize(7));
        manage.textStyle(style -> style.fontSize(7));

        Button refresh = button(MapSelectionWidgetCatalog.BROWSER_ACTIONS + ".refresh", "gui.fpsm.map_select.refresh", 8, event -> {});
        Button close = button(MapSelectionWidgetCatalog.BROWSER_ACTIONS + ".close", "gui.done", 8, event -> {});
        for (Button action : List.of(refresh, close)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(8).top(8).width(72).height(16));
        }
        FPSMLdlib2Theme.button(refresh, FPSMLdlib2Theme.ButtonKind.QUIET);
        FPSMLdlib2Theme.button(close, FPSMLdlib2Theme.ButtonKind.QUIET);
        refresh.textStyle(style -> style.fontSize(7));
        close.textStyle(style -> style.fontSize(7));
        UIElement actions = new UIElement().setId(MapSelectionWidgetCatalog.ACTIONS);
        FPSMLdlib2Theme.panel(actions);
        actions.addChildren(join, manage);
        UIElement browserActions = new UIElement().setId(MapSelectionWidgetCatalog.BROWSER_ACTIONS);
        browserActions.addChildren(refresh, close);
        root.addChildren(header, filters, search, roomList, detailPanel, toast, actions, browserActions);

        List<Button> actionButtons = List.of(join, manage);
        List<Button> browserActionButtons = List.of(refresh, close);
        Parts parts = new Parts(ModularUI.of(UI.of(root, screenSize -> Size.of(screenSize.getWidth(), screenSize.getHeight()))), roomList, detailPanel, detailLabel, playerList,
                search, filters, stateSelector, modeSelector, actions, browserActions, toast,
                actionButtons, browserActionButtons, refresh, close, join, manage);
        roomList.setItemUIProvider((UIElementProvider<MapRoomSummary>) summary -> {
            Button row = new Button().noText();
            row.setId(MapSelectionWidgetCatalog.ROOM_LIST + "." + summary.gameType() + "." + summary.mapName());
            row.setOnClick(event -> parts.screenSelect(summary));
            row.layout(layout -> layout.height(ROW_HEIGHT).widthPercent(100).marginBottom(ROW_GAP));
            FPSMLdlib2Theme.roomRow(row, roomStatusColor(summary), parts.isSelected(summary));
            boolean compactRow = parts.usesCompactRoomRows();
            UIElement status = new UIElement();
            status.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).top(0).bottom(0).width(3));
            status.style(style -> style.background(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(roomStatusColor(summary))));
            Ldlib2MapThumbnailElement thumbnail = new Ldlib2MapThumbnailElement(
                    row.getElementName() + ".preview", "", summary.mapName(), summary.gameType(), summary.displayName());
            thumbnail.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(compactRow ? 6 : 8).top(compactRow ? 8 : 6)
                    .width(compactRow ? 26 : 30).height(compactRow ? 26 : 30));
            Label name = label(row.getElementName() + ".name", Component.literal(summary.displayName()));
            name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(compactRow ? 38 : 44).right(compactRow ? 46 : 60).top(5).height(13));
            FPSMLdlib2Theme.sectionTitle(name);
            Label meta = label(row.getElementName() + ".meta",
                    Component.literal(summary.gameType().toUpperCase(java.util.Locale.ROOT) + "  " + summary.mapName()));
            meta.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(compactRow ? 38 : 44).right(compactRow ? 46 : 60).top(22).height(11));
            FPSMLdlib2Theme.muted(meta);
            Label players = label(row.getElementName() + ".players",
                    Component.literal(summary.joinedPlayers() + "/" + maxPlayers(summary)));
            players.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .right(compactRow ? 6 : 8).top(6).width(compactRow ? 38 : 46).height(11));
            FPSMLdlib2Theme.status(players, summary.full() ? FPSMLdlib2Theme.DANGER : FPSMLdlib2Theme.TEXT);
            Label state = label(row.getElementName() + ".status", statusText(summary));
            state.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .right(compactRow ? 6 : 8).top(22).width(compactRow ? 42 : 54).height(11));
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
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.STATE_FILTER)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.MODE_FILTER)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.BROWSER_ACTIONS)
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

    private static Label selectorLabel(Component text) {
        Label label = new Label();
        label.setValue(text);
        FPSMLdlib2Theme.body(label);
        label.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HIDE));
        return label;
    }

    private static Component stateFilterText(String filter) {
        String valueKey = switch (filter == null ? "all" : filter) {
            case "waiting" -> "gui.fpsm.map_select.filter.waiting";
            case "running" -> "gui.fpsm.map_select.filter.running";
            case "open" -> "gui.fpsm.map_select.filter.open";
            default -> "gui.fpsm.map_select.filter.all";
        };
        return Component.translatable("gui.fpsm.map_select.filter.status_selector",
                Component.translatable(valueKey));
    }

    private static Component modeFilterText(String mode) {
        Component value = "all".equals(normalizeMode(mode))
                ? Component.translatable("gui.fpsm.map_select.filter.mode.all")
                : Component.literal(normalizeMode(mode).toUpperCase(Locale.ROOT));
        return Component.translatable("gui.fpsm.map_select.filter.mode_selector", value);
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
                         UIElement filters, Selector<String> stateSelector, Selector<String> modeSelector,
                         UIElement actions, UIElement browserActions, UIElement toast,
                         List<Button> actionButtons, List<Button> browserActionButtons,
                         Button refresh, Button close, Button join, Button manage) {
        private void screenSelect(MapRoomSummary summary) {
            if (ui.getScreen() instanceof Ldlib2MapSelectionScreen screen) screen.select(summary);
        }

        private boolean isSelected(MapRoomSummary summary) {
            return ui.getScreen() instanceof Ldlib2MapSelectionScreen screen && sameRoom(screen.selected, summary);
        }

        private boolean usesCompactRoomRows() {
            return ui.getScreen() instanceof Ldlib2MapSelectionScreen screen && screen.compactLayout;
        }
    }
}
