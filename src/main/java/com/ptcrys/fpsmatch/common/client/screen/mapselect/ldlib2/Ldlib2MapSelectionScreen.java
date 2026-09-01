package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleModularUIScreen;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleSelector;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleTextField;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2RenderGuard;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapDetailChildScreen;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.packet.mapselect.CloseMapViewC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** LDLib2 map-room browser and detail view. */
public final class Ldlib2MapSelectionScreen extends AccessibleModularUIScreen
        implements FPSMMapDetailChildScreen {
    private static final int PANEL_HORIZONTAL_INSET = 3;
    private static final int PANEL_VERTICAL_INSET = 6;
    private static final int DETAIL_INFO_HEIGHT = 56;
    private static final int DETAIL_PLAYERS_MIN_HEIGHT = 24;

    private final Screen parent;
    private final VirtualScrollerView<MapRoomSummary> roomList;
    private final Label roomListHeading;
    private final Label emptyState;
    private final UIElement detailPanel;
    private final Label detailLabel;
    private final VirtualScrollerView<MapRoomPlayerInfo> playerList;
    private final AccessibleTextField search;
    private final UIElement filters;
    private final AccessibleSelector<String> stateSelector;
    private final AccessibleSelector<String> modeSelector;
    private final UIElement actions;
    private final UIElement browserActions;
    private final UIElement toast;
    private final Label toastLabel;
    private final List<AccessibleButton> actionButtons;
    private final List<AccessibleButton> browserActionButtons;
    private final Set<MapRoomToastS2CPacket> announcedToasts =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean compactLayout;
    private boolean manageVisible;
    private int actionPanelWidth;
    private int actionPanelHeight;
    private MapSelectionSnapshotS2CPacket snapshot;
    private MapRoomDetail detail;
    private MapRoomSummary selected;
    private String query = "";
    private String stateFilter = "all";
    private String gameModeFilter = "all";
    private PendingOpen pendingOpen = PendingOpen.NONE;
    private MapRoomToastS2CPacket dismissedToast;

    private enum PendingOpen {
        NONE,
        DETAIL,
        MANAGE,
        TEAM
    }

    public Ldlib2MapSelectionScreen(MapSelectionSnapshotS2CPacket snapshot, Screen parent) {
        this(MapSelectionUiView.build(), snapshot, parent);
    }

    private Ldlib2MapSelectionScreen(
            MapSelectionUiView.Parts parts,
            MapSelectionSnapshotS2CPacket snapshot,
            Screen parent
    ) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.title"));
        this.parent = parent;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.roomList = parts.roomList();
        this.roomListHeading = parts.roomListHeading();
        this.emptyState = parts.emptyState();
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
        this.toastLabel = parts.toastLabel();
        this.actionButtons = parts.actionButtons();
        this.browserActionButtons = parts.browserActionButtons();
        parts.refresh().setOnClick(event -> refresh());
        parts.close().setOnClick(event -> onClose());
        parts.join().setOnClick(event -> openSelectedDetail());
        parts.manage().setOnClick(event -> openMapManage());
        stateSelector.setOnValueChanged(value -> setStateFilter(value == null ? "all" : value));
        modeSelector.setOnValueChanged(value -> setGameModeFilter(value == null ? "all" : value));
        registerFocusGroup(this::focusTargets);
        refreshFilterState();
        refreshList();
    }

    @Override
    public void init() {
        super.init();
        // Element IDs are registered only after ModularUI.setScreenAndInit (super.init).
        bindRequiredWidgets();
        applyResponsiveLayout();
        refreshToast();
    }

    @Override
    public void tick() {
        super.tick();
        modularUI.tick();
        refreshToast();
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        if (detail == null) {
            return;
        }
        dismissToast();
        List<MapRoomSummary> rooms = filteredRooms();
        retainSelection(rooms);
        if (selected == null || !sameRoom(selected, detail.summary())) {
            // The router consumes a pending child-open immediately after this callback. A late
            // response must not open a child for a room that is no longer selected or visible.
            pendingOpen = PendingOpen.NONE;
            updateEmptyState(rooms);
            refreshDetail();
            refreshActionState();
            roomList.refreshVisibleItems();
            return;
        }
        this.detail = detail;
        refreshDetail();
        refreshActionState();
    }

    public void applySnapshot(MapSelectionSnapshotS2CPacket snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        dismissToast();
        refreshFilterState();
        refreshList();
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
        FPSMLdlib2Backdrop.draw(graphics, this.width, this.height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } catch (ConcurrentModificationException failure) {
            if (!Ldlib2RenderGuard.ignoreConcurrentModification(this, failure)) {
                throw failure;
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        modularUI.getWidget().mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && stateSelector.isOpen()) {
            stateSelector.hide();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && modeSelector.isOpen()) {
            modeSelector.hide();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (search.isFocused() || stateSelector.isOpen() || modeSelector.isOpen()) {
            return false;
        }
        if ((keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)
                && focusOutsideRoomRows()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            return moveRoomSelection(-1);
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            return moveRoomSelection(1);
        }
        return false;
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        targets.add(this.search);
        targets.add(stateSelector);
        targets.add(modeSelector);
        targets.addAll(visibleRoomFocusTargets());
        if (actionButtons.get(0).isActive()) {
            targets.add(actionButtons.get(0));
        }
        if (actionButtons.get(1).isVisible() && actionButtons.get(1).isActive()) {
            targets.add(actionButtons.get(1));
        }
        targets.add(browserActionButtons.get(0));
        targets.add(browserActionButtons.get(1));
        return List.copyOf(targets);
    }

    private List<Ldlib2AccessibilityController.FocusTarget> visibleRoomFocusTargets() {
        List<AccessibleButton> mountedRows = roomList.allChildrenStream()
                .filter(AccessibleButton.class::isInstance)
                .map(AccessibleButton.class::cast)
                .toList();
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        for (MapRoomSummary summary : filteredRooms()) {
            String rowId = MapSelectionUiView.roomId(summary);
            mountedRows.stream()
                    .filter(row -> Objects.equals(row.getId(), rowId))
                    .findFirst()
                    .ifPresent(targets::add);
        }
        return targets;
    }

    private void refreshList() {
        List<MapRoomSummary> rooms = filteredRooms();
        retainSelection(rooms);
        roomList.setItems(rooms);
        roomList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(MapSelectionUiView.ROW_HEIGHT + MapSelectionUiView.ROW_GAP)
                .overscanPixels(MapSelectionUiView.ROW_HEIGHT * 2));
        updateEmptyState(rooms);
        refreshDetail();
        refreshActionState();
        roomList.refreshVisibleItems();
    }

    private List<MapRoomSummary> filteredRooms() {
        return snapshot.maps().stream()
                .filter(this::matchesQuery)
                .filter(this::matchesStateFilter)
                .filter(this::matchesModeFilter)
                .toList();
    }

    private void retainSelection(List<MapRoomSummary> rooms) {
        if (selected == null) {
            return;
        }
        selected = rooms.stream().filter(summary -> sameRoom(summary, selected)).findFirst().orElse(null);
        if (selected == null) {
            detail = null;
            pendingOpen = PendingOpen.NONE;
        }
    }

    private void updateEmptyState(List<MapRoomSummary> rooms) {
        emptyState.setVisible(rooms.isEmpty());
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
        place(toast, layout.toast(), originX, originY);
        place(filters, layout.filters(), originX, originY);
        placeRoomList(layout.roomList(), originX, originY);
        placeRoomListHeading(layout.roomList(), originX, originY);
        placeEmptyState(layout.roomList(), originX, originY);
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
        int searchTop = Math.min(layout.compact() ? 22 : 24, Math.max(0, filterPanelHeight - 1));
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

    private void placeRoomList(MapSelectionLayoutModel.Rect rect, int originX, int originY) {
        int horizontalInset = insetFor(rect.width(), PANEL_HORIZONTAL_INSET);
        int verticalInset = insetFor(rect.height(), PANEL_VERTICAL_INSET);
        int headerHeight = roomListHeaderHeight(rect.height());
        int width = placedDimension(rect.width(), PANEL_HORIZONTAL_INSET);
        int height = Math.max(1, placedDimension(rect.height(), PANEL_VERTICAL_INSET) - headerHeight);
        roomList.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(originX + rect.x() + horizontalInset)
                .top(originY + rect.y() + verticalInset + headerHeight)
                .width(width).height(height));
    }

    private void placeRoomListHeading(MapSelectionLayoutModel.Rect rect, int originX, int originY) {
        int horizontalInset = insetFor(rect.width(), PANEL_HORIZONTAL_INSET);
        int verticalInset = insetFor(rect.height(), PANEL_VERTICAL_INSET);
        roomListHeading.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(originX + rect.x() + horizontalInset + 7)
                .top(originY + rect.y() + verticalInset + 3)
                .width(Math.max(1, placedDimension(rect.width(), PANEL_HORIZONTAL_INSET) - 14))
                .height(Math.min(14, roomListHeaderHeight(rect.height()) - 2)));
    }

    private static int roomListHeaderHeight(int rectHeight) {
        return Math.min(20, Math.max(14, rectHeight / 10));
    }

    private void placeEmptyState(MapSelectionLayoutModel.Rect rect, int originX, int originY) {
        int listWidth = placedDimension(rect.width(), PANEL_HORIZONTAL_INSET);
        int listHeight = Math.max(1, placedDimension(rect.height(), PANEL_VERTICAL_INSET)
                - roomListHeaderHeight(rect.height()));
        int labelHeight = Math.min(20, Math.max(1, listHeight));
        emptyState.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(originX + rect.x() + insetFor(rect.width(), PANEL_HORIZONTAL_INSET) + 4)
                .top(originY + rect.y() + insetFor(rect.height(), PANEL_VERTICAL_INSET)
                        + roomListHeaderHeight(rect.height())
                        + Math.max(0, (listHeight - labelHeight) / 2))
                .width(Math.max(1, listWidth - 8))
                .height(labelHeight));
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
        int headingHeight = Math.min(16, Math.max(1, panelHeight / 10));
        int contentTop = padding + headingHeight + 4;
        int availableHeight = Math.max(1, panelHeight - contentTop - padding);
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
                .left(padding).top(contentTop).width(contentWidth).height(labelHeight));
        playerList.setVisible(showPlayers);
        playerList.layout(style -> style.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto()
                .left(padding).top(contentTop + labelHeight + gap)
                .width(contentWidth).height(playerHeight));
    }

    void select(MapRoomSummary summary) {
        dismissToast();
        selected = summary;
        detail = null;
        pendingOpen = PendingOpen.NONE;
        refreshDetail();
        refreshActionState();
        roomList.refreshVisibleItems();
        requestDetail();
    }

    private boolean moveRoomSelection(int direction) {
        List<MapRoomSummary> rooms = filteredRooms();
        if (rooms.isEmpty()) {
            return false;
        }
        int currentIndex = -1;
        for (int index = 0; index < rooms.size(); index++) {
            if (sameRoom(rooms.get(index), selected)) {
                currentIndex = index;
                break;
            }
        }
        int nextIndex = currentIndex < 0
                ? (direction < 0 ? rooms.size() - 1 : 0)
                : Math.max(0, Math.min(rooms.size() - 1, currentIndex + direction));
        MapRoomSummary next = rooms.get(nextIndex);
        if (sameRoom(selected, next)) {
            return true;
        }
        select(next);
        focusVisibleRoom(next);
        return true;
    }

    void setQuery(String value) {
        query = value == null ? "" : value.trim();
        dismissToast();
        refreshList();
    }

    MapRoomSummary selectedRoom() {
        return selected;
    }

    boolean usesCompactRoomRows() {
        return compactLayout;
    }

    private boolean focusOutsideRoomRows() {
        return accessibility().focusedTarget()
                .map(Ldlib2AccessibilityController.FocusTarget::element)
                .map(UIElement::getId)
                .filter(Objects::nonNull)
                .filter(id -> !id.startsWith(MapSelectionWidgetCatalog.ROOM_LIST + "."))
                .isPresent();
    }

    private void focusVisibleRoom(MapRoomSummary summary) {
        List<MapRoomSummary> rooms = filteredRooms();
        int index = -1;
        for (int candidate = 0; candidate < rooms.size(); candidate++) {
            if (sameRoom(rooms.get(candidate), summary)) {
                index = candidate;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        roomList.verticalScroller.setNormalizedValue(
                (float) normalizedRoomPosition(index, rooms.size())
        );
        roomList.refreshVisibleItems();
        String rowId = MapSelectionUiView.roomId(summary);
        roomList.allChildrenStream()
                .filter(AccessibleButton.class::isInstance)
                .map(AccessibleButton.class::cast)
                .filter(row -> Objects.equals(row.getId(), rowId))
                .findFirst()
                .ifPresent(row -> {
                    modularUI.requestFocus(row);
                    accessibility().reconcileFocus();
                });
    }

    private static double normalizedRoomPosition(int index, int size) {
        return size <= 1 ? 0.0 : index / (double) (size - 1);
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

    public boolean consumePendingDetailOpen() {
        if (pendingOpen != PendingOpen.DETAIL) {
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
        dismissToast();
        FPSMatch.sendToServer(new OpenMapSelectionC2SPacket());
    }

    private void openSelectedDetail() {
        if (selected == null) {
            return;
        }
        requestDetailFor(PendingOpen.DETAIL);
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

    static String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private void refreshActionState() {
        MapRoomSummary summary = selected == null ? null : summaryFor(selected);
        boolean hasSelection = summary != null;
        // 浏览页只负责选择与进入详情；加入、队伍和管理动作均在详情/子页面完成。
        boolean canOpenDetail = hasSelection;
        FPSMLdlib2Theme.buttonState(
                actionButtons.get(0), FPSMLdlib2Theme.ButtonKind.PRIMARY,
                canOpenDetail
        );
        manageVisible = false;
        actionButtons.get(1).setDisplay(manageVisible);
        actionButtons.get(1).setVisible(manageVisible);
        FPSMLdlib2Theme.buttonState(
                actionButtons.get(1), FPSMLdlib2Theme.ButtonKind.SECONDARY,
                manageVisible
        );
        browserActionButtons.forEach(button -> FPSMLdlib2Theme.buttonState(
                button, FPSMLdlib2Theme.ButtonKind.QUIET, true
        ));
        if (actionPanelWidth > 0 && actionPanelHeight > 0) {
            layoutActionsInPanel(actionPanelWidth, actionPanelHeight);
        }
    }

    public void applyToast() {
        dismissedToast = null;
        refreshToast();
    }

    private void refreshToast() {
        FPSMClient.getGlobalData().getMapRoomToast().ifPresentOrElse(packet -> {
            if (packet == dismissedToast) {
                toast.setVisible(false);
                return;
            }
            toast.setVisible(true);
            toastLabel.setValue(packet.message());
            FPSMLdlib2Theme.status(
                    toastLabel,
                    packet.error() ? FPSMLdlib2Theme.DANGER : FPSMLdlib2Theme.SUCCESS
            );
            if (announcedToasts.add(packet)) {
                accessibility().announce(packet.message(), packet.error());
            }
        }, () -> toast.setVisible(false));
    }

    private void dismissToast() {
        FPSMClient.getGlobalData().getMapRoomToast().ifPresent(packet ->
                dismissedToast = packet
        );
        toast.setVisible(false);
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

    static boolean sameRoom(MapRoomSummary first, MapRoomSummary second) {
        return first != null && second != null && first.gameType().equals(second.gameType()) && first.mapName().equals(second.mapName());
    }

    static String maxPlayers(MapRoomSummary summary) {
        return summary.maxPlayers() < 0 ? "?" : Integer.toString(summary.maxPlayers());
    }

    static int roomStatusColor(MapRoomSummary summary) {
        if (summary.full()) return FPSMLdlib2Theme.DANGER;
        if (summary.debug()) return FPSMLdlib2Theme.ACCENT;
        if (summary.started()) return summary.allowJoinInProgress() ? FPSMLdlib2Theme.WARNING : FPSMLdlib2Theme.DANGER;
        return FPSMLdlib2Theme.SUCCESS;
    }

    static Component statusText(MapRoomSummary summary) {
        if (summary.full()) return Component.translatable("gui.fpsm.map_select.full");
        if (summary.debug()) return Component.translatable("gui.fpsm.map_select.status.debug");
        if (summary.started()) return Component.translatable(summary.allowJoinInProgress()
                ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private void bindRequiredWidgets() {
        if (!modularUI.hasElementWithId(MapSelectionWidgetCatalog.ROOT)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.ROOM_LIST)
                || !modularUI.hasElementWithId(MapSelectionWidgetCatalog.EMPTY_STATE)
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
}
