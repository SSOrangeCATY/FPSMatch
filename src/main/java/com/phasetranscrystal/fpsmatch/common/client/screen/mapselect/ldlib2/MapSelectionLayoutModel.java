package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

/** Pure responsive layout calculation used by the LDLib2 map-room screen. */
public record MapSelectionLayoutModel(
        Rect header,
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
     * Desktop keeps filters, room list, and detail in a horizontal shell. Compact windows keep
     * the filter/action/list order but omit the detail rail so the browser remains usable.
     */
    public static MapSelectionLayoutModel responsive(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("screen dimensions must be positive");
        }
        int headerHeight = Math.min(height, Math.max(20, Math.min(32, height / 18)));
        int contentTop = headerHeight;
        int contentHeight = Math.max(1, height - contentTop);

        // Three-column shell once there is room for filter rail + list + right rail.
        if (width >= 420) {
            int filterWidth;
            int rightWidth;
            if (width >= 900) {
                filterWidth = Math.max(150, Math.min(190, width * 18 / 100));
                rightWidth = Math.max(240, Math.min(300, width * 28 / 100));
            } else if (width >= 700) {
                filterWidth = Math.max(140, Math.min(170, width * 18 / 100));
                rightWidth = Math.max(210, Math.min(260, width * 28 / 100));
            } else {
                // Mid desktop: keep the list dominant while leaving both side rails usable.
                filterWidth = Math.max(104, Math.min(140, width * 22 / 100));
                rightWidth = Math.max(144, Math.min(190, width * 32 / 100));
            }
            int listWidth = width - filterWidth - rightWidth;
            if (listWidth < 120) {
                int deficit = 120 - listWidth;
                int shrinkRight = Math.min(deficit, Math.max(0, rightWidth - 128));
                rightWidth -= shrinkRight;
                deficit -= shrinkRight;
                int shrinkFilter = Math.min(deficit, Math.max(0, filterWidth - 96));
                filterWidth -= shrinkFilter;
                listWidth = width - filterWidth - rightWidth;
            }
            listWidth = Math.max(0, listWidth);
            int browserActionsHeight = Math.min(contentHeight,
                    Math.min(54, Math.max(48, contentHeight / 4)));
            int actionHeight = Math.min(Math.max(0, contentHeight - browserActionsHeight),
                    Math.min(48, Math.max(36, contentHeight * 18 / 100)));
            int filterHeight = contentHeight - browserActionsHeight - actionHeight;
            if (filterHeight < 72) {
                int deficit = 72 - filterHeight;
                int browserShrink = Math.min(deficit, Math.max(0, browserActionsHeight - 36));
                browserActionsHeight -= browserShrink;
                deficit -= browserShrink;
                actionHeight -= Math.min(deficit, Math.max(0, actionHeight - 28));
                filterHeight = contentHeight - browserActionsHeight - actionHeight;
            }
            filterHeight = Math.max(0, filterHeight);
            int actionsTop = contentTop + filterHeight;
            int browserTop = actionsTop + actionHeight;
            return new MapSelectionLayoutModel(
                    new Rect(0, 0, width, headerHeight),
                    new Rect(0, contentTop, filterWidth, filterHeight),
                    new Rect(filterWidth, contentTop, listWidth, contentHeight),
                    new Rect(filterWidth + listWidth, contentTop, rightWidth, contentHeight),
                    new Rect(filterWidth + listWidth, contentTop, rightWidth, contentHeight),
                    new Rect(0, actionsTop, filterWidth, actionHeight),
                    new Rect(0, browserTop, filterWidth, browserActionsHeight),
                    false
            );
        }

        // Narrow: preserve the filter/action rail on the left, give the room list the full-height
        // right column, and omit only the detail rail.
        int minimumLeftWidth = Math.min(104, Math.max(1, width - 1));
        int leftWidth = width < 2 ? width : Math.min(width - 1,
                Math.max(minimumLeftWidth, width * 40 / 100));
        int listWidth = Math.max(0, width - leftWidth);
        int browserActionsHeight = Math.min(contentHeight,
                Math.min(42, Math.max(36, contentHeight / 5)));
        int actionHeight = Math.min(Math.max(0, contentHeight - browserActionsHeight),
                Math.min(36, Math.max(30, contentHeight * 16 / 100)));
        int filterHeight = Math.max(0, contentHeight - browserActionsHeight - actionHeight);
        int actionsTop = contentTop + filterHeight;
        int browserTop = contentTop + contentHeight - browserActionsHeight;
        return new MapSelectionLayoutModel(
                new Rect(0, 0, width, headerHeight),
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
