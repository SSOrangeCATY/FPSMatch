package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapPacketEndpointRegistryTest {

    @Test
    void replacementAndCloseAdvanceGenerationAndReleaseConnectionState() {
        MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        MinimapPacketEndpointRegistry registry =
                new MinimapPacketEndpointRegistry(reassembler);
        Object connection = new Object();

        MinimapPacketEndpointRegistry.EndpointLease first = registry.install(connection);
        MinimapPacketEndpointRegistry.EndpointGeneration firstGeneration =
                first.generation();
        assertTrue(registry.isCurrent(firstGeneration));

        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(
                new UUID(0, 1), new byte[40_000]
        );
        reassembler.accept(
                connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        );
        assertEquals(1, reassembler.inFlightFrames(connection));

        MinimapPacketEndpointRegistry.EndpointLease replacement =
                registry.install(connection);
        assertFalse(first.isOpen());
        assertFalse(registry.isCurrent(firstGeneration));
        assertTrue(registry.isCurrent(replacement.generation()));
        assertTrue(replacement.generation().value() > firstGeneration.value());
        assertEquals(0, reassembler.inFlightFrames(connection));

        first.close();
        first.close();
        assertTrue(replacement.isOpen());
        replacement.close();
        replacement.close();
        assertFalse(replacement.isOpen());
        assertEquals(0, registry.activeEndpointCount());
    }

    @Test
    void equalButDistinctConnectionTokensRemainIndependentUntilCloseAll() {
        MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        MinimapPacketEndpointRegistry registry =
                new MinimapPacketEndpointRegistry(reassembler);
        Object firstConnection = new String("connection");
        Object secondConnection = new String("connection");

        MinimapPacketEndpointRegistry.EndpointLease first =
                registry.install(firstConnection);
        MinimapPacketEndpointRegistry.EndpointLease second =
                registry.install(secondConnection);

        assertTrue(first.isOpen());
        assertTrue(second.isOpen());
        assertEquals(2, registry.activeEndpointCount());
        registry.closeAll();
        assertFalse(first.isOpen());
        assertFalse(second.isOpen());
        assertEquals(0, registry.activeEndpointCount());

        MinimapPacketEndpointRegistry.EndpointLease afterRestart =
                registry.install(firstConnection);
        assertTrue(afterRestart.generation().value() > second.generation().value());
        assertTrue(afterRestart.isOpen());
    }
}
