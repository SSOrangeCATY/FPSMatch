package com.phasetranscrystal.fpsmatch.core.connection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerConnectionRuntimeTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void disconnectStartsGraceTimerAndEmitsFirstDisconnectOnlyOnce() {
        PlayerConnectionRuntime runtime = new PlayerConnectionRuntime(PlayerConnectionPolicy.disconnectGrace(3));

        assertTrue(runtime.disconnect(PLAYER));
        assertFalse(runtime.disconnect(PLAYER));

        assertEquals(3, runtime.ticksRemaining(PLAYER));
        assertEquals(List.of(new PlayerConnectionEvent(PLAYER, PlayerConnectionEventType.DISCONNECTED, 3)),
                runtime.drainEvents());
    }

    @Test
    void reconnectClearsDisconnectedRecordAndEmitsReconnectEvent() {
        PlayerConnectionRuntime runtime = new PlayerConnectionRuntime(PlayerConnectionPolicy.disconnectGrace(3));
        runtime.disconnect(PLAYER);
        runtime.drainEvents();

        assertTrue(runtime.reconnect(PLAYER));

        assertFalse(runtime.isDisconnected(PLAYER));
        assertEquals(List.of(new PlayerConnectionEvent(PLAYER, PlayerConnectionEventType.RECONNECTED, 0)),
                runtime.drainEvents());
    }

    @Test
    void graceExpiryRemovesDisconnectedRecordAndEmitsExpiryOnce() {
        PlayerConnectionRuntime runtime = new PlayerConnectionRuntime(PlayerConnectionPolicy.disconnectGrace(2));
        runtime.disconnect(PLAYER);
        runtime.drainEvents();

        runtime.tick(1);
        assertTrue(runtime.isDisconnected(PLAYER));
        assertEquals(1, runtime.ticksRemaining(PLAYER));
        assertTrue(runtime.drainEvents().isEmpty());

        runtime.tick(1);
        runtime.tick(5);

        assertFalse(runtime.isDisconnected(PLAYER));
        assertEquals(List.of(new PlayerConnectionEvent(PLAYER, PlayerConnectionEventType.GRACE_EXPIRED, 0)),
                runtime.drainEvents());
    }

    @Test
    void clearRemovesDisconnectedRecordWithoutEmittingEvent() {
        PlayerConnectionRuntime runtime = new PlayerConnectionRuntime(PlayerConnectionPolicy.disconnectGrace(3));
        runtime.disconnect(PLAYER);
        runtime.drainEvents();

        assertTrue(runtime.clear(PLAYER));
        runtime.tick(3);

        assertFalse(runtime.isDisconnected(PLAYER));
        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void restoresDisconnectedRecordsAndContinuesTimers() {
        PlayerConnectionRuntime runtime = PlayerConnectionRuntime.restore(
                PlayerConnectionPolicy.disconnectGrace(10),
                List.of(new PlayerConnectionSnapshot(PLAYER, 2))
        );

        runtime.tick(2);

        assertFalse(runtime.isDisconnected(PLAYER));
        assertEquals(List.of(new PlayerConnectionEvent(PLAYER, PlayerConnectionEventType.GRACE_EXPIRED, 0)),
                runtime.drainEvents());
    }

    @Test
    void rejectsNegativeTickAmount() {
        PlayerConnectionRuntime runtime = new PlayerConnectionRuntime(PlayerConnectionPolicy.disconnectGrace(3));

        assertThrows(IllegalArgumentException.class, () -> runtime.tick(-1));
    }
}
