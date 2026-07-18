package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentPointerTest {
    @Test
    void pointerRoundTripsCanonicalBytesAndRejectsNonCanonicalInput() {
        CurrentPointer pointer = new CurrentPointer(
                5,
                7,
                Sha256.parse("a".repeat(64))
        );
        byte[] bytes = pointer.canonicalBytes();
        assertEquals("{\"descriptorChecksum\":\"" + "a".repeat(64)
                + "\",\"expectedBaseRevision\":\"5\",\"revision\":\"7\"}",
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        CurrentPointer decoded = CurrentPointer.read(bytes);
        assertEquals(pointer, decoded);
        assertArrayEquals(bytes, decoded.canonicalBytes());

        assertThrows(ContainerStorageException.class,
                () -> CurrentPointer.read(" { }".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(ContainerStorageException.class,
                () -> CurrentPointer.read(("{\"descriptorChecksum\":\"" + "a".repeat(64)
                        + "\",\"expectedBaseRevision\":\"5\",\"revision\":-1}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(ContainerStorageException.class,
                () -> CurrentPointer.read(("{\"descriptorChecksum\":\"" + "a".repeat(64)
                        + "\",\"revision\":\"7\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
