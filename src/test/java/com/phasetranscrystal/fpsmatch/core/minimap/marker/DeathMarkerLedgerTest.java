package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathMarkerLedgerTest {
    @Test
    void recordsPurgesAndExpires() {
        DeathMarkerLedger ledger = new DeathMarkerLedger();
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        DeathMarkerEvent event = new DeathMarkerEvent(
                id, "ct", 1, 2, 3, 10f, 5, 15, Optional.of("ground")
        );
        ledger.record(event);
        assertEquals(1, ledger.activeAt(15).size());
        assertTrue(ledger.activeAt(16).isEmpty());
        ledger.purgeExpired(16);
        assertEquals(0, ledger.size());
    }
}