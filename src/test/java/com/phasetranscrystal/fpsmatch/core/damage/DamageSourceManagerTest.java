package com.phasetranscrystal.fpsmatch.core.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageSourceManagerTest {
    @AfterEach
    void tearDown() {
        DamageSourceManager.clearCustomRules();
    }

    @Test
    void customIdRuleOverridesFallbackCategory() {
        String sourceId = "test:molotov_fire";

        assertEquals(DamageSourceCategory.FALLBACK, DamageSourceManager.classify(sourceId));

        DamageSourceManager.registerId(sourceId, DamageSourceCategory.INCENDIARY);

        assertEquals(DamageSourceCategory.INCENDIARY, DamageSourceManager.classify(sourceId));
    }
}
