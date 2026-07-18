package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerResetAccumulatorTest {
    @Test
    void assemblesOutOfOrderPagesAndIgnoresIdenticalDuplicates() {
        MarkerResetAccumulator accumulator = new MarkerResetAccumulator(
                4, 8, 64, 30_000L
        );
        UUID resetId = uuid(10);
        MarkerWireMessage.Reset second = reset(
                resetId, 1, 2, List.of(marker("fpsmatch:b", 2.0))
        );
        MarkerWireMessage.Reset first = reset(
                resetId, 0, 2, List.of(marker("fpsmatch:a", 1.0))
        );

        assertTrue(accumulator.accept(second, 0L).isEmpty());
        assertTrue(accumulator.accept(second, 1L).isEmpty());
        List<WireMarker.Marker> assembled = accumulator.accept(first, 2L).orElseThrow();

        assertEquals(List.of(
                NamespacedId.parse("fpsmatch:a"),
                NamespacedId.parse("fpsmatch:b")
        ), assembled.stream().map(WireMarker.Marker::markerId).toList());
        assertTrue(accumulator.accept(first, 3L).isEmpty());
    }

    @Test
    void conflictingDuplicatePageFailsClosedAndDiscardsTheReset() {
        MarkerResetAccumulator accumulator = new MarkerResetAccumulator(
                4, 8, 64, 30_000L
        );
        UUID resetId = uuid(11);

        assertTrue(accumulator.accept(reset(
                resetId, 0, 2, List.of(marker("fpsmatch:a", 1.0))
        ), 0L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(reset(
                resetId, 0, 2, List.of(marker("fpsmatch:a", 2.0))
        ), 1L));

        assertTrue(accumulator.accept(reset(
                resetId, 1, 2, List.of(marker("fpsmatch:b", 2.0))
        ), 2L).isEmpty());
    }

    @Test
    void expiredPartialResetCannotCombineWithLatePages() {
        MarkerResetAccumulator accumulator = new MarkerResetAccumulator(
                4, 8, 64, 10L
        );
        UUID resetId = uuid(12);

        assertTrue(accumulator.accept(reset(
                resetId, 0, 2, List.of(marker("fpsmatch:a", 1.0))
        ), 0L).isEmpty());
        assertEquals(1, accumulator.discardExpired(11L));
        assertTrue(accumulator.accept(reset(
                resetId, 1, 2, List.of(marker("fpsmatch:b", 2.0))
        ), 12L).isEmpty());
    }

    private static MarkerWireMessage.Reset reset(
            UUID resetId,
            int pageIndex,
            int pageCount,
            List<WireMarker.Marker> markers
    ) {
        return new MarkerWireMessage.Reset(
                Optional.empty(), lease(), runtime(), uuid(20), 0L,
                resetId, pageIndex, pageCount, markers
        );
    }

    private static WireMarker.Marker marker(String id, double x) {
        return new WireMarker.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse("fpsmatch:type/player"),
                NamespacedId.parse("fpsmatch:style/default"),
                x, 0, 0, 0f, 0L, Optional.empty(), Optional.of("ground"),
                List.of()
        );
    }

    private static WireIdentity.ScopeLease lease() {
        return new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 1L, 1L);
    }

    private static WireIdentity.RuntimeIdentity runtime() {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("cs", "dust2"),
                                NamespacedId.parse("minecraft:overworld")
                        ),
                        NamespacedId.parse("fpsmatch:dust2")
                ),
                1L,
                Sha256Digest.of("runtime".getBytes(StandardCharsets.UTF_8)),
                Optional.empty()
        );
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }
}
