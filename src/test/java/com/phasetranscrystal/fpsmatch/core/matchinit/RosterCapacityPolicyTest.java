package com.phasetranscrystal.fpsmatch.core.matchinit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RosterCapacityPolicyTest {
    @Test
    void acceptsExistingReservationEvenWhenFiniteTeamIsFull() {
        assertTrue(RosterCapacityPolicy.canAccept(2, 2, true));
    }

    @Test
    void rejectsNewPlayerWhenFiniteTeamIsFull() {
        assertFalse(RosterCapacityPolicy.canAccept(2, 2, false));
    }

    @Test
    void acceptsNewPlayerWhenTeamIsUnlimited() {
        assertTrue(RosterCapacityPolicy.canAccept(-1, 20, false));
    }

    @Test
    void acceptsNewPlayerWhenFiniteTeamHasSpace() {
        assertTrue(RosterCapacityPolicy.canAccept(2, 1, false));
    }
}
