package com.phasetranscrystal.fpsmatch.core.health;

import com.phasetranscrystal.fpsmatch.core.matchinit.HealthSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthRuntimeTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void damageAccumulatesUntilPlayerIsDepleted() {
        HealthRuntime runtime = new HealthRuntime();
        runtime.addPlayer(PLAYER, new HealthSnapshot(150, List.of(50, 50, 50)));

        assertEquals(150, runtime.currentHealth(PLAYER));
        assertEquals(HealthChangeResult.damaged(PLAYER, 60, 90, false), runtime.damage(PLAYER, 60));
        assertEquals(90, runtime.currentHealth(PLAYER));

        HealthChangeResult result = runtime.damage(PLAYER, 100);

        assertEquals(HealthChangeResult.damaged(PLAYER, 90, 0, true), result);
        assertEquals(0, runtime.currentHealth(PLAYER));
        assertTrue(runtime.isDepleted(PLAYER));
    }

    @Test
    void healingCannotExceedMaximumHealth() {
        HealthRuntime runtime = new HealthRuntime();
        runtime.addPlayer(PLAYER, new HealthSnapshot(150, List.of(50, 50, 50)));

        runtime.damage(PLAYER, 70);

        assertEquals(HealthChangeResult.healed(PLAYER, 70, 150), runtime.heal(PLAYER, 200));
        assertEquals(150, runtime.currentHealth(PLAYER));
    }

    @Test
    void initialCurrentHealthComesFromHealthChunks() {
        HealthRuntime runtime = new HealthRuntime();
        runtime.addPlayer(PLAYER, new HealthSnapshot(150, List.of(50, 25)));

        assertEquals(75, runtime.currentHealth(PLAYER));
        assertEquals(150, runtime.maxHealth(PLAYER));
    }

    @Test
    void currentHealthCanBeRaisedToReviveFloorWithoutExceedingMaximum() {
        HealthRuntime runtime = new HealthRuntime();
        runtime.addPlayer(PLAYER, new HealthSnapshot(150, List.of(50, 50, 50)));

        runtime.damage(PLAYER, 150);

        assertEquals(HealthChangeResult.healed(PLAYER, 50, 50), runtime.setCurrentHealth(PLAYER, 50));
        assertEquals(HealthChangeResult.healed(PLAYER, 100, 150), runtime.setCurrentHealth(PLAYER, 200));
        assertEquals(150, runtime.currentHealth(PLAYER));
    }
}
