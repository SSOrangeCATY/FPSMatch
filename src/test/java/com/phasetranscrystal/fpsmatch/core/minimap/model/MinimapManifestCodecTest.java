package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidationCode;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapManifestCodecTest {
    private static final Sha256 SOURCE_HASH = Sha256.parse("1".repeat(64));
    private static final Sha256 ENTRY_HASH = Sha256.parse("a".repeat(64));

    @Test
    void sourceManifestRoundTripsOnlySourceAuthorityFields() {
        SourceManifest manifest = sourceManifest(List.of(
                new SourceEntryDescriptor(
                        ContainerPath.parse("document.json"),
                        321,
                        MediaType.APPLICATION_JSON,
                        ENTRY_HASH
                )
        ));
        JsonElement encoded = MinimapModelCodecs.SOURCE_MANIFEST
                .encodeStart(JsonOps.INSTANCE, manifest).result().orElseThrow();
        JsonObject root = encoded.getAsJsonObject();

        assertEquals(Set.of(
                "formatVersion", "documentId", "binding", "revision", "dimension",
                "provenance", "tileEdge", "entries"
        ), root.keySet());
        assertEquals("7", root.get("revision").getAsString());
        assertTrue(root.get("revision").isJsonPrimitive());
        assertEquals("321", root.getAsJsonArray("entries").get(0).getAsJsonObject()
                .get("byteLength").getAsString());
        assertFalse(root.has("worldBounds"));
        assertFalse(root.has("floors"));

        assertEquals(manifest, MinimapModelCodecs.SOURCE_MANIFEST
                .parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    void runtimeManifestContainsCompiledMetadataButNoEditableSourceFields() {
        AffineTransform2D transform = MinimapDefinitionCodecTest.approvedCalibration().fit().transform();
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                7,
                SOURCE_HASH,
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), new MinimapFormatVersion(1, 0)),
                new CanvasBounds(512, 512),
                DefaultViewMode.FOLLOW_PLAYER,
                List.of(new RuntimeFloor(
                        new MinimapFloor("ground", -10, 20, 0, 0.5, 1),
                        DisplayLabel.literal("Ground"),
                        Optional.of(new CanvasRect(0, 0, 512, 512)),
                        transform,
                        3
                )),
                256,
                List.of(new RuntimeEntryDescriptor(
                        ContainerPath.parse("floors/ground/tiles/0/0_0.png"),
                        1234,
                        ENTRY_HASH
                ))
        );
        JsonObject root = MinimapModelCodecs.RUNTIME_MANIFEST
                .encodeStart(JsonOps.INSTANCE, manifest).result().orElseThrow().getAsJsonObject();

        assertEquals(Set.of(
                "formatVersion", "documentId", "binding", "publishRevision", "sourceHash",
                "compilerProfile", "canvas", "defaultViewMode", "floors", "tileEdge", "entries"
        ), root.keySet());
        assertEquals("7", root.get("publishRevision").getAsString());
        assertEquals("follow_player", root.get("defaultViewMode").getAsString());
        assertFalse(root.has("provenance"));
        assertFalse(root.has("worldBounds"));
        assertFalse(root.has("layerOrder"));
        JsonObject floor = root.getAsJsonArray("floors").get(0).getAsJsonObject();
        assertTrue(floor.has("worldToCanvas"));
        assertFalse(floor.has("controlPoints"));
        assertFalse(floor.has("northVector"));

        assertEquals(manifest, MinimapModelCodecs.RUNTIME_MANIFEST
                .parse(JsonOps.INSTANCE, root).result().orElseThrow());
    }

    @Test
    void hashesPathsAndManifestCountersRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> Sha256.parse("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> Sha256.parse("a".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> ContainerPath.parse("../document.json"));
        assertThrows(IllegalArgumentException.class, () -> ContainerPath.parse("/document.json"));
        assertThrows(IllegalArgumentException.class, () -> ContainerPath.parse("floors\\ground.png"));
        assertThrows(IllegalArgumentException.class, () -> sourceManifest(-1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeEntryDescriptor(
                ContainerPath.parse("thumbnail.png"), -1, ENTRY_HASH
        ));
    }

    @Test
    void manifestDescriptorCountReservesOneZipEntryForTheManifestItself() {
        SourceEntryDescriptor sourceEntry = new SourceEntryDescriptor(
                ContainerPath.parse("document.json"), 1, MediaType.APPLICATION_JSON, ENTRY_HASH
        );
        RuntimeEntryDescriptor runtimeEntry = new RuntimeEntryDescriptor(
                ContainerPath.parse("regions-runtime.json"), 1, ENTRY_HASH
        );

        sourceManifest(Collections.nCopies(MinimapHardLimits.MAX_ZIP_ENTRIES - 1, sourceEntry));
        runtimeManifest(Collections.nCopies(MinimapHardLimits.MAX_ZIP_ENTRIES - 1, runtimeEntry));

        assertThrows(IllegalArgumentException.class, () -> sourceManifest(
                Collections.nCopies(MinimapHardLimits.MAX_ZIP_ENTRIES, sourceEntry)
        ));
        assertThrows(IllegalArgumentException.class, () -> runtimeManifest(
                Collections.nCopies(MinimapHardLimits.MAX_ZIP_ENTRIES, runtimeEntry)
        ));
    }

    @Test
    void validatorRejectsManifestSelfEntriesAndDuplicatePaths() {
        SourceEntryDescriptor self = new SourceEntryDescriptor(
                ContainerPath.parse("manifest.json"), 10, MediaType.APPLICATION_JSON, ENTRY_HASH
        );
        SourceEntryDescriptor duplicate = new SourceEntryDescriptor(
                ContainerPath.parse("document.json"), 10, MediaType.APPLICATION_JSON, ENTRY_HASH
        );
        SourceManifest source = sourceManifest(List.of(self, duplicate, duplicate));
        assertEquals(Set.of(
                        MinimapValidationCode.SELF_MANIFEST_ENTRY,
                        MinimapValidationCode.DUPLICATE_ENTRY_PATH
                ),
                MinimapValidator.validate(source).stream().map(issue -> issue.code()).collect(Collectors.toSet()));

        RuntimeManifest runtime = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                SOURCE_HASH,
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), MinimapFormatContract.CURRENT),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(),
                256,
                List.of(new RuntimeEntryDescriptor(
                        ContainerPath.parse("runtime-manifest.json"), 10, ENTRY_HASH
                ))
        );
        assertEquals(Set.of(MinimapValidationCode.SELF_MANIFEST_ENTRY),
                MinimapValidator.validate(runtime).stream().map(issue -> issue.code()).collect(Collectors.toSet()));
    }

    private static SourceManifest sourceManifest(List<SourceEntryDescriptor> entries) {
        return sourceManifest(7, entries);
    }

    private static SourceManifest sourceManifest(long revision, List<SourceEntryDescriptor> entries) {
        return new SourceManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                revision,
                NamespacedId.parse("minecraft:overworld"),
                Optional.of(new Provenance(
                        NamespacedId.parse("legacy:dust2"),
                        new MapKey("cs", "old_dust2"),
                        NamespacedId.parse("minecraft:overworld"),
                        3,
                        SOURCE_HASH
                )),
                256,
                entries
        );
    }

    private static RuntimeManifest runtimeManifest(List<RuntimeEntryDescriptor> entries) {
        return new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                SOURCE_HASH,
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), MinimapFormatContract.CURRENT),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(),
                256,
                entries
        );
    }
}
