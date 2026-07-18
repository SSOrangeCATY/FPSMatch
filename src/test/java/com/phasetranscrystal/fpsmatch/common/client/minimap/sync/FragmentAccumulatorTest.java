package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FragmentAccumulatorTest {
    @Test
    void acceptsOutOfOrderFragmentsAndRejectsConflicts() {
        FragmentAccumulator acc = new FragmentAccumulator(4, 1024 * 1024, 30_000L);
        byte[] full = new byte[600];
        for (int i = 0; i < full.length; i++) {
            full[i] = (byte) i;
        }
        Sha256 hash = Sha256Digest.of(full);
        TransferKey key = new TransferKey("entry.png", hash, full.length, 2);

        Optional<byte[]> none = acc.accept(key, 1, slice(full, 300, 300), 0L);
        assertTrue(none.isEmpty());
        // conflict on already-received index before completion
        assertThrows(FragmentAssemblyException.class, () ->
                acc.accept(key, 1, new byte[] {9, 9, 9}, 1L));
        Optional<byte[]> done = acc.accept(key, 0, slice(full, 0, 300), 2L);
        assertTrue(done.isPresent());
        assertArrayEquals(full, done.get());
    }

    @Test
    void duplicateIdenticalIsIdempotentAndTimeoutDropsStale() {
        FragmentAccumulator acc = new FragmentAccumulator(4, 1024 * 1024, 10L);
        byte[] full = "hello-world-fragment-payload".getBytes(StandardCharsets.UTF_8);
        TransferKey key = new TransferKey("a.bin", Sha256Digest.of(full), full.length, 2);
        assertTrue(acc.accept(key, 0, slice(full, 0, 10), 0L).isEmpty());
        assertTrue(acc.accept(key, 0, slice(full, 0, 10), 1L).isEmpty()); // identical duplicate
        assertEquals(1, acc.discardExpired(20L)); // partial transfer times out
        // after timeout, a fresh single-fragment transfer can complete
        TransferKey single = new TransferKey("b.bin", Sha256Digest.of(full), full.length, 1);
        assertTrue(acc.accept(single, 0, full, 21L).isPresent());
    }

    @Test
    void enforcesPreallocationAndTotalBudgets() {
        FragmentAccumulator acc = new FragmentAccumulator(1, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES, 30_000L);
        byte[] payload = new byte[100];
        TransferKey first = new TransferKey("one", Sha256Digest.of(payload), payload.length * 2, 2);
        // hold an in-flight transfer so budget remains occupied
        assertTrue(acc.accept(first, 0, payload, 0L).isEmpty());
        TransferKey second = new TransferKey("two", Sha256Digest.of(payload), payload.length, 1);
        assertThrows(FragmentAssemblyException.class, () -> acc.accept(second, 0, payload, 1L));
        assertThrows(FragmentAssemblyException.class, () ->
                acc.accept(first, 0, new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1], 2L));
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(source, offset, out, 0, length);
        return out;
    }
}