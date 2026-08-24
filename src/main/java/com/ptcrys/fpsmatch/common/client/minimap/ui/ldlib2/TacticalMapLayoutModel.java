package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

/**
 * Pure tactical-map layout contract shared by every active screen size.
 */
public record TacticalMapLayoutModel(
        Mode mode,
        Rect canvas,
        Rect sidebar,
        Rect controlsToggle
) {
    private static final int WIDE_MIN_WIDTH = 640;
    private static final int COMPACT_MIN_WIDTH = 420;
    private static final int WIDE_SIDEBAR_PIXELS = 196;
    private static final int COMPACT_SIDEBAR_PIXELS = 156;
    private static final int TOGGLE_WIDTH = 72;
    private static final int TOGGLE_HEIGHT = 22;
    private static final int TOGGLE_MARGIN = 8;

    public enum Mode {
        WIDE,
        COMPACT,
        DRAWER
    }

    public static TacticalMapLayoutModel responsive(
            int width,
            int height,
            boolean drawerOpen
    ) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Tactical map dimensions must be positive"
            );
        }
        Mode mode = modeFor(width);
        Rect canvas;
        Rect sidebar;
        if (mode == Mode.DRAWER) {
            canvas = new Rect(0, 0, width, height);
            sidebar = new Rect(
                    0,
                    0,
                    drawerOpen ? Math.min(WIDE_SIDEBAR_PIXELS, width) : 0,
                    height
            );
        } else {
            int sidebarWidth = mode == Mode.WIDE
                    ? WIDE_SIDEBAR_PIXELS
                    : COMPACT_SIDEBAR_PIXELS;
            sidebar = new Rect(0, 0, sidebarWidth, height);
            canvas = new Rect(
                    sidebarWidth,
                    0,
                    width - sidebarWidth,
                    height
            );
        }
        return new TacticalMapLayoutModel(
                mode,
                canvas,
                sidebar,
                controlsToggle(width, height)
        );
    }

    private static Mode modeFor(int width) {
        if (width >= WIDE_MIN_WIDTH) {
            return Mode.WIDE;
        }
        return width >= COMPACT_MIN_WIDTH ? Mode.COMPACT : Mode.DRAWER;
    }

    private static Rect controlsToggle(int width, int height) {
        int toggleWidth = Math.min(TOGGLE_WIDTH, width);
        int toggleHeight = Math.min(TOGGLE_HEIGHT, height);
        return new Rect(
                Math.max(0, width - toggleWidth - TOGGLE_MARGIN),
                Math.max(0, Math.min(TOGGLE_MARGIN, height - toggleHeight)),
                toggleWidth,
                toggleHeight
        );
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (x < 0 || y < 0 || width < 0 || height < 0) {
                throw new IllegalArgumentException(
                        "Tactical layout rectangles cannot be negative"
                );
            }
        }

        public boolean intersects(Rect other) {
            if (other == null || width == 0 || height == 0
                    || other.width == 0 || other.height == 0) {
                return false;
            }
            return x < (long) other.x + other.width
                    && (long) x + width > other.x
                    && y < (long) other.y + other.height
                    && (long) y + height > other.y;
        }
    }
}
