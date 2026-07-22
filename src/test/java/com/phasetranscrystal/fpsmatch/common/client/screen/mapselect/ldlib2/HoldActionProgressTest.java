package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldActionProgressTest {
    @Test
    void incompleteHoldReturnsQuicklyToZero() {
        HoldActionProgress progress = new HoldActionProgress(900, 150, 220);

        progress.press(1_000);
        assertEquals(0.5f, progress.progress(1_450), 0.001f);
        progress.release(1_450);
        assertEquals(0.25f, progress.progress(1_525), 0.001f);
        assertFalse(progress.update(1_600));
        assertEquals(0f, progress.progress(1_600), 0.001f);
    }

    @Test
    void completionFiresOnceThenFades() {
        HoldActionProgress progress = new HoldActionProgress(900, 150, 220);

        progress.press(2_000);
        assertTrue(progress.update(2_900));
        assertFalse(progress.update(2_901));
        assertEquals(1f, progress.progress(3_010), 0.001f);
        assertEquals(0.5f, progress.opacity(3_010), 0.001f);
        progress.update(3_120);
        assertEquals(0f, progress.progress(3_120), 0.001f);
    }
}
