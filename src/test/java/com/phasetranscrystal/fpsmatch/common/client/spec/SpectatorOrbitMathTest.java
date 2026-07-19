package com.phasetranscrystal.fpsmatch.common.client.spec;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectatorOrbitMathTest {
    @Test
    void clampsOrbitPitchToConfiguredLimits() {
        assertEquals(-75.0f, SpectatorCameraMath.clampPitch(-120.0f));
        assertEquals(75.0f, SpectatorCameraMath.clampPitch(120.0f));
        assertEquals(20.0f, SpectatorCameraMath.clampPitch(20.0f));
    }

    @Test
    void computesStablePositionAtFixedRadius() {
        Vec3 center = new Vec3(10.0, 5.0, -2.0);
        Vec3 position = SpectatorCameraMath.orbitPosition(center, 0.0f, 0.0f, 4.0f);
        assertEquals(4.0, position.distanceTo(center), 1.0E-6);
        assertEquals(10.0, position.x, 1.0E-6);
        assertEquals(5.0, position.y, 1.0E-6);
        assertEquals(-6.0, position.z, 1.0E-6);
    }
}
