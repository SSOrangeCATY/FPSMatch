package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudAnchor;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable client minimap settings with contract clamps matching FPSMConfig.Client ranges.
 */
public final class MinimapClientSettings {
    private static final Pattern MARKER_NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern MARKER_PATH = Pattern.compile("[a-z0-9/._-]{1,256}");

    private final boolean enabled;
    private final HudAnchor anchor;
    private final int preferredSize;
    private final int minSize;
    private final int marginX;
    private final int marginY;
    private final int safeAreaPriority;
    private final ShapeMode shape;
    private final float opacity;
    private final float backgroundOpacity;
    private final MinimapOrientation orientation;
    private final double followZoom;
    private final boolean showRegionLabels;
    private final boolean showFloorLabel;
    private final boolean showCompass;
    private final AdjacentFloorMarkerStyle adjacentFloorMarkerStyle;
    private final List<String> markerFilter;
    private final int manualFloorTimeoutTicks;

    public MinimapClientSettings(
            boolean enabled,
            HudAnchor anchor,
            int preferredSize,
            int minSize,
            int marginX,
            int marginY,
            int safeAreaPriority,
            ShapeMode shape,
            float opacity,
            float backgroundOpacity,
            MinimapOrientation orientation,
            double followZoom,
            boolean showRegionLabels,
            boolean showFloorLabel,
            boolean showCompass,
            AdjacentFloorMarkerStyle adjacentFloorMarkerStyle,
            List<String> markerFilter,
            int manualFloorTimeoutTicks
    ) {
        this.enabled = enabled;
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.preferredSize = preferredSize;
        this.minSize = minSize;
        this.marginX = marginX;
        this.marginY = marginY;
        this.safeAreaPriority = safeAreaPriority;
        this.shape = Objects.requireNonNull(shape, "shape");
        this.opacity = opacity;
        this.backgroundOpacity = backgroundOpacity;
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.followZoom = followZoom;
        this.showRegionLabels = showRegionLabels;
        this.showFloorLabel = showFloorLabel;
        this.showCompass = showCompass;
        this.adjacentFloorMarkerStyle = Objects.requireNonNull(adjacentFloorMarkerStyle, "adjacentFloorMarkerStyle");
        this.markerFilter = List.copyOf(Objects.requireNonNull(markerFilter, "markerFilter"));
        this.manualFloorTimeoutTicks = manualFloorTimeoutTicks;
    }

    public static MinimapClientSettings defaults() {
        return new MinimapClientSettings(
                true,
                HudAnchor.TOP_LEFT,
                128,
                96,
                12,
                12,
                50,
                ShapeMode.SQUARE,
                1.0f,
                0.6f,
                MinimapOrientation.DOCUMENT,
                1.0,
                true,
                true,
                true,
                AdjacentFloorMarkerStyle.FADED_ARROWS,
                List.of(),
                100
        );
    }

    public MinimapClientSettings clamp() {
        int preferred = clampInt(preferredSize, 96, 512);
        int minimum = clampInt(minSize, 64, 512);
        if (minimum > preferred) {
            minimum = preferred;
        }
        List<String> filters = new ArrayList<>();
        for (String id : markerFilter) {
            if (isValidMarkerId(id)) {
                filters.add(id);
            }
        }
        return new MinimapClientSettings(
                enabled,
                anchor,
                preferred,
                minimum,
                clampInt(marginX, 0, 256),
                clampInt(marginY, 0, 256),
                clampInt(safeAreaPriority, 0, 1000),
                shape,
                clampFloat(opacity, 0f, 1f),
                clampFloat(backgroundOpacity, 0f, 1f),
                orientation,
                clampDouble(followZoom, 0.25, 8.0),
                showRegionLabels,
                showFloorLabel,
                showCompass,
                adjacentFloorMarkerStyle,
                filters,
                clampInt(manualFloorTimeoutTicks, 20, 1200)
        );
    }

    public MinimapClientSettings withAnchor(HudAnchor anchor) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withPreferredSize(int preferredSize) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withMinSize(int minSize) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withShape(ShapeMode shape) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withOpacity(float opacity) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withShowLabels(boolean showLabels) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withOrientation(MinimapOrientation orientation) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withFollowZoom(double followZoom) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withMarkerFilter(List<String> markerFilter) {
        return copyWith(enabled, anchor, preferredSize, minSize, marginX, marginY,
                safeAreaPriority, shape, opacity, backgroundOpacity, orientation,
                followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks);
    }

    public MinimapClientSettings withAdjacentFloorMarkerStyle(
            AdjacentFloorMarkerStyle adjacentFloorMarkerStyle
    ) {
        return copyWith(
                enabled,
                anchor,
                preferredSize,
                minSize,
                marginX,
                marginY,
                safeAreaPriority,
                shape,
                opacity,
                backgroundOpacity,
                orientation,
                followZoom,
                showRegionLabels,
                showFloorLabel,
                showCompass,
                adjacentFloorMarkerStyle,
                markerFilter,
                manualFloorTimeoutTicks
        );
    }

    private MinimapClientSettings copyWith(
            boolean enabled,
            HudAnchor anchor,
            int preferredSize,
            int minSize,
            int marginX,
            int marginY,
            int safeAreaPriority,
            ShapeMode shape,
            float opacity,
            float backgroundOpacity,
            MinimapOrientation orientation,
            double followZoom,
            boolean showRegionLabels,
            boolean showFloorLabel,
            boolean showCompass,
            AdjacentFloorMarkerStyle adjacentFloorMarkerStyle,
            List<String> markerFilter,
            int manualFloorTimeoutTicks
    ) {
        return new MinimapClientSettings(
                enabled, anchor, preferredSize, minSize, marginX, marginY, safeAreaPriority, shape, opacity,
                backgroundOpacity, orientation, followZoom, showRegionLabels, showFloorLabel, showCompass,
                adjacentFloorMarkerStyle, markerFilter, manualFloorTimeoutTicks
        );
    }

    public boolean enabled() { return enabled; }
    public HudAnchor anchor() { return anchor; }
    public int preferredSize() { return preferredSize; }
    public int minSize() { return minSize; }
    public int marginX() { return marginX; }
    public int marginY() { return marginY; }
    public int safeAreaPriority() { return safeAreaPriority; }
    public ShapeMode shape() { return shape; }
    public float opacity() { return opacity; }
    public float backgroundOpacity() { return backgroundOpacity; }
    public MinimapOrientation orientation() { return orientation; }
    public double followZoom() { return followZoom; }
    public boolean showRegionLabels() { return showRegionLabels; }
    public boolean showFloorLabel() { return showFloorLabel; }
    public boolean showCompass() { return showCompass; }
    public AdjacentFloorMarkerStyle adjacentFloorMarkerStyle() { return adjacentFloorMarkerStyle; }
    public List<String> markerFilter() { return markerFilter; }
    public int manualFloorTimeoutTicks() { return manualFloorTimeoutTicks; }

    private static boolean isValidMarkerId(String id) {
        if (id == null) {
            return false;
        }
        int separator = id.indexOf(':');
        if (separator <= 0 || separator != id.lastIndexOf(':') || separator == id.length() - 1) {
            return false;
        }
        if (!MARKER_NAMESPACE.matcher(id.substring(0, separator)).matches()) {
            return false;
        }
        String path = id.substring(separator + 1);
        if (!MARKER_PATH.matcher(path).matches()) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static MinimapOrientation parseOrientation(String raw) {
        return MinimapOrientation.valueOf(raw.toUpperCase(Locale.ROOT));
    }

    public static AdjacentFloorMarkerStyle parseAdjacentStyle(String raw) {
        return AdjacentFloorMarkerStyle.valueOf(raw.toUpperCase(Locale.ROOT));
    }

    public static ShapeMode parseShape(String raw) {
        return ShapeMode.valueOf(raw.toUpperCase(Locale.ROOT));
    }

    public static HudAnchor parseAnchor(String raw) {
        return HudAnchor.valueOf(raw.toUpperCase(Locale.ROOT));
    }
}
