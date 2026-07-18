package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Provenance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapRebindServiceTest {
    private static final ContainerPath RUNTIME_TILE = ContainerPath.parse(
            "floors/ground/tiles/0/0_0.png"
    );
    private static final byte[] UNDECODABLE_PNG = "trusted-png-sentinel"
            .getBytes(StandardCharsets.US_ASCII);

    @Test
    void committedSnapshotRebindPreservesPayloadsExtensionsAndPredecessorWithoutPngDecode()
            throws Exception {
        TrustedPair pair = trustedPairWithUndecodablePngs();
        assertThrows(ContainerValidationException.class, () -> {
            try (SourceMap ignored = SourceMapReader.read(pair.sourceBytes())) {
                // The sentinel proves any full source reader would decode and reject the PNG.
            }
        });
        assertThrows(ContainerValidationException.class, () -> {
            try (RuntimeMap ignored = RuntimeMapReader.read(pair.runtimeBytes())) {
                // The sentinel proves any full runtime reader would decode and reject the PNG.
            }
        });

        try (CommittedMapPairSnapshot original = pair.open(7)) {
            MapRebindService.ReboundMapPair rebound = MapRebindService.rebindCommitted(
                    original, 8
            );
            try (CommittedMapPairSnapshot reboundSnapshot = CommittedMapPairSnapshot.open(
                    rebound.sourceBytes(),
                    rebound.runtimeBytes(),
                    pair.binding(),
                    8,
                    rebound.sourceHash(),
                    rebound.runtimeHash(),
                    rebound.runtimeContainerHash()
            )) {
                assertNonManifestEntriesEqual(original, reboundSnapshot, true);
                assertNonManifestEntriesEqual(original, reboundSnapshot, false);
                assertEquals(original.sourceManifestExtensions(),
                        reboundSnapshot.sourceManifestExtensions());
                assertEquals(original.runtimeManifestExtensions(),
                        reboundSnapshot.runtimeManifestExtensions());

                Provenance provenance = reboundSnapshot.sourceManifest()
                        .provenance().orElseThrow();
                assertEquals(original.sourceManifest().documentId(), provenance.originDocumentId());
                assertEquals(original.sourceManifest().binding(), provenance.originBinding());
                assertEquals(original.sourceManifest().dimension(), provenance.originDimension());
                assertEquals(7, provenance.originRevision());
                assertEquals(pair.sourceHash(), provenance.originSourceHash());
                assertEquals(8, reboundSnapshot.sourceManifest().revision());
                assertEquals(8, reboundSnapshot.runtimeManifest().publishRevision());
                assertEquals(rebound.sourceHash(), reboundSnapshot.runtimeManifest().sourceHash());
            }
        }
    }

    @Test
    void streamingRebindWritesExactContainersWithoutClosingCallerOutputs()
            throws Exception {
        TrustedPair pair = trustedPairWithUndecodablePngs();
        TrackingOutputStream sourceOutput = new TrackingOutputStream();
        TrackingOutputStream runtimeOutput = new TrackingOutputStream();

        try (CommittedMapPairSnapshot snapshot = pair.open(7)) {
            MapRebindService.ContainerOutputDigest source =
                    MapRebindService.writeReboundSource(
                            snapshot, 8, sourceOutput
                    );
            MapRebindService.ContainerOutputDigest runtime =
                    MapRebindService.writeReboundRuntime(
                            snapshot, 8, source.containerHash(), runtimeOutput
                    );
            MapRebindService.ReboundMapPair inMemory =
                    MapRebindService.rebindCommitted(snapshot, 8);

            assertArrayEquals(inMemory.sourceBytes(), sourceOutput.toByteArray());
            assertArrayEquals(inMemory.runtimeBytes(), runtimeOutput.toByteArray());
            assertEquals(sourceOutput.size(), source.containerLength());
            assertEquals(runtimeOutput.size(), runtime.containerLength());
            assertEquals(inMemory.sourceHash(), source.containerHash());
            assertEquals(inMemory.runtimeContainerHash(), runtime.containerHash());
            assertEquals(inMemory.runtimeHash(), runtime.manifestHash());
            try (CanonicalZipReader.Archive sourceArchive = CanonicalZipReader.read(
                    sourceOutput.toByteArray(), ContainerLimits.sourceHardLimits()
            ); CanonicalZipReader.Archive runtimeArchive = CanonicalZipReader.read(
                    runtimeOutput.toByteArray(), ContainerLimits.runtimeHardLimits()
            )) {
                assertEquals(
                        source.manifestLength(),
                        sourceArchive.entryLength(MinimapContainerLayout.SOURCE_MANIFEST)
                );
                assertEquals(
                        runtime.manifestLength(),
                        runtimeArchive.entryLength(MinimapContainerLayout.RUNTIME_MANIFEST)
                );
            }
        }

        assertEquals(false, sourceOutput.closed);
        assertEquals(false, runtimeOutput.closed);
    }

    @Test
    void rejectsReboundPairWhoseSourceManifestCrossesTheHardLimit() throws Exception {
        TrustedPair pair = trustedPairWithSourceManifestAtHardLimit();

        try (CommittedMapPairSnapshot snapshot = pair.open(7)) {
            assertThrows(
                    ContainerValidationException.class,
                    () -> MapRebindService.rebindCommitted(snapshot, Long.MAX_VALUE)
            );
        }
    }

    @Test
    void rejectsReboundPairWhoseRuntimeManifestCrossesTheHardLimit() throws Exception {
        TrustedPair pair = trustedPairWithRuntimeManifestAtHardLimit();

        try (CommittedMapPairSnapshot snapshot = pair.open(7)) {
            assertThrows(
                    ContainerValidationException.class,
                    () -> MapRebindService.rebindCommitted(snapshot, Long.MAX_VALUE)
            );
        }
    }

    private static TrustedPair trustedPairWithUndecodablePngs() throws Exception {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        SourceManifest baseManifest = base.manifest();
        SourceManifest withPriorOrigin = new SourceManifest(
                baseManifest.formatVersion(),
                baseManifest.documentId(),
                baseManifest.binding(),
                baseManifest.revision(),
                baseManifest.dimension(),
                Optional.of(new Provenance(
                        NamespacedId.parse("example:original-document"),
                        new MapKey("example:origin", "Origin"),
                        NamespacedId.parse("minecraft:the_nether"),
                        3,
                        Sha256Digest.of(new byte[]{3})
                )),
                baseManifest.tileEdge(),
                List.of()
        );
        MinimapDefinition definition = new MinimapDefinition(
                withPriorOrigin,
                base.document(),
                base.regions(),
                base.connections(),
                base.styles()
        );
        PreservedExtensions sourceExtensions = extension("source");
        byte[] validSource = SourceMapWriter.write(new SourceMapDraft(
                definition,
                List.of(new CanonicalZipWriter.Entry(
                        MinimapContainerLayout.THUMBNAIL,
                        CanonicalPngCodecV1.encode(
                                1, 1, new byte[]{1, 2, 3, (byte) 255}
                        )
                )),
                Map.of(MinimapContainerLayout.SOURCE_MANIFEST, sourceExtensions)
        ));

        byte[] validRuntime;
        try (SourceMap source = SourceMapReader.read(validSource)) {
            validRuntime = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:test-rebind"),
                                    MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            ).runtimeBytes();
        }

        byte[] sourceBytes = replaceSourcePng(validSource);
        Sha256 sourceHash = Sha256Digest.of(sourceBytes);
        PreservedExtensions runtimeExtensions = extension("runtime");
        RewrittenRuntime runtime = replaceRuntimePng(
                validRuntime, sourceHash, runtimeExtensions
        );
        return new TrustedPair(
                sourceBytes,
                runtime.bytes(),
                definition.manifest().binding(),
                sourceHash,
                runtime.manifestHash(),
                Sha256Digest.of(runtime.bytes())
        );
    }

    private static TrustedPair trustedPairWithSourceManifestAtHardLimit() throws Exception {
        TrustedPair base = trustedPairWithUndecodablePngs();
        byte[] sourceBytes = padSourceManifestToHardLimit(base.sourceBytes());
        Sha256 sourceHash = Sha256Digest.of(sourceBytes);
        RewrittenRuntime runtime = replaceRuntimePng(
                base.runtimeBytes(), sourceHash, extension("runtime")
        );
        return new TrustedPair(
                sourceBytes,
                runtime.bytes(),
                base.binding(),
                sourceHash,
                runtime.manifestHash(),
                Sha256Digest.of(runtime.bytes())
        );
    }

    private static TrustedPair trustedPairWithRuntimeManifestAtHardLimit() throws Exception {
        TrustedPair base = trustedPairWithUndecodablePngs();
        RewrittenRuntime runtime = padRuntimeManifestToHardLimit(base.runtimeBytes());
        return new TrustedPair(
                base.sourceBytes(),
                runtime.bytes(),
                base.binding(),
                base.sourceHash(),
                runtime.manifestHash(),
                Sha256Digest.of(runtime.bytes())
        );
    }

    private static byte[] padSourceManifestToHardLimit(byte[] sourceBytes) throws Exception {
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                sourceBytes, ContainerLimits.sourceHardLimits()
        )) {
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>(archive.entries());
            JsonObject manifest = StrictJsonParser.parse(
                    entries.get(MinimapContainerLayout.SOURCE_MANIFEST)
            ).getAsJsonObject();
            manifest.remove("provenance");
            JsonObject payload = new JsonObject();
            payload.addProperty("padding", "");
            JsonObject extensions = new JsonObject();
            extensions.add("example_mod", payload);
            manifest.add("extensions", extensions);

            byte[] unpadded = JcsCanonicalizer.canonicalize(manifest);
            int paddingLength = Math.toIntExact(
                    MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES - unpadded.length
            );
            payload.addProperty("padding", "x".repeat(paddingLength));
            byte[] padded = JcsCanonicalizer.canonicalize(manifest);
            if (padded.length != MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES) {
                throw new AssertionError("Source manifest fixture did not reach its hard limit");
            }
            entries.put(MinimapContainerLayout.SOURCE_MANIFEST, padded);
            return CanonicalZipWriter.write(entries, ContainerLimits.sourceHardLimits());
        }
    }

    private static RewrittenRuntime padRuntimeManifestToHardLimit(byte[] runtimeBytes)
            throws Exception {
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                runtimeBytes, ContainerLimits.runtimeHardLimits()
        )) {
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>(archive.entries());
            JsonObject manifest = StrictJsonParser.parse(
                    entries.get(MinimapContainerLayout.RUNTIME_MANIFEST)
            ).getAsJsonObject();
            JsonObject payload = new JsonObject();
            payload.addProperty("padding", "");
            JsonObject extensions = new JsonObject();
            extensions.add("example_mod", payload);
            manifest.add("extensions", extensions);

            byte[] unpadded = JcsCanonicalizer.canonicalize(manifest);
            int paddingLength = Math.toIntExact(
                    MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES - unpadded.length
            );
            payload.addProperty("padding", "x".repeat(paddingLength));
            byte[] padded = JcsCanonicalizer.canonicalize(manifest);
            if (padded.length != MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES) {
                throw new AssertionError("Runtime manifest fixture did not reach its hard limit");
            }
            entries.put(MinimapContainerLayout.RUNTIME_MANIFEST, padded);
            return new RewrittenRuntime(
                    CanonicalZipWriter.write(entries, ContainerLimits.runtimeHardLimits()),
                    Sha256Digest.of(padded)
            );
        }
    }

    private static byte[] replaceSourcePng(byte[] sourceBytes) throws Exception {
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                sourceBytes, ContainerLimits.sourceHardLimits()
        )) {
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>(archive.entries());
            entries.put(MinimapContainerLayout.THUMBNAIL, UNDECODABLE_PNG.clone());
            JsonObject manifest = StrictJsonParser.parse(
                    entries.get(MinimapContainerLayout.SOURCE_MANIFEST)
            ).getAsJsonObject();
            updateDescriptor(manifest, MinimapContainerLayout.THUMBNAIL, UNDECODABLE_PNG);
            entries.put(
                    MinimapContainerLayout.SOURCE_MANIFEST,
                    JcsCanonicalizer.canonicalize(manifest)
            );
            return CanonicalZipWriter.write(entries, ContainerLimits.sourceHardLimits());
        }
    }

    private static RewrittenRuntime replaceRuntimePng(
            byte[] runtimeBytes,
            Sha256 sourceHash,
            PreservedExtensions extensions
    ) throws Exception {
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                runtimeBytes, ContainerLimits.runtimeHardLimits()
        )) {
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>(archive.entries());
            entries.put(RUNTIME_TILE, UNDECODABLE_PNG.clone());
            JsonObject manifest = StrictJsonParser.parse(
                    entries.get(MinimapContainerLayout.RUNTIME_MANIFEST)
            ).getAsJsonObject();
            manifest.addProperty("sourceHash", sourceHash.value());
            manifest.add("extensions", extensions.asJsonObject());
            updateDescriptor(manifest, RUNTIME_TILE, UNDECODABLE_PNG);
            byte[] manifestBytes = JcsCanonicalizer.canonicalize(manifest);
            entries.put(MinimapContainerLayout.RUNTIME_MANIFEST, manifestBytes);
            return new RewrittenRuntime(
                    CanonicalZipWriter.write(entries, ContainerLimits.runtimeHardLimits()),
                    Sha256Digest.of(manifestBytes)
            );
        }
    }

    private static void updateDescriptor(
            JsonObject manifest,
            ContainerPath path,
            byte[] bytes
    ) {
        JsonArray descriptors = manifest.getAsJsonArray("entries");
        for (var descriptor : descriptors) {
            JsonObject object = descriptor.getAsJsonObject();
            if (path.value().equals(object.get("path").getAsString())) {
                object.add("byteLength", new JsonPrimitive(Integer.toString(bytes.length)));
                object.add("sha256", new JsonPrimitive(Sha256Digest.of(bytes).value()));
                return;
            }
        }
        throw new AssertionError("Fixture descriptor is missing: " + path);
    }

    private static void assertNonManifestEntriesEqual(
            CommittedMapPairSnapshot expected,
            CommittedMapPairSnapshot actual,
            boolean source
    ) throws Exception {
        ContainerPath manifest = source
                ? MinimapContainerLayout.SOURCE_MANIFEST
                : MinimapContainerLayout.RUNTIME_MANIFEST;
        var expectedPaths = source ? expected.sourcePaths() : expected.runtimePaths();
        var actualPaths = source ? actual.sourcePaths() : actual.runtimePaths();
        assertEquals(expectedPaths, actualPaths);
        for (ContainerPath path : expectedPaths) {
            if (path.equals(manifest)) {
                continue;
            }
            CanonicalZipWriter.EntrySource expectedEntry = source
                    ? expected.sourceEntrySource(path)
                    : expected.runtimeEntrySource(path);
            CanonicalZipWriter.EntrySource actualEntry = source
                    ? actual.sourceEntrySource(path)
                    : actual.runtimeEntrySource(path);
            assertArrayEquals(readAll(expectedEntry), readAll(actualEntry), path.value());
        }
    }

    private static byte[] readAll(CanonicalZipWriter.EntrySource source) throws Exception {
        try (InputStream input = source.openStream()) {
            return input.readAllBytes();
        }
    }

    private static PreservedExtensions extension(String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", value);
        JsonObject root = new JsonObject();
        root.add("example_mod", payload);
        return PreservedExtensions.of(root);
    }

    private record RewrittenRuntime(byte[] bytes, Sha256 manifestHash) {
    }

    private record TrustedPair(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            MapKey binding,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        private CommittedMapPairSnapshot open(long revision) {
            return CommittedMapPairSnapshot.open(
                    sourceBytes,
                    runtimeBytes,
                    binding,
                    revision,
                    sourceHash,
                    runtimeHash,
                    runtimeContainerHash
            );
        }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
