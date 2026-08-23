package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import java.util.List;

public final class MinimapHudWidgetCatalog {
    public static final String ROOT = "minimap.hud.root";
    public static final String CANVAS = "minimap.hud.canvas";
    public static final String CONFIG_PREVIEW = "minimap.hud.config_preview";
    public static final String PLACEHOLDER = "minimap.hud.placeholder";
    public static final String COMPASS = "minimap.hud.compass";

    private MinimapHudWidgetCatalog() {
    }

    public static MinimapHudWidgetCatalog defaultCatalog() {
        return new MinimapHudWidgetCatalog();
    }

    public List<String> ids() {
        return List.of(ROOT, CANVAS, CONFIG_PREVIEW, PLACEHOLDER, COMPASS);
    }
}