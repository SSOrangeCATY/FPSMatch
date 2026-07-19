package com.phasetranscrystal.fpsmatch.common.client.spec;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpectateStateTest {
    @Test
    void keepsLegacyModeOrdinalsAndRecognizesRestrictedModes() {
        assertEquals(0, SpectateMode.ATTACH.ordinal());
        assertEquals(1, SpectateMode.FREE.ordinal());
        assertTrue(SpectateMode.TEAMMATE.isRestricted());
        assertTrue(SpectateMode.C4_ORBIT.isRestricted());
        assertTrue(SpectateMode.DEATH_SPOT.isRestricted());
        assertFalse(SpectateMode.FREE.isRestricted());
    }

    @Test
    void targetUpdateReplacesModeAndTargetAtomically() {
        SpectateTarget target = new SpectateTarget(
                SpectateMode.C4_ORBIT, 42, new Vec3(1.0, 2.0, 3.0), 90.0f, -10.0f, 4.0f);
        SpectateState.setTarget(target);
        assertEquals(SpectateMode.C4_ORBIT, SpectateState.get());
        assertEquals(target, SpectateState.getTarget());
        assertTrue(SpectateState.isRestricted());
    }
}
