package com.phasetranscrystal.fpsmatch.core.minimap.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapPrimitiveContractTest {
    @Test
    void validatesNamespacedIdsAndInternalSlugs() {
        assertEquals(new NamespacedId("fpsmatch", "dust2/overview"),
                NamespacedId.parse("fpsmatch:dust2/overview"));
        assertEquals("fpsmatch:dust2/overview", NamespacedId.parse("fpsmatch:dust2/overview").toString());

        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("FPSMatch:dust2"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("fpsmatch:../dust2"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("fpsmatch:dust2//overview"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("fpsmatch:dust2/"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("fpsmatch:"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("fpsmatch:a\uD800b"));
        assertThrows(IllegalArgumentException.class,
                () -> NamespacedId.parse("a".repeat(MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES + 1) + ":path"));
        assertThrows(IllegalArgumentException.class,
                () -> NamespacedId.parse("fpsmatch:" + "a".repeat(MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES + 1)));
        assertEquals(NamespacedId.parse("fpsmatch:dust2/overview"), NamespacedId.codec()
                .parse(JsonOps.INSTANCE, new JsonPrimitive("fpsmatch:dust2/overview"))
                .result().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse(null));

        assertTrue(MinimapFormatContract.isInternalSlug("ground-1"));
        assertFalse(MinimapFormatContract.isInternalSlug(""));
        assertFalse(MinimapFormatContract.isInternalSlug("Ground"));
        assertFalse(MinimapFormatContract.isInternalSlug("ground/main"));
        assertFalse(MinimapFormatContract.isInternalSlug("fpsmatch:ground"));
        assertFalse(MinimapFormatContract.isInternalSlug("."));
        assertFalse(MinimapFormatContract.isInternalSlug(".."));
        assertFalse(MinimapFormatContract.isInternalSlug("a".repeat(MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES + 1)));
    }

    @Test
    void mapKeyUsesAValidatedStructuredCodecWithoutChangingOpaqueValues() {
        String mapName = "\u6218\u672f\u5730\u56fe A";
        MapKey key = new MapKey("fpsmatch:cs", mapName);

        JsonObject expected = new JsonObject();
        expected.addProperty("gameType", "fpsmatch:cs");
        expected.addProperty("mapName", mapName);
        JsonElement encoded = MapKey.codec().encodeStart(JsonOps.INSTANCE, key).result().orElseThrow();

        assertEquals(expected, encoded);
        assertEquals(key, MapKey.codec().parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        assertTrue(MapKey.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("fpsmatch:cs:" + mapName)).result().isEmpty());
        assertTrue(MapKey.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"gameType\":\"\",\"mapName\":\"map\"}"))
                .result().isEmpty());
        assertTrue(MapKey.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"gameType\":1,\"mapName\":\"map\"}"))
                .result().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new MapKey("cs", ""));
        assertThrows(IllegalArgumentException.class, () -> new MapKey("cs", "e\u0301"));
        assertThrows(IllegalArgumentException.class, () -> new MapKey("cs", "bad\u0000name"));
        assertThrows(IllegalArgumentException.class, () -> new MapKey("cs", "bad\uD800name"));
        assertThrows(IllegalArgumentException.class, () -> new MapKey("CS Mode", "map"));
        assertThrows(IllegalArgumentException.class,
                () -> new MapKey("x".repeat(MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES + 1), "map"));
        assertThrows(IllegalArgumentException.class,
                () -> new MapKey("cs", "x".repeat(MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES + 1)));

        assertEquals(" map ", new MapKey("cs", " map ").mapName());
        assertFalse(new MapKey("cs", "map").equals(new MapKey("cs", "Map")));
        assertEquals("\u754c".repeat(85), new MapKey("cs", "\u754c".repeat(85)).mapName());
        assertThrows(IllegalArgumentException.class, () -> new MapKey("cs", "\u754c".repeat(86)));
        assertEquals("\ud83d\uddfa\ufe0f map", new MapKey("cs", "\ud83d\uddfa\ufe0f map").mapName());
    }

    @Test
    void revisionsAreNonNegativeLongsEncodedOnlyAsDecimalStrings() {
        assertEquals(7L, parseLongString("\"7\"").orElseThrow());
        assertEquals(0L, parseLongString("\"0\"").orElseThrow());
        assertEquals(Long.MAX_VALUE, parseLongString("\"9223372036854775807\"").orElseThrow());
        assertEquals(new JsonPrimitive("0"), encodeLong(0));
        assertEquals(new JsonPrimitive("7"), encodeLong(7));
        assertEquals(new JsonPrimitive(Long.toString(Long.MAX_VALUE)), encodeLong(Long.MAX_VALUE));

        List.of(
                "7",
                "\"\"",
                "\"-1\"",
                "\"+1\"",
                "\" 1\"",
                "\"1 \"",
                "\"01\"",
                "\"9223372036854775808\""
        ).forEach(json -> assertTrue(parseLongString(json).isEmpty(), json));
        assertTrue(MinimapCodecs.NON_NEGATIVE_LONG.encodeStart(JsonOps.INSTANCE, -1L).result().isEmpty());
    }

    @Test
    void formatVersionHasStrictMajorMinorEncoding() {
        assertEquals(new MinimapFormatVersion(1, 0), MinimapFormatContract.CURRENT);
        assertEquals(new JsonPrimitive("1.0"), MinimapFormatVersion.codec()
                .encodeStart(JsonOps.INSTANCE, MinimapFormatContract.CURRENT)
                .result().orElseThrow());
        assertEquals(MinimapFormatContract.CURRENT, MinimapFormatVersion.codec()
                .parse(JsonOps.INSTANCE, new JsonPrimitive("1.0"))
                .result().orElseThrow());

        List.of("01.0", "1", "1.0.0", "-1.0", "1.-1", "2147483648.0")
                .forEach(version -> assertTrue(MinimapFormatVersion.codec()
                        .parse(JsonOps.INSTANCE, new JsonPrimitive(version)).result().isEmpty(), version));
        assertTrue(MinimapFormatVersion.codec()
                .parse(JsonOps.INSTANCE, new JsonPrimitive("1.2147483648")).result().isEmpty());
        assertTrue(MinimapFormatVersion.codec()
                .parse(JsonOps.INSTANCE, new JsonPrimitive("1".repeat(1_000) + ".0")).result().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MinimapFormatVersion(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> MinimapFormatVersion.parse(null));
    }

    @Test
    void hardSecurityLimitsAreStable() {
        assertEquals(64, MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES);
        assertEquals(256, MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES);
        assertEquals(64, MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES);
        assertEquals(256, MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES);
        assertEquals(64, MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES);
        assertEquals(21, MinimapHardLimits.MAX_FORMAT_VERSION_UTF8_BYTES);
        assertEquals(512, MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES);
        assertEquals(32 * 1024, MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES);
        assertEquals(16_384, MinimapHardLimits.MAX_CANVAS_EDGE);
        assertEquals(32, MinimapHardLimits.MAX_FLOORS);
        assertEquals(256, MinimapHardLimits.MAX_SOURCE_LAYERS);
        assertEquals(8_192, MinimapHardLimits.MAX_REGIONS);
        assertEquals(262_144, MinimapHardLimits.MAX_VECTOR_VERTICES);
        assertEquals(32_768, MinimapHardLimits.MAX_ZIP_ENTRIES);
        assertEquals(4L * 1024 * 1024, MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES);
        assertEquals(4L * 1024 * 1024, MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES);
        assertEquals(64L * 1024 * 1024, MinimapHardLimits.MAX_JSON_ENTRY_BYTES);
        assertEquals(128L * 1024 * 1024, MinimapHardLimits.MAX_ZIP_ENTRY_BYTES);
        assertEquals(1_024, MinimapHardLimits.MAX_TILE_EDGE);
        assertEquals(128L * 1024 * 1024, MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES);
        assertEquals(1L * 1024 * 1024 * 1024, MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES);
        assertEquals(512L * 1024 * 1024, MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES);
        assertEquals(256 * 1024, MinimapHardLimits.MAX_WIRE_BODY_BYTES);
        assertEquals(4L * 1024 * 1024, MinimapHardLimits.MAX_DECODED_TILE_BYTES);
    }

    @Test
    void wireLimitsAreIndependentAndStable() {
        assertEquals(262_144, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES);
        assertEquals(32_768, MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES);
        assertEquals(327_680, MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        assertEquals(30_720, MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES);
        assertEquals(4, MinimapHardLimits.MAX_REASSEMBLY_FRAMES_PER_CONNECTION);
        assertEquals(1_310_720, MinimapHardLimits.MAX_REASSEMBLY_BYTES_PER_CONNECTION);
        assertEquals(Duration.ofSeconds(30), MinimapHardLimits.REASSEMBLY_TTL);
        assertEquals(1_073_741_824L, MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES);
        assertEquals(64, MinimapHardLimits.MAX_MARKER_STATE_FIELDS);
        assertEquals(4_096, MinimapHardLimits.MAX_MARKER_STATE_BYTES);
        assertEquals(3_584, MinimapHardLimits.MAX_MARKER_BYTES_VALUE);
        assertEquals(4_096, MinimapHardLimits.MAX_WIRE_PAGE_COUNT);
        assertEquals(32, MinimapHardLimits.MAX_SNAPSHOT_REQUEST_CHANNELS);
        assertEquals(32, MinimapHardLimits.MAX_SNAPSHOT_SECTIONS_PER_PAGE);
        assertEquals(16, MinimapHardLimits.MAX_SNAPSHOT_CHANNELS_PER_SECTION);
        assertEquals(134_217_728L, MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES);
        assertEquals(
                536_870_912L,
                MinimapHardLimits.MAX_SNAPSHOT_MANIFEST_DECLARED_BYTES
        );
        assertEquals(4_096, MinimapHardLimits.MAX_DIRTY_SECTION_RESULTS);
        assertEquals(128, MinimapHardLimits.MAX_REBASE_ITEMS);
        assertEquals(128, MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES);
        assertEquals(1_024, MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES);
        assertEquals(MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES,
                MinimapHardLimits.MAX_WIRE_BODY_BYTES);
    }

    @Test
    void opcodeTableAndWireVersionAreStable() {
        List<String> expected = List.of(
                "C2S_SUBSCRIBE:C2S:01",
                "C2S_UNSUBSCRIBE:C2S:02",
                "C2S_REQUEST_ENTRIES:C2S:03",
                "C2S_REQUEST_MARKER_RESET:C2S:04",
                "C2S_EDITOR_OPEN:C2S:10",
                "C2S_EDITOR_RESUME:C2S:11",
                "C2S_EDITOR_REQUEST_SOURCE_ENTRIES:C2S:12",
                "C2S_EDITOR_OPERATION:C2S:13",
                "C2S_EDITOR_UPLOAD_FRAGMENT:C2S:14",
                "C2S_EDITOR_SAVE_DRAFT:C2S:15",
                "C2S_EDITOR_REBASE:C2S:16",
                "C2S_EDITOR_REQUEST_WORLD_SNAPSHOT:C2S:17",
                "C2S_EDITOR_REQUEST_DIRTY_SECTIONS:C2S:18",
                "C2S_EDITOR_RESERVE_PUBLISH:C2S:19",
                "C2S_EDITOR_COMMIT_PUBLISH:C2S:1a",
                "C2S_EDITOR_CLOSE:C2S:1b",
                "C2S_EDITOR_QUERY_PUBLISH_STATUS:C2S:1c",
                "S2C_SCOPE_ACK:S2C:41",
                "S2C_MANIFEST:S2C:42",
                "S2C_ENTRY_FRAGMENT:S2C:43",
                "S2C_MARKER_RESET:S2C:44",
                "S2C_MARKER_DELTA:S2C:45",
                "S2C_EDITOR_SESSION:S2C:50",
                "S2C_EDITOR_SOURCE_MANIFEST:S2C:51",
                "S2C_EDITOR_SOURCE_FRAGMENT:S2C:52",
                "S2C_EDITOR_ACK:S2C:53",
                "S2C_EDITOR_REBASE_RESULT:S2C:54",
                "S2C_WORLD_SNAPSHOT_MANIFEST:S2C:55",
                "S2C_WORLD_SNAPSHOT_FRAGMENT:S2C:56",
                "S2C_DIRTY_SECTIONS:S2C:57",
                "S2C_PUBLISH_RESERVATION:S2C:58",
                "S2C_PUBLISH_RESULT:S2C:59",
                "S2C_PUBLISH_STATUS:S2C:5a",
                "S2C_ERROR:S2C:7f"
        );
        List<String> actual = Arrays.stream(MinimapOpcode.values())
                .map(opcode -> opcode.name() + ":" + opcode.direction() + ":" + String.format("%02x", opcode.code()))
                .toList();
        assertEquals(expected, actual);

        Set<Integer> uniqueCodes = new HashSet<>();
        Arrays.stream(MinimapOpcode.values()).forEach(opcode -> {
            assertTrue(uniqueCodes.add(opcode.code()), opcode.name());
            if (opcode.direction() == MinimapMessageDirection.C2S) {
                assertTrue(opcode.code() >= 0x01 && opcode.code() <= 0x3f, opcode.name());
            } else {
                assertTrue(opcode.code() >= 0x41 && opcode.code() <= 0x7f, opcode.name());
            }
        });
        assertFalse(uniqueCodes.contains(0x00));
        assertFalse(uniqueCodes.contains(0x40));
        assertFalse(uniqueCodes.contains(0x05));
        assertFalse(uniqueCodes.contains(0x3f));
        assertFalse(uniqueCodes.contains(0x46));
        assertFalse(uniqueCodes.contains(0x7e));
        assertEquals(1, MinimapProtocolContract.WIRE_MAJOR);
        assertEquals(0, MinimapProtocolContract.WIRE_MINOR);
    }

    @Test
    void errorCodeTableIsStable() {
        assertEquals(List.of(
                "MALFORMED_MESSAGE:0001",
                "UNSUPPORTED_WIRE_VERSION:0002",
                "UNKNOWN_OPCODE:0003",
                "WRONG_DIRECTION:0004",
                "UNAUTHORIZED:0010",
                "SESSION_NOT_FOUND:0011",
                "SESSION_EXPIRED:0012",
                "SCOPE_MISMATCH:0013",
                "INVALID_MAP_KEY:0020",
                "INVALID_RESOURCE_ID:0021",
                "INVALID_PATH:0022",
                "FORMAT_UNSUPPORTED:0023",
                "VALIDATION_FAILED:0024",
                "HASH_MISMATCH:0025",
                "QUOTA_EXCEEDED:0026",
                "FRAGMENT_CONFLICT:0027",
                "REVISION_CONFLICT:0030",
                "PUBLISH_TOKEN_INVALID:0031",
                "PUBLISH_TOKEN_EXPIRED:0032",
                "PUBLISH_IO_FAILED:0033",
                "PUBLISH_IO_DEGRADED:0034",
                "PUBLISH_STATUS_UNKNOWN:0035",
                "MAP_UNAVAILABLE:0040",
                "ENTRY_NOT_FOUND:0041",
                "SNAPSHOT_UNAVAILABLE:0042",
                "INTERNAL_ERROR:7fff"
        ), Arrays.stream(MinimapErrorCode.values())
                .map(error -> error.name() + ":" + String.format("%04x", error.code()))
                .toList());
        assertEquals(MinimapErrorCode.HASH_MISMATCH, MinimapErrorCode.fromCode(0x0025).orElseThrow());
        assertTrue(MinimapErrorCode.fromCode(0x7777).isEmpty());
        assertEquals(MinimapErrorCode.values().length,
                Arrays.stream(MinimapErrorCode.values()).map(MinimapErrorCode::code).distinct().count());
    }

    private Optional<Long> parseLongString(String json) {
        return MinimapCodecs.NON_NEGATIVE_LONG
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result();
    }

    private JsonElement encodeLong(long value) {
        return MinimapCodecs.NON_NEGATIVE_LONG
                .encodeStart(JsonOps.INSTANCE, value)
                .result()
                .orElseThrow();
    }
}
