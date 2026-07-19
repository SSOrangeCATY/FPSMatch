package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

/** Pure responsive layout calculation used by the LDLib2 map-room screen. */
public record MapSelectionLayoutModel(
        Rect header,
        Rect filters,
        Rect roomList,
        Rect detail,
        Rect players,
        Rect actions,
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

    public static MapSelectionLayoutModel responsive(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("screen dimensions must be positive");
        }
        int headerHeight = Math.min(54, Math.max(42, height / 8));
        int actionHeight = width < 620 ? 58 : Math.min(40, Math.max(32, height / 10));
        int contentTop = headerHeight;
        int contentHeight = Math.max(1, height - headerHeight - actionHeight);
        if (width < 620) {
            int filterHeight = 38;
            int workspaceHeight = Math.max(2, contentHeight - filterHeight);
            int listHeight = Math.max(1, workspaceHeight * 52 / 100);
            int detailHeight = Math.max(1, workspaceHeight - listHeight);
            return new MapSelectionLayoutModel(
                    new Rect(0, 0, width, headerHeight),
                    new Rect(0, contentTop, width, filterHeight),
                    new Rect(0, contentTop + filterHeight, width, listHeight),
                    new Rect(0, contentTop + filterHeight + listHeight, width, detailHeight),
                    new Rect(0, contentTop + filterHeight + listHeight, width, detailHeight),
                    new Rect(0, height - actionHeight, width, actionHeight),
                    true
            );
        }
        int filterWidth = Math.max(150, Math.min(210, width / 5));
        int detailWidth = Math.max(220, Math.min(310, width / 3));
        int listWidth = Math.max(1, width - filterWidth - detailWidth);
        return new MapSelectionLayoutModel(
                new Rect(0, 0, width, headerHeight),
                new Rect(0, contentTop, filterWidth, contentHeight),
                new Rect(filterWidth, contentTop, listWidth, contentHeight),
                new Rect(filterWidth + listWidth, contentTop, detailWidth, contentHeight),
                new Rect(filterWidth + listWidth, contentTop, detailWidth, contentHeight),
                new Rect(0, height - actionHeight, width, actionHeight),
                false
        );
    }
}
