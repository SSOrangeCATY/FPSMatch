package com.phasetranscrystal.fpsmatch.common.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FPSMDeathPipelineEventHookTest {

    @Test
    void deathIsNotReadyUntilAfterItsCreationTick() {
        assertFalse(DeathFinalizationTiming.isReady(100L, 99L));
        assertFalse(DeathFinalizationTiming.isReady(100L, 100L));
        assertTrue(DeathFinalizationTiming.isReady(100L, 101L));
    }
}
