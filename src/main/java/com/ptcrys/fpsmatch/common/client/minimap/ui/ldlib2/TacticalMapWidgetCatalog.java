package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import java.util.List;

public final class TacticalMapWidgetCatalog {
    public static final String ROOT = "minimap.tactical.root";
    public static final String CANVAS = "minimap.tactical.canvas";
    public static final String FLOOR_SELECTOR = "minimap.tactical.floor_selector";
    public static final String AUTO_MANUAL = "minimap.tactical.auto_manual";
    public static final String FILTER_LIST = "minimap.tactical.filter_list";
    public static final String LEGEND = "minimap.tactical.legend";
    public static final String REGION_DETAIL = "minimap.tactical.region_detail";
    public static final String FIT = "minimap.tactical.fit";
    public static final String CLOSE = "minimap.tactical.close";
    public static final String CONTROLS_TOGGLE = "minimap.tactical.controls_toggle";
    public static final String STATE = "minimap.tactical.state";

    private TacticalMapWidgetCatalog() {
    }

    public static TacticalMapWidgetCatalog defaultCatalog() {
        return new TacticalMapWidgetCatalog();
    }

    public List<String> ids() {
        return List.of(
                ROOT,
                CANVAS,
                FLOOR_SELECTOR,
                AUTO_MANUAL,
                FILTER_LIST,
                LEGEND,
                REGION_DETAIL,
                FIT,
                CLOSE,
                CONTROLS_TOGGLE,
                STATE
        );
    }
}
