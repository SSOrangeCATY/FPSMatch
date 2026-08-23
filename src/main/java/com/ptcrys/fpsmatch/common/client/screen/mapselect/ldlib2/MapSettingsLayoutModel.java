package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

/** Pure responsive geometry for the horizontal map-settings editor. */
public record MapSettingsLayoutModel(Rect sidebar, Rect content) {
    public record Rect(int x, int y, int width, int height) {
        public boolean intersects(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    /** Geometry relative to the content panel. The popup intentionally overlays the list. */
    public record ToolbarLayout(Rect search, Rect categoryFilter, Rect list, Rect categoryPopup) {
    }

    public static MapSettingsLayoutModel responsive(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("screen dimensions must be positive");
        }
        int horizontalMargin = Math.min(16, Math.max(8, width / 32));
        int verticalMargin = Math.min(14, Math.max(8, height / 24));
        int gap = Math.min(10, Math.max(6, width / 64));
        int availableWidth = Math.max(2, width - horizontalMargin * 2 - gap);
        int preferredSidebarWidth = Math.min(150, Math.max(96, width * 26 / 100));
        int sidebarWidth = Math.max(1,
                Math.min(preferredSidebarWidth, availableWidth * 40 / 100));
        int contentWidth = Math.max(1, availableWidth - sidebarWidth);
        int contentHeight = Math.max(1, height - verticalMargin * 2);
        Rect sidebar = new Rect(horizontalMargin, verticalMargin, sidebarWidth, contentHeight);
        Rect content = new Rect(horizontalMargin + sidebarWidth + gap, verticalMargin,
                contentWidth, contentHeight);
        return new MapSettingsLayoutModel(sidebar, content);
    }

    public static ToolbarLayout toolbar(int contentWidth, int contentHeight, int categoryCount) {
        if (contentWidth <= 0 || contentHeight <= 0 || categoryCount < 0) {
            throw new IllegalArgumentException("content dimensions and category count must be valid");
        }
        int padding = contentWidth >= 20 ? 6 : 1;
        int usableWidth = Math.max(1, contentWidth - padding * 2);
        int gap = usableWidth >= 80 ? 4 : usableWidth >= 3 ? 1 : 0;
        int controlsWidth = Math.max(1, usableWidth - gap);
        int preferredFilterWidth = Math.min(104, Math.max(64, contentWidth * 34 / 100));
        int filterWidth = Math.min(preferredFilterWidth, Math.max(1, controlsWidth - 52));
        int searchWidth = Math.max(1, controlsWidth - filterWidth);

        Rect search = new Rect(padding, 5, searchWidth, 18);
        Rect categoryFilter = new Rect(padding + searchWidth + gap, 5, filterWidth, 18);
        Rect list = new Rect(padding, 27, usableWidth, Math.max(1, contentHeight - 33));

        int popupWidth = filterWidth;
        int popupTop = Math.min(26, Math.max(0, contentHeight - 1));
        int desiredPopupHeight = Math.max(40, categoryCount * 16 + 26);
        int popupHeight = Math.max(1, Math.min(desiredPopupHeight, contentHeight - popupTop - padding));
        Rect categoryPopup = new Rect(contentWidth - padding - popupWidth, popupTop,
                popupWidth, popupHeight);
        return new ToolbarLayout(search, categoryFilter, list, categoryPopup);
    }
}
