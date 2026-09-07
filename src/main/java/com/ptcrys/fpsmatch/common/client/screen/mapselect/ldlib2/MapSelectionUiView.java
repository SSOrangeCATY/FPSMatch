package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleSelector;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleTextField;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.MapThumbnailRenderer;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;
import java.util.Locale;

/** Builds the map-room element tree and bridges element callbacks back to its screen owner. */
final class MapSelectionUiView {
    static final int ROW_HEIGHT = 56;
    static final int ROW_GAP = 4;

    private MapSelectionUiView() {
    }

    static Parts build() {
        UIElement root = new UIElement().setId(MapSelectionWidgetCatalog.ROOT);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);

        UIElement headerPanel = new UIElement().setId(MapSelectionWidgetCatalog.HEADER + ".band");
        headerPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(8).height(42));
        headerPanel.style(style -> style.background(FPSMMapSelectTheme.panelTexture(
                0xD913171A, FPSMMapSelectTheme.BORDER_SOFT)));
        Label system = label(MapSelectionWidgetCatalog.HEADER + ".system",
                Component.translatable("gui.fpsm.map_select.eyebrow"));
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(3).height(10));
        FPSMMapSelectTheme.systemLabel(system);
        Label header = label(MapSelectionWidgetCatalog.HEADER,
                Component.translatable("gui.fpsm.map_select.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(142).top(14).height(22));
        FPSMMapSelectTheme.title(header);
        Label scope = label(MapSelectionWidgetCatalog.HEADER + ".scope",
                Component.translatable("gui.fpsm.map_select.sync.ready"));
        scope.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(10).top(17).width(124).height(14));
        FPSMMapSelectTheme.status(scope, FPSMMapSelectTheme.MUTED);
        headerPanel.addChildren(system, header, scope);

        AccessibleTextField search = new AccessibleTextField();
        search.setId(MapSelectionWidgetCatalog.SEARCH);
        search.setAccessibleName(Component.translatable("gui.fpsm.map_select.search"));
        search.setAnyString();
        search.setText("");
        search.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(30).height(22));
        FPSMMapSelectTheme.input(search, Component.translatable("gui.fpsm.map_select.search"));

        VirtualScrollerView<MapRoomSummary> roomList = new VirtualScrollerView<>();
        roomList.setId(MapSelectionWidgetCatalog.ROOM_LIST);
        roomList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(18).top(68).width(320).height(220));
        FPSMMapSelectTheme.virtualScroller(roomList);
        roomList.viewContainer(container -> container.layout(layout -> layout
                .paddingHorizontal(5).paddingVertical(5)));
        roomList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(ROW_HEIGHT + ROW_GAP).overscanPixels(ROW_HEIGHT * 2));
        Label roomListHeading = label(MapSelectionWidgetCatalog.ROOM_LIST_HEADING,
                Component.translatable("gui.fpsm.map_select.rooms.title"));
        roomListHeading.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).right(6).top(3).height(16));
        FPSMMapSelectTheme.sectionTitle(roomListHeading);
        Label emptyState = label(MapSelectionWidgetCatalog.EMPTY_STATE,
                Component.translatable("gui.fpsm.map_select.empty"));
        emptyState.setAllowHitTest(false);
        emptyState.setFocusable(false);
        emptyState.setVisible(false);
        FPSMMapSelectTheme.muted(emptyState);
        emptyState.textStyle(style -> style.fontSize(10).textWrap(TextWrap.WRAP));

        UIElement detailPanel = new UIElement().setId(MapSelectionWidgetCatalog.ROOM_DETAIL);
        detailPanel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(350).top(68).width(260).height(220));
        FPSMMapSelectTheme.panel(detailPanel);
        Label detailHeading = label("fpsmatch.map_selection.detail.heading",
                Component.translatable("gui.fpsm.map_select.preview.title"));
        detailHeading.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(7).height(15));
        FPSMMapSelectTheme.sectionTitle(detailHeading);
        MapPreviewElement preview = new MapPreviewElement("fpsmatch.map_selection.detail.preview");
        preview.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(26).height(96));
        FPSMMapSelectTheme.preview(preview);
        Label previewTitle = label("fpsmatch.map_selection.detail.name", Component.empty());
        previewTitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(128).height(16));
        FPSMMapSelectTheme.mapIdentity(previewTitle);
        previewTitle.setOverflowVisible(false);
        previewTitle.textStyle(style -> style.textWrap(TextWrap.HIDE));
        Label previewMeta = label("fpsmatch.map_selection.detail.meta", Component.empty());
        previewMeta.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(146).height(12));
        FPSMMapSelectTheme.systemLabel(previewMeta);
        previewMeta.setOverflowVisible(false);
        previewMeta.textStyle(style -> style.textWrap(TextWrap.HIDE));
        Label detailLabel = label("fpsmatch.map_selection.detail.text",
                Component.translatable("gui.fpsm.map_select.preview.none"));
        detailLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(162).height(48));
        FPSMMapSelectTheme.body(detailLabel);
        detailLabel.setOverflowVisible(false);
        detailLabel.textStyle(style -> style.textWrap(TextWrap.WRAP));
        Label rulesLabel = label("fpsmatch.map_selection.detail.rules", Component.empty());
        rulesLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(214).height(38));
        FPSMMapSelectTheme.muted(rulesLabel);
        rulesLabel.setOverflowVisible(false);
        rulesLabel.textStyle(style -> style.textWrap(TextWrap.WRAP).lineSpacing(1));
        Label playersHeading = label("fpsmatch.map_selection.detail.players_heading",
                Component.translatable("gui.fpsm.map_select.players.title"));
        playersHeading.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(256).height(14));
        FPSMMapSelectTheme.systemLabel(playersHeading);
        VirtualScrollerView<MapRoomPlayerInfo> playerList = new VirtualScrollerView<>();
        playerList.setId(MapSelectionWidgetCatalog.PLAYERS);
        playerList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(272).bottom(10));
        FPSMMapSelectTheme.virtualScroller(playerList);
        playerList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(26).overscanPixels(52));
        playerList.setItemUIProvider(info -> playerRow(
                MapSelectionWidgetCatalog.PLAYERS + "." + info.uuid(), info));
        detailPanel.addChildren(detailHeading, preview, previewTitle, previewMeta,
                detailLabel, rulesLabel, playersHeading, playerList);

        UIElement filters = new UIElement().setId(MapSelectionWidgetCatalog.FILTERS);
        filters.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).top(52).width(180).height(220));
        FPSMMapSelectTheme.panel(filters);
        Label filterHeading = label(MapSelectionWidgetCatalog.FILTER_HEADING,
                Component.translatable("gui.fpsm.map_select.filters.title"));
        filterHeading.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(7).height(16));
        FPSMMapSelectTheme.sectionTitle(filterHeading);
        AccessibleSelector<String> stateSelector = new AccessibleSelector<>();
        stateSelector.setId(MapSelectionWidgetCatalog.STATE_FILTER);
        stateSelector.setAccessibleName(() -> stateFilterText(stateSelector.getValue()));
        stateSelector.setCandidateUIProvider(value -> selectorLabel(stateFilterText(value)));
        stateSelector.setCandidates(List.of("all", "waiting", "running", "open"));
        stateSelector.setSelected("all", false);
        FPSMMapSelectTheme.selector(stateSelector);
        AccessibleSelector<String> modeSelector = new AccessibleSelector<>();
        modeSelector.setId(MapSelectionWidgetCatalog.MODE_FILTER);
        modeSelector.setAccessibleName(() -> modeFilterText(modeSelector.getValue()));
        modeSelector.setCandidateUIProvider(value -> selectorLabel(modeFilterText(value)));
        modeSelector.setCandidates(List.of("all"));
        modeSelector.setSelected("all", false);
        FPSMMapSelectTheme.selector(modeSelector);
        filters.addChildren(filterHeading, search, stateSelector, modeSelector);

        UIElement toast = new UIElement().setId(MapSelectionWidgetCatalog.TOAST);
        toast.setVisible(false);
        toast.setAllowHitTest(false);
        toast.setFocusable(false);
        FPSMMapSelectTheme.statusSurface(toast, FPSMMapSelectTheme.BORDER);
        Label toastText = label(MapSelectionWidgetCatalog.TOAST + ".text", Component.empty());
        toastText.setAllowHitTest(false);
        toastText.setFocusable(false);
        toastText.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(3).bottom(3));
        toast.addChild(toastText);

        AccessibleButton open = button(MapSelectionWidgetCatalog.ACTIONS + ".join",
                "gui.fpsm.map_select.detail", 8);
        AccessibleButton manage = button(MapSelectionWidgetCatalog.ACTIONS + ".manage",
                "gui.fpsm.map_select.manage", 8);
        for (AccessibleButton action : List.of(open, manage)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).top(8).width(80).height(22));
        }
        FPSMMapSelectTheme.button(open, FPSMMapSelectTheme.ButtonKind.PRIMARY);
        FPSMMapSelectTheme.button(manage, FPSMMapSelectTheme.ButtonKind.SECONDARY);
        AccessibleButton refresh = button(MapSelectionWidgetCatalog.BROWSER_ACTIONS + ".refresh",
                "gui.fpsm.map_select.refresh", 8);
        AccessibleButton close = button(MapSelectionWidgetCatalog.BROWSER_ACTIONS + ".close",
                "gui.done", 8);
        for (AccessibleButton action : List.of(refresh, close)) {
            action.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).top(8).width(80).height(20));
        }
        FPSMMapSelectTheme.button(refresh, FPSMMapSelectTheme.ButtonKind.QUIET);
        FPSMMapSelectTheme.button(close, FPSMMapSelectTheme.ButtonKind.QUIET);
        UIElement actions = new UIElement().setId(MapSelectionWidgetCatalog.ACTIONS);
        FPSMMapSelectTheme.panel(actions);
        actions.addChildren(open, manage);
        UIElement browserActions = new UIElement().setId(MapSelectionWidgetCatalog.BROWSER_ACTIONS);
        browserActions.style(style -> style.background(new ColorRectTexture(0x90101417)));
        browserActions.addChildren(refresh, close);
        root.addChildren(headerPanel, filters, roomListHeading, roomList, emptyState,
                detailPanel, toast, actions, browserActions);

        Parts parts = new Parts(
                ModularUI.of(UI.of(root, screenSize -> Size.of(
                        screenSize.getWidth(), screenSize.getHeight()))),
                headerPanel, scope, roomList, roomListHeading, emptyState,
                detailPanel, preview, previewTitle, previewMeta, detailLabel, rulesLabel,
                playersHeading, playerList, search, filters, filterHeading,
                stateSelector, modeSelector, actions, browserActions, toast, toastText,
                List.of(open, manage), List.of(refresh, close), refresh, close, open, manage
        );
        roomList.setItemUIProvider((UIElementProvider<MapRoomSummary>) summary -> roomRow(parts, summary));
        search.setTextResponder(parts::screenSearch);
        return parts;
    }

    static String roomId(MapRoomSummary summary) {
        return MapSelectionWidgetCatalog.ROOM_LIST + "." + summary.gameType() + "." + summary.mapName();
    }

    private static AccessibleButton roomRow(Parts parts, MapRoomSummary summary) {
        AccessibleButton row = (AccessibleButton) new AccessibleButton().noText();
        row.setId(roomId(summary));
        row.setAccessibleName(() -> Component.translatable(
                "gui.fpsm.map_select.info.map", summary.displayName(), summary.mapName()));
        row.setAccessibleState(() -> Ldlib2MapSelectionScreen.statusText(summary));
        row.setOnClick(event -> parts.screenSelect(summary));
        row.layout(layout -> layout.height(ROW_HEIGHT).widthPercent(100).marginBottom(ROW_GAP));
        FPSMMapSelectTheme.roomRow(row, Ldlib2MapSelectionScreen.roomStatusColor(summary),
                parts.isSelected(summary));
        addRoomRowContents(row, summary, parts.usesCompactRoomRows());
        return row;
    }

    private static void addRoomRowContents(AccessibleButton row, MapRoomSummary summary, boolean compact) {
        int statusColor = Ldlib2MapSelectionScreen.roomStatusColor(summary);
        UIElement status = new UIElement();
        status.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).top(0).bottom(0).width(3));
        status.style(style -> style.background(new ColorRectTexture(statusColor)));
        int previewSize = compact ? 38 : 44;
        Ldlib2MapThumbnailElement thumbnail = new Ldlib2MapThumbnailElement(
                row.getElementName() + ".preview", "", summary.mapName(),
                summary.gameType(), summary.displayName());
        thumbnail.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).top((ROW_HEIGHT - previewSize) / 2).width(previewSize).height(previewSize));
        int textLeft = previewSize + 14;
        int trailingWidth = compact ? 52 : 66;
        Label name = label(row.getElementName() + ".name", Component.literal(summary.displayName()));
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(textLeft).right(trailingWidth).top(8).height(15));
        FPSMMapSelectTheme.sectionTitle(name);
        name.setOverflowVisible(false);
        name.textStyle(style -> style.textWrap(TextWrap.HIDE));
        Label meta = label(row.getElementName() + ".meta",
                Component.literal(summary.gameType().toUpperCase(Locale.ROOT) + " / " + summary.mapName()));
        meta.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(textLeft).right(trailingWidth).top(30).height(12));
        FPSMMapSelectTheme.systemLabel(meta);
        meta.setOverflowVisible(false);
        meta.textStyle(style -> style.textWrap(TextWrap.HIDE));
        Label players = label(row.getElementName() + ".players",
                Component.literal(summary.joinedPlayers() + "/" + Ldlib2MapSelectionScreen.maxPlayers(summary)));
        players.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(8).top(8).width(trailingWidth - 12).height(12));
        FPSMMapSelectTheme.status(players,
                summary.full() ? FPSMMapSelectTheme.DANGER : FPSMMapSelectTheme.TEXT);
        Label state = label(row.getElementName() + ".status",
                Ldlib2MapSelectionScreen.statusText(summary));
        state.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(8).top(31).width(trailingWidth - 10).height(12));
        FPSMMapSelectTheme.status(state, statusColor);
        state.setOverflowVisible(false);
        state.textStyle(style -> style.textWrap(TextWrap.HIDE));
        row.addChildren(status, thumbnail, name, meta, players, state);
    }

    private static Label playerRow(String id, MapRoomPlayerInfo info) {
        Label row = label(id, playerText(info));
        row.layout(layout -> layout.height(26).widthPercent(100)
                .paddingLeft(8).paddingRight(8));
        FPSMMapSelectTheme.muted(row);
        row.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));
        row.style(style -> style.background(FPSMMapSelectTheme.panelTexture(
                0xFF171C20, FPSMMapSelectTheme.BORDER_SOFT)));
        return row;
    }

    static Component playerText(MapRoomPlayerInfo info) {
        Component text = Component.literal(info.name() + " / " + info.teamName());
        if (info.spectator()) {
            text = text.copy().append(Component.literal(" / "))
                    .append(Component.translatable("gui.fpsm.map_select.spectating"));
        } else if (info.ready()) {
            text = text.copy().append(Component.literal(" / "))
                    .append(Component.translatable("gui.fpsm.team_manage.ready_mark"));
        }
        return text;
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
        FPSMMapSelectTheme.body(label);
        label.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));
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
        String normalized = Ldlib2MapSelectionScreen.normalizeMode(mode);
        Component value = "all".equals(normalized)
                ? Component.translatable("gui.fpsm.map_select.filter.mode.all")
                : Component.literal(normalized.toUpperCase(Locale.ROOT));
        return Component.translatable("gui.fpsm.map_select.filter.mode_selector", value);
    }

    static final class MapPreviewElement extends UIElement {
        private MapRoomSummary summary;
        private MapRoomDetail detail;

        MapPreviewElement(String id) {
            setId(id);
            setAllowHitTest(false);
            setFocusable(false);
        }

        void show(MapRoomSummary summary, MapRoomDetail detail) {
            this.summary = summary;
            this.detail = detail != null && Ldlib2MapSelectionScreen.sameRoom(summary, detail.summary())
                    ? detail : null;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            if (summary == null || getSizeWidth() <= 0 || getSizeHeight() <= 0) return;
            String texture = "";
            if (detail != null) {
                texture = detail.backgroundTexture().isBlank()
                        ? detail.iconTexture() : detail.backgroundTexture();
            }
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            MapThumbnailRenderer.render(context.graphics, x, y, width, height, texture,
                    summary.mapName(), summary.gameType(), summary.displayName(), false);
            context.graphics.fill(x, y, x + 3, y + height,
                    Ldlib2MapSelectionScreen.roomStatusColor(summary));
            context.graphics.fill(x, y + height - 1, x + width, y + height,
                    FPSMMapSelectTheme.BORDER);
        }
    }

    record Parts(
            ModularUI ui, UIElement headerPanel, Label scopeLabel,
            VirtualScrollerView<MapRoomSummary> roomList, Label roomListHeading, Label emptyState,
            UIElement detailPanel, MapPreviewElement preview, Label previewTitle, Label previewMeta,
            Label detailLabel, Label rulesLabel, Label playersHeading,
            VirtualScrollerView<MapRoomPlayerInfo> playerList,
            AccessibleTextField search, UIElement filters, Label filterHeading,
            AccessibleSelector<String> stateSelector, AccessibleSelector<String> modeSelector,
            UIElement actions, UIElement browserActions, UIElement toast, Label toastLabel,
            List<AccessibleButton> actionButtons, List<AccessibleButton> browserActionButtons,
            AccessibleButton refresh, AccessibleButton close, AccessibleButton open,
            AccessibleButton manage
    ) {
        void screenSelect(MapRoomSummary summary) {
            if (ui.getScreen() instanceof Ldlib2MapSelectionScreen screen) screen.select(summary);
        }

        void screenSearch(String value) {
            if (ui.getScreen() instanceof Ldlib2MapSelectionScreen screen) screen.setQuery(value);
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
