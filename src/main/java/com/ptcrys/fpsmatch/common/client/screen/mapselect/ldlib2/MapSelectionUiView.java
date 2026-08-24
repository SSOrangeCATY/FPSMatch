package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleSelector;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleTextField;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;
import java.util.Locale;

/** Builds the map-room element tree and bridges element callbacks back to its screen owner. */
final class MapSelectionUiView {
    static final int ROW_HEIGHT = 42;
    static final int ROW_GAP = 3;

    private MapSelectionUiView() {
    }

    static Parts build() {
        UIElement root = new UIElement().setId(MapSelectionWidgetCatalog.ROOT);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);

        Label header = label(
                MapSelectionWidgetCatalog.HEADER,
                Component.translatable("gui.fpsm.map_select.title")
        );
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(18).right(18).top(10).height(22));
        FPSMLdlib2Theme.title(header);

        AccessibleTextField search = new AccessibleTextField();
        search.setId(MapSelectionWidgetCatalog.SEARCH);
        search.setAccessibleName(Component.translatable("gui.fpsm.map_select.search"));
        search.setAnyString();
        search.setText("");
        search.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(18).top(38).width(180).height(18));
        FPSMLdlib2Theme.input(search, Component.translatable("gui.fpsm.map_select.search"));
        search.textFieldStyle(style -> style.fontSize(8));

        VirtualScrollerView<MapRoomSummary> roomList = new VirtualScrollerView<>();
        roomList.setId(MapSelectionWidgetCatalog.ROOM_LIST);
        roomList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(18).top(68).width(320).height(220));
        FPSMLdlib2Theme.panel(roomList);
        roomList.viewContainer(container -> container.layout(layout -> layout
                .paddingHorizontal(4).paddingVertical(3)));
        roomList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(ROW_HEIGHT).overscanPixels(ROW_HEIGHT * 2));

        Label emptyState = label(
                MapSelectionWidgetCatalog.EMPTY_STATE,
                Component.translatable("gui.fpsm.map_select.empty")
        );
        emptyState.setAllowHitTest(false);
        emptyState.setFocusable(false);
        emptyState.setVisible(false);
        FPSMLdlib2Theme.muted(emptyState);
        emptyState.textStyle(style -> style.fontSize(8).textWrap(TextWrap.WRAP));

        UIElement detailPanel = new UIElement().setId(MapSelectionWidgetCatalog.ROOM_DETAIL);
        detailPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(350).top(68).width(260).height(220));
        FPSMLdlib2Theme.panel(detailPanel);
        Label detailLabel = label(
                "fpsmatch.map_selection.detail.text",
                Component.translatable("gui.fpsm.map_select.empty")
        );
        detailLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(12).height(120));
        FPSMLdlib2Theme.body(detailLabel);
        detailLabel.setOverflowVisible(false);
        detailLabel.textStyle(style -> style.fontSize(9).lineSpacing(1).textWrap(TextWrap.WRAP));

        VirtualScrollerView<MapRoomPlayerInfo> playerList = new VirtualScrollerView<>();
        playerList.setId(MapSelectionWidgetCatalog.PLAYERS);
        playerList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(138).bottom(12));
        FPSMLdlib2Theme.elevated(playerList);
        playerList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(24).overscanPixels(48));
        playerList.setItemUIProvider(info -> {
            Label row = new Label();
            row.setId(MapSelectionWidgetCatalog.PLAYERS + "." + info.uuid());
            row.setValue(Component.literal(info.name() + "  " + info.teamName()));
            row.layout(layout -> layout.height(24).widthPercent(100)
                    .paddingLeft(8).paddingRight(8));
            FPSMLdlib2Theme.muted(row);
            row.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HIDE));
            row.style(style -> style.background(FPSMLdlib2Theme.panelTexture(
                    0xFF182029, FPSMLdlib2Theme.BORDER_SOFT)));
            return row;
        });
        detailPanel.addChildren(playerList, detailLabel);

        UIElement filters = new UIElement().setId(MapSelectionWidgetCatalog.FILTERS);
        filters.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).top(52).width(180).height(220));
        FPSMLdlib2Theme.panel(filters);

        AccessibleSelector<String> stateSelector = new AccessibleSelector<>();
        stateSelector.setId(MapSelectionWidgetCatalog.STATE_FILTER);
        stateSelector.setAccessibleName(() -> stateFilterText(stateSelector.getValue()));
        stateSelector.setCandidateUIProvider(value -> selectorLabel(stateFilterText(value)));
        stateSelector.setCandidates(List.of("all", "waiting", "running", "open"));
        stateSelector.setSelected("all", false);
        FPSMLdlib2Theme.selector(stateSelector);

        AccessibleSelector<String> modeSelector = new AccessibleSelector<>();
        modeSelector.setId(MapSelectionWidgetCatalog.MODE_FILTER);
        modeSelector.setAccessibleName(() -> modeFilterText(modeSelector.getValue()));
        modeSelector.setCandidateUIProvider(value -> selectorLabel(modeFilterText(value)));
        modeSelector.setCandidates(List.of("all"));
        modeSelector.setSelected("all", false);
        FPSMLdlib2Theme.selector(modeSelector);
        filters.addChildren(stateSelector, modeSelector);

        UIElement toast = new UIElement().setId(MapSelectionWidgetCatalog.TOAST);
        toast.setVisible(false);
        toast.setAllowHitTest(false);
        toast.setFocusable(false);
        FPSMLdlib2Theme.panel(toast);
        Label label = label(MapSelectionWidgetCatalog.TOAST + ".text", Component.empty());
        label.setAllowHitTest(false);
        label.setFocusable(false);
        label.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).right(8).top(2).bottom(2));
        toast.addChild(label);

        AccessibleButton join = button(
                MapSelectionWidgetCatalog.ACTIONS + ".join",
                "gui.fpsm.map_select.join",
                8
        );
        AccessibleButton manage = button(
                MapSelectionWidgetCatalog.ACTIONS + ".manage",
                "gui.fpsm.map_select.manage",
                8
        );
        for (AccessibleButton action : List.of(join, manage)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).top(8).width(72).height(18));
        }
        FPSMLdlib2Theme.button(join, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        FPSMLdlib2Theme.button(manage, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        join.textStyle(style -> style.fontSize(8));
        manage.textStyle(style -> style.fontSize(8));

        AccessibleButton refresh = button(
                MapSelectionWidgetCatalog.BROWSER_ACTIONS + ".refresh",
                "gui.fpsm.map_select.refresh",
                8
        );
        AccessibleButton close = button(
                MapSelectionWidgetCatalog.BROWSER_ACTIONS + ".close",
                "gui.done",
                8
        );
        for (AccessibleButton action : List.of(refresh, close)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).top(8).width(72).height(16));
        }
        FPSMLdlib2Theme.button(refresh, FPSMLdlib2Theme.ButtonKind.QUIET);
        FPSMLdlib2Theme.button(close, FPSMLdlib2Theme.ButtonKind.QUIET);
        refresh.textStyle(style -> style.fontSize(8));
        close.textStyle(style -> style.fontSize(8));

        UIElement actions = new UIElement().setId(MapSelectionWidgetCatalog.ACTIONS);
        FPSMLdlib2Theme.panel(actions);
        actions.addChildren(join, manage);
        UIElement browserActions = new UIElement().setId(MapSelectionWidgetCatalog.BROWSER_ACTIONS);
        browserActions.addChildren(refresh, close);
        root.addChildren(
                header, filters, search, roomList, emptyState, detailPanel, toast,
                actions, browserActions
        );

        Parts parts = new Parts(
                ModularUI.of(UI.of(root, screenSize -> Size.of(
                        screenSize.getWidth(), screenSize.getHeight()))),
                roomList, emptyState, detailPanel, detailLabel, playerList, search,
                filters, stateSelector, modeSelector, actions, browserActions, toast, label,
                List.of(join, manage), List.of(refresh, close), refresh, close, join, manage
        );
        roomList.setItemUIProvider((UIElementProvider<MapRoomSummary>) summary -> {
            AccessibleButton row = (AccessibleButton) new AccessibleButton().noText();
            row.setId(roomId(summary));
            row.setAccessibleName(() -> Component.translatable(
                    "gui.fpsm.map_select.info.map", summary.displayName(), summary.mapName()));
            row.setAccessibleState(() -> Ldlib2MapSelectionScreen.statusText(summary));
            row.setOnClick(event -> parts.screenSelect(summary));
            row.layout(layout -> layout.height(ROW_HEIGHT).widthPercent(100)
                    .marginBottom(ROW_GAP));
            FPSMLdlib2Theme.roomRow(
                    row,
                    Ldlib2MapSelectionScreen.roomStatusColor(summary),
                    parts.isSelected(summary)
            );
            boolean compactRow = parts.usesCompactRoomRows();
            addRoomRowContents(row, summary, compactRow);
            return row;
        });
        search.setTextResponder(parts::screenSearch);
        return parts;
    }

    static String roomId(MapRoomSummary summary) {
        return MapSelectionWidgetCatalog.ROOM_LIST + "."
                + summary.gameType() + "." + summary.mapName();
    }

    private static void addRoomRowContents(
            AccessibleButton row,
            MapRoomSummary summary,
            boolean compact
    ) {
        int statusColor = Ldlib2MapSelectionScreen.roomStatusColor(summary);
        UIElement status = new UIElement();
        status.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).top(0).bottom(0).width(3));
        status.style(style -> style.background(
                new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(statusColor)));
        Ldlib2MapThumbnailElement thumbnail = new Ldlib2MapThumbnailElement(
                row.getElementName() + ".preview",
                "",
                summary.mapName(),
                summary.gameType(),
                summary.displayName()
        );
        thumbnail.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(compact ? 6 : 8).top(compact ? 8 : 6)
                .width(compact ? 26 : 30).height(compact ? 26 : 30));
        Label name = label(row.getElementName() + ".name", Component.literal(summary.displayName()));
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(compact ? 38 : 44).right(compact ? 46 : 60).top(5).height(13));
        FPSMLdlib2Theme.sectionTitle(name);
        Label meta = label(
                row.getElementName() + ".meta",
                Component.literal(summary.gameType().toUpperCase(Locale.ROOT)
                        + "  " + summary.mapName())
        );
        meta.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(compact ? 38 : 44).right(compact ? 46 : 60).top(22).height(11));
        FPSMLdlib2Theme.muted(meta);
        Label players = label(
                row.getElementName() + ".players",
                Component.literal(summary.joinedPlayers() + "/"
                        + Ldlib2MapSelectionScreen.maxPlayers(summary))
        );
        players.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(compact ? 6 : 8).top(6).width(compact ? 38 : 46).height(11));
        FPSMLdlib2Theme.status(
                players,
                summary.full() ? FPSMLdlib2Theme.DANGER : FPSMLdlib2Theme.TEXT
        );
        Label state = label(
                row.getElementName() + ".status",
                Ldlib2MapSelectionScreen.statusText(summary)
        );
        state.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(compact ? 6 : 8).top(22).width(compact ? 42 : 54).height(11));
        FPSMLdlib2Theme.status(state, statusColor);
        row.addChildren(status, thumbnail, name, meta, players, state);
    }

    private static AccessibleButton button(String id, String key, int left) {
        AccessibleButton button = new AccessibleButton();
        button.setId(id);
        button.setText(Component.translatable(key));
        button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(left));
        return button;
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
        return Component.translatable(
                "gui.fpsm.map_select.filter.status_selector",
                Component.translatable(valueKey)
        );
    }

    private static Component modeFilterText(String mode) {
        String normalized = Ldlib2MapSelectionScreen.normalizeMode(mode);
        Component value = "all".equals(normalized)
                ? Component.translatable("gui.fpsm.map_select.filter.mode.all")
                : Component.literal(normalized.toUpperCase(Locale.ROOT));
        return Component.translatable("gui.fpsm.map_select.filter.mode_selector", value);
    }

    record Parts(
            ModularUI ui,
            VirtualScrollerView<MapRoomSummary> roomList,
            Label emptyState,
            UIElement detailPanel,
            Label detailLabel,
            VirtualScrollerView<MapRoomPlayerInfo> playerList,
            AccessibleTextField search,
            UIElement filters,
            AccessibleSelector<String> stateSelector,
            AccessibleSelector<String> modeSelector,
            UIElement actions,
            UIElement browserActions,
            UIElement toast,
            Label toastLabel,
            List<AccessibleButton> actionButtons,
            List<AccessibleButton> browserActionButtons,
            AccessibleButton refresh,
            AccessibleButton close,
            AccessibleButton join,
            AccessibleButton manage
    ) {
        void screenSelect(MapRoomSummary summary) {
            if (ui.getScreen() instanceof Ldlib2MapSelectionScreen screen) {
                screen.select(summary);
            }
        }

        void screenSearch(String value) {
            if (ui.getScreen() instanceof Ldlib2MapSelectionScreen screen) {
                screen.setQuery(value);
            }
        }

        boolean isSelected(MapRoomSummary summary) {
            return ui.getScreen() instanceof Ldlib2MapSelectionScreen screen
                    && Ldlib2MapSelectionScreen.sameRoom(screen.selectedRoom(), summary);
        }

        boolean usesCompactRoomRows() {
            return ui.getScreen() instanceof Ldlib2MapSelectionScreen screen
                    && screen.usesCompactRoomRows();
        }
    }
}
