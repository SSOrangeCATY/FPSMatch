package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSettingsLayoutModelTest {
    @Test
    void compactToolbarKeepsSearchAndFilterSeparateAndInsideContent() {
        MapSettingsLayoutModel.ToolbarLayout toolbar = MapSettingsLayoutModel.toolbar(198, 164, 7);

        assertFalse(toolbar.search().intersects(toolbar.categoryFilter()));
        assertTrue(toolbar.search().x() >= 0);
        assertTrue(toolbar.categoryFilter().x() + toolbar.categoryFilter().width() <= 198);
        assertTrue(toolbar.categoryPopup().x() >= 0);
        assertTrue(toolbar.categoryPopup().x() + toolbar.categoryPopup().width() <= 198);
        assertTrue(toolbar.categoryPopup().y() + toolbar.categoryPopup().height() <= 164);
        assertEquals(toolbar.categoryFilter().x(), toolbar.categoryPopup().x());
        assertEquals(toolbar.categoryFilter().width(), toolbar.categoryPopup().width());
    }
}
