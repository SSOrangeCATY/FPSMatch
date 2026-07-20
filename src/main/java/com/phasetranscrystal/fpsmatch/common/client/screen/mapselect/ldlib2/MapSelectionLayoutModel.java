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
        int headerHeight = Math.min(36, Math.max(28, height / 16));
        int actionHeight = width < 720 ? 66 : 42;
        int toolbarHeight = width < 720 ? 58 : 40;
        int contentTop = headerHeight + toolbarHeight;
        int contentHeight = Math.max(1, height - contentTop - actionHeight);
        if (width < 720) {
            int listHeight = Math.max(1, contentHeight * 55 / 100);
            int detailHeight = Math.max(1, contentHeight - listHeight);
            return new MapSelectionLayoutModel(
                    new Rect(0, 0, width, headerHeight),
                    new Rect(0, headerHeight, width, toolbarHeight),
                    new Rect(0, contentTop, width, listHeight),
                    new Rect(0, contentTop + listHeight, width, detailHeight),
                    new Rect(0, contentTop + listHeight, width, detailHeight),
                    new Rect(0, height - actionHeight, width, actionHeight),
                    true
            );
        }
        int detailWidth = Math.max(240, Math.min(340, width * 36 / 100));
        int listWidth = Math.max(1, width - detailWidth);
        return new MapSelectionLayoutModel(
                new Rect(0, 0, width, headerHeight),
                new Rect(0, headerHeight, width, toolbarHeight),
                new Rect(0, contentTop, listWidth, contentHeight),
                new Rect(listWidth, contentTop, detailWidth, contentHeight),
                new Rect(listWidth, contentTop, detailWidth, contentHeight),
                new Rect(0, height - actionHeight, width, actionHeight),
                false
        );
    }
}
