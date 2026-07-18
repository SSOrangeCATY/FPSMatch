package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltinRuntimeBindingCodecTest {
    private static final BuiltinRuntimeBinding BINDING = new BuiltinRuntimeBinding(
            new MapKey("cs", "dust2"),
            NamespacedId.parse("minecraft:overworld"),
            NamespacedId.parse("blockoffensive:dust2"),
            Sha256.parse("a".repeat(64))
    );

    @Test
    void readsAndWritesStableCanonicalBytes() {
        byte[] canonical = ("{\"binding\":{\"gameType\":\"cs\",\"mapName\":\"dust2\"},"
                        + "\"dimension\":\"minecraft:overworld\","
                        + "\"documentId\":\"blockoffensive:dust2\","
                        + "\"runtimeHash\":\"" + "a".repeat(64) + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        assertEquals(BINDING, BuiltinRuntimeBindingCodec.read(canonical));
        assertArrayEquals(canonical, BuiltinRuntimeBindingCodec.write(BINDING));
    }

    @Test
    void rejectsUnknownDuplicateMissingAndOversizedDeclarations() {
        assertThrows(ContainerValidationException.class, () -> read(("""
                {
                  "runtimeHash": "%s",
                  "documentId": "blockoffensive:dust2",
                  "dimension": "minecraft:overworld",
                  "binding": {"mapName": "dust2", "gameType": "cs"}
                }
                """).formatted("a".repeat(64))));
        assertThrows(ContainerValidationException.class, () -> read("""
                {"binding":{"gameType":"cs","mapName":"dust2"},
                "dimension":"minecraft:overworld","documentId":"blockoffensive:dust2",
                "runtimeHash":"%s","extra":true}
                """.formatted("a".repeat(64))));
        assertThrows(ContainerValidationException.class, () -> read("""
                {"binding":{"gameType":"cs","gameType":"cs","mapName":"dust2"},
                "dimension":"minecraft:overworld","documentId":"blockoffensive:dust2",
                "runtimeHash":"%s"}
                """.formatted("a".repeat(64))));
        assertThrows(ContainerValidationException.class, () -> read("""
                {"binding":{"gameType":"cs","mapName":"dust2"},
                "dimension":"minecraft:overworld","runtimeHash":"%s"}
                """.formatted("a".repeat(64))));
        assertThrows(ContainerValidationException.class, () ->
                BuiltinRuntimeBindingCodec.read(new byte[64 * 1024 + 1]));
    }

    private static BuiltinRuntimeBinding read(String json) {
        return BuiltinRuntimeBindingCodec.read(json.getBytes(StandardCharsets.UTF_8));
    }
}
