package com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2;

import com.phasetranscrystal.fpsmatch.common.client.PauseMenuLayoutModel;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2.MapSelectionLayoutModel;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2.MapSelectionWidgetCatalog;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2.MapSettingsLayoutModel;
import com.phasetranscrystal.fpsmatch.common.client.screen.shop.ldlib2.ShopEditorLayoutModel;
import com.phasetranscrystal.fpsmatch.common.client.screen.shop.ldlib2.ShopEditorWidgetCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ldlib2ScreenContractTest {
    @Test
    void mapAndShopWidgetCatalogsAreStableAndUnique() {
        assertEquals(MapSelectionWidgetCatalog.ids().size(),
                new HashSet<>(MapSelectionWidgetCatalog.ids()).size());
        assertEquals(ShopEditorWidgetCatalog.ids().size(),
                new HashSet<>(ShopEditorWidgetCatalog.ids()).size());
        assertTrue(MapSelectionWidgetCatalog.ids().contains("fpsmatch.map_selection.room_list"));
        assertTrue(MapSelectionWidgetCatalog.ids().contains(MapSelectionWidgetCatalog.STATE_FILTER));
        assertTrue(MapSelectionWidgetCatalog.ids().contains(MapSelectionWidgetCatalog.MODE_FILTER));
        assertTrue(ShopEditorWidgetCatalog.ids().contains("fpsmatch.shop_editor.item"));
    }

    @Test
    void mapSelectionFitsBothDesktopAndCompactLayouts() {
        MapSelectionLayoutModel desktop = MapSelectionLayoutModel.responsive(960, 540);
        MapSelectionLayoutModel mid = MapSelectionLayoutModel.responsive(450, 270);
        MapSelectionLayoutModel compact = MapSelectionLayoutModel.responsive(360, 280);
        assertFalse(desktop.compact());
        assertFalse(mid.compact());
        assertTrue(compact.compact());
        // Desktop shell: filters | list | detail/actions
        assertFalse(desktop.filters().intersects(desktop.roomList()));
        assertFalse(desktop.roomList().intersects(desktop.detail()));
        assertFalse(desktop.detail().intersects(desktop.actions()));
        assertFalse(desktop.browserActions().intersects(desktop.filters()));
        assertFalse(desktop.browserActions().intersects(desktop.roomList()));
        assertEquals(desktop.filters().width(), desktop.browserActions().width());
        assertEquals(540, desktop.roomList().y() + desktop.roomList().height());
        assertEquals(desktop.filters().x(), desktop.actions().x());
        assertEquals(desktop.filters().width(), desktop.actions().width());
        assertTrue(desktop.filters().x() < desktop.roomList().x());
        assertTrue(desktop.roomList().x() < desktop.detail().x());
        // Mid desktop still three-column with a usable list rail.
        assertTrue(mid.filters().x() < mid.roomList().x());
        assertTrue(mid.roomList().x() < mid.detail().x());
        assertTrue(mid.roomList().width() >= 140);
        assertFalse(mid.filters().intersects(mid.roomList()));
        assertFalse(mid.detail().intersects(mid.actions()));
        assertFalse(mid.browserActions().intersects(mid.filters()));
        assertFalse(mid.browserActions().intersects(mid.roomList()));
        assertEquals(mid.filters().width(), mid.browserActions().width());
        assertEquals(270, mid.roomList().y() + mid.roomList().height());
        // Compact keeps a left control rail and gives the room list the full-height right column.
        assertTrue(compact.filters().x() < compact.roomList().x());
        assertTrue(compact.roomList().x() < compact.detail().x());
        assertFalse(compact.filters().intersects(compact.roomList()));
        assertFalse(compact.detail().intersects(compact.actions()));
        assertFalse(compact.browserActions().intersects(compact.filters()));
        assertFalse(compact.browserActions().intersects(compact.roomList()));
        assertEquals(compact.filters().x(), compact.actions().x());
        assertEquals(compact.filters().width(), compact.actions().width());
        assertEquals(144, compact.filters().width());
        assertEquals(360, compact.filters().width() + compact.roomList().width());
        assertTrue(compact.roomList().width() > compact.filters().width());
        assertEquals(0, compact.detail().width());
        assertEquals(0, compact.detail().height());
        assertTrue(compact.filters().y() < compact.actions().y());
        assertEquals(compact.filters().y(), compact.roomList().y());
        assertEquals(280, compact.roomList().y() + compact.roomList().height());
        assertEquals(compact.filters().width(), compact.browserActions().width());
        assertTrue(compact.browserActions().height() >= 36);
        assertLayoutWithin(compact, 360, 280);
        MapSelectionLayoutModel small = MapSelectionLayoutModel.responsive(300, 180);
        assertLayoutWithin(small, 300, 180);
        assertEquals(0, small.detail().width());
        assertEquals(0, small.detail().height());
        assertEquals(300, small.filters().width() + small.roomList().width());
        assertTrue(small.roomList().width() > 0);
        assertTrue(small.actions().height() >= 30);
        assertFalse(small.browserActions().intersects(small.filters()));
        assertFalse(small.browserActions().intersects(small.roomList()));
    }

    @Test
    void shopEditorFitsBothDesktopAndCompactLayouts() {
        ShopEditorLayoutModel desktop = ShopEditorLayoutModel.responsive(960, 540);
        ShopEditorLayoutModel compact = ShopEditorLayoutModel.responsive(480, 360);
        assertFalse(desktop.compact());
        assertTrue(compact.compact());
        assertFalse(desktop.categories().intersects(desktop.slots()));
        assertFalse(desktop.slots().intersects(desktop.properties()));
        assertEquals(960, desktop.actions().width());
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> MapSelectionLayoutModel.responsive(0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> ShopEditorLayoutModel.responsive(100, -1));
    }

    @Test
    void mapSettingsKeepsSidebarAndEditorSideBySideAtSmallSizes() {
        for (int[] size : new int[][]{{854, 480}, {427, 240}, {300, 180}, {240, 160}}) {
            MapSettingsLayoutModel layout = MapSettingsLayoutModel.responsive(size[0], size[1]);
            assertFalse(layout.sidebar().intersects(layout.content()));
            assertTrue(layout.sidebar().x() < layout.content().x());
            assertSettingsRectWithin(layout.sidebar(), size[0], size[1]);
            assertSettingsRectWithin(layout.content(), size[0], size[1]);
        }
    }

    @Test
    void pauseEntryStaysBelowMenuAndInsideSmallViewport() {
        PauseMenuLayoutModel.Placement normal = PauseMenuLayoutModel.belowMenu(427, 240, 40, 212);
        assertEquals(216, normal.y());
        assertEquals(0, normal.menuShiftUp());
        assertEquals(204, normal.width());

        PauseMenuLayoutModel.Placement compact = PauseMenuLayoutModel.belowMenu(320, 180, 40, 174);
        assertEquals(22, compact.menuShiftUp());
        assertTrue(compact.y() >= 4);
        assertTrue(compact.y() + compact.height() <= 176);
        assertTrue(compact.y() >= 174 - compact.menuShiftUp() + 4);

        PauseMenuLayoutModel.Placement narrow = PauseMenuLayoutModel.belowMenu(120, 160, 20, 100);
        assertEquals(104, narrow.width());
        assertEquals(8, narrow.x());
    }

    private static void assertLayoutWithin(MapSelectionLayoutModel layout, int width, int height) {
        assertRectWithin(layout.header(), width, height);
        assertRectWithin(layout.filters(), width, height);
        assertRectWithin(layout.roomList(), width, height);
        assertRectWithin(layout.detail(), width, height);
        assertRectWithin(layout.actions(), width, height);
        assertRectWithin(layout.browserActions(), width, height);
    }

    private static void assertRectWithin(MapSelectionLayoutModel.Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0);
        assertTrue(rect.y() >= 0);
        assertTrue(rect.x() + rect.width() <= width);
        assertTrue(rect.y() + rect.height() <= height);
    }

    private static void assertSettingsRectWithin(MapSettingsLayoutModel.Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0);
        assertTrue(rect.y() >= 0);
        assertTrue(rect.x() + rect.width() <= width);
        assertTrue(rect.y() + rect.height() <= height);
    }
}
