package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import java.util.List;

/** Stable IDs used by the LDLib2 map-room UI and its passive refresh bridge. */
public final class MapSelectionWidgetCatalog {
    public static final String ROOT = "fpsmatch.map_selection.root";
    public static final String HEADER = "fpsmatch.map_selection.header";
    public static final String SEARCH = "fpsmatch.map_selection.search";
    public static final String FILTERS = "fpsmatch.map_selection.filters";
    public static final String ROOM_LIST = "fpsmatch.map_selection.room_list";
    public static final String ROOM_DETAIL = "fpsmatch.map_selection.room_detail";
    public static final String PLAYERS = "fpsmatch.map_selection.players";
    public static final String ACTIONS = "fpsmatch.map_selection.actions";
    public static final String TOAST = "fpsmatch.map_selection.toast";

    private MapSelectionWidgetCatalog() {
    }

    public static List<String> ids() {
        return List.of(ROOT, HEADER, SEARCH, FILTERS, ROOM_LIST, ROOM_DETAIL,
                PLAYERS, ACTIONS, TOAST);
    }
}
