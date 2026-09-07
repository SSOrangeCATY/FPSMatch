package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

/** Pure responsive layout calculation used by the LDLib2 map-room screen. */
public record MapSelectionLayoutModel(
        Rect header,
        Rect toast,
        Rect filters,
        Rect roomList,
        Rect detail,
        Rect players,
        Rect actions,
        Rect browserActions,
        boolean compact
) {
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("layout dimensions must be non-negative");
            }
        }

        public boolean intersects(Rect other) {
            return x < other.x + other.width && other.x < x + width
                    && y < other.y + other.height && other.y < y + height;
        }
    }

    /**
     * Desktop gives the selected map a substantial preview rail. Compact windows preserve the
     * filter/action rail and room queue, while the full detail remains one explicit action away.
     */
    public static MapSelectionLayoutModel responsive(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("screen dimensions must be positive");
        }
        int headerHeight = Math.min(height, height >= 180 ? 44 : Math.max(24, height / 4));
        // The notice band is reserved, even while hidden, so asynchronous feedback never shifts
        // the first room row.
        int toastHeight = Math.min(24, Math.max(0, height - headerHeight));
        int contentTop = headerHeight + toastHeight;
        int contentHeight = Math.max(0, height - contentTop);

        // Three-column shell once the preview and room queue can both remain readable.
        if (width >= 560 && contentHeight >= 96) {
            int filterWidth = Math.max(124, Math.min(184, width * 18 / 100));
            int rightWidth = Math.max(220, Math.min(440, width * 38 / 100));
            int listWidth = width - filterWidth - rightWidth;
            if (listWidth < 180) {
                int deficit = 180 - listWidth;
                int shrinkRight = Math.min(deficit, Math.max(0, rightWidth - 190));
                rightWidth -= shrinkRight;
                deficit -= shrinkRight;
                int shrinkFilter = Math.min(deficit, Math.max(0, filterWidth - 112));
                filterWidth -= shrinkFilter;
                listWidth = width - filterWidth - rightWidth;
            }
            listWidth = Math.max(0, listWidth);
            int browserActionsHeight = Math.min(contentHeight, Math.min(54,
                    Math.max(40, contentHeight / 4)));
            int actionHeight = Math.min(Math.max(0, contentHeight - browserActionsHeight),
                    Math.min(46, Math.max(36, contentHeight / 5)));
            int filterHeight = contentHeight - browserActionsHeight - actionHeight;
            if (filterHeight < 68) {
                int deficit = 68 - filterHeight;
                int browserShrink = Math.min(deficit, Math.max(0, browserActionsHeight - 34));
                browserActionsHeight -= browserShrink;
                deficit -= browserShrink;
                actionHeight -= Math.min(deficit, Math.max(0, actionHeight - 30));
                filterHeight = contentHeight - browserActionsHeight - actionHeight;
            }
            filterHeight = Math.max(0, filterHeight);
            int actionsTop = contentTop + filterHeight;
            int browserTop = actionsTop + actionHeight;
            return new MapSelectionLayoutModel(
                    new Rect(0, 0, width, headerHeight),
                    new Rect(0, headerHeight, width, toastHeight),
                    new Rect(0, contentTop, filterWidth, filterHeight),
                    new Rect(filterWidth, contentTop, listWidth, contentHeight),
                    new Rect(filterWidth + listWidth, contentTop, rightWidth, contentHeight),
                    new Rect(filterWidth + listWidth, contentTop, rightWidth, contentHeight),
                    new Rect(0, actionsTop, filterWidth, actionHeight),
                    new Rect(0, browserTop, filterWidth, browserActionsHeight),
                    false
            );
        }

        // Narrow: keep browsing and filtering usable; the preview moves to the detail screen.
        int minimumLeftWidth = Math.min(116, Math.max(1, width - 1));
        int leftWidth = width < 2 ? width : Math.min(width - 1,
                Math.min(176, Math.max(minimumLeftWidth, width * 38 / 100)));
        int listWidth = Math.max(0, width - leftWidth);
        int browserActionsHeight = Math.min(contentHeight,
                Math.min(52, Math.max(38, contentHeight / 4)));
        int actionHeight = Math.min(Math.max(0, contentHeight - browserActionsHeight),
                Math.min(42, Math.max(32, contentHeight / 5)));
        int filterHeight = Math.max(0, contentHeight - browserActionsHeight - actionHeight);
        int actionsTop = contentTop + filterHeight;
        int browserTop = contentTop + contentHeight - browserActionsHeight;
        return new MapSelectionLayoutModel(
                new Rect(0, 0, width, headerHeight),
                new Rect(0, headerHeight, width, toastHeight),
                new Rect(0, contentTop, leftWidth, filterHeight),
                new Rect(leftWidth, contentTop, listWidth, contentHeight),
                new Rect(width, contentTop, 0, 0),
                new Rect(width, contentTop, 0, 0),
                new Rect(0, actionsTop, leftWidth, actionHeight),
                new Rect(0, browserTop, leftWidth, browserActionsHeight),
                true
        );
    }
}
