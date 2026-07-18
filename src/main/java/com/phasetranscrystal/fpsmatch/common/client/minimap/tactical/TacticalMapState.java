package com.phasetranscrystal.fpsmatch.common.client.minimap.tactical;

import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;

import java.util.Objects;
import java.util.Set;

public final class TacticalMapState {
    public enum FitMode {
        NONE,
        FLOOR,
        ALL
    }

    private final double panX;
    private final double panY;
    private final double zoom;
    private final FloorViewState floor;
    private final Set<String> hiddenMarkerTypes;
    private final int viewportWidth;
    private final int viewportHeight;
    private final FitMode fitMode;

    public TacticalMapState(
            double panX,
            double panY,
            double zoom,
            FloorViewState floor,
            Set<String> hiddenMarkerTypes,
            int viewportWidth,
            int viewportHeight,
            FitMode fitMode
    ) {
        if (!Double.isFinite(panX)
                || !Double.isFinite(panY)
                || !Double.isFinite(zoom)
                || zoom <= 0) {
            throw new IllegalArgumentException(
                    "Tactical camera must be finite with zoom > 0"
            );
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException(
                    "Tactical viewport must be positive"
            );
        }
        this.panX = panX;
        this.panY = panY;
        this.zoom = zoom;
        this.floor = Objects.requireNonNull(floor, "floor");
        this.hiddenMarkerTypes = Set.copyOf(Objects.requireNonNull(hiddenMarkerTypes, "hiddenMarkerTypes"));
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.fitMode = Objects.requireNonNull(fitMode, "fitMode");
    }

    public static TacticalMapState initial() {
        return new TacticalMapState(
                0, 0, 1.0, FloorViewState.automatic("ground"), Set.of(),
                800, 600, FitMode.ALL
        );
    }

    public TacticalMapState withZoom(double zoom) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.NONE);
    }

    public TacticalMapState withPan(double panX, double panY) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.NONE);
    }

    public TacticalMapState withCamera(
            double panX,
            double panY,
            double zoom
    ) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.NONE);
    }

    public TacticalMapState withFloor(FloorViewState floor) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.FLOOR);
    }

    public TacticalMapState updateFloor(FloorViewState floor) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, fitMode);
    }

    public TacticalMapState withHiddenMarkerTypes(Set<String> hiddenMarkerTypes) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, fitMode);
    }

    public TacticalMapState withViewport(int width, int height) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                width, height, fitMode == FitMode.NONE ? FitMode.NONE : fitMode);
    }

    public TacticalMapState fitFloor() {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.FLOOR);
    }

    public TacticalMapState fitAll() {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.ALL);
    }

    public TacticalMapState resolveCamera(
            double panX,
            double panY,
            double zoom
    ) {
        return copy(panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, FitMode.NONE);
    }

    private static TacticalMapState copy(
            double panX,
            double panY,
            double zoom,
            FloorViewState floor,
            Set<String> hiddenMarkerTypes,
            int viewportWidth,
            int viewportHeight,
            FitMode fitMode
    ) {
        return new TacticalMapState(
                panX, panY, zoom, floor, hiddenMarkerTypes,
                viewportWidth, viewportHeight, fitMode
        );
    }

    public double panX() { return panX; }
    public double panY() { return panY; }
    public double zoom() { return zoom; }
    public FloorViewState floor() { return floor; }
    public Set<String> hiddenMarkerTypes() { return hiddenMarkerTypes; }
    public int viewportWidth() { return viewportWidth; }
    public int viewportHeight() { return viewportHeight; }
    public FitMode fitMode() { return fitMode; }
}
