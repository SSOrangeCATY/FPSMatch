package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapCacheKey;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalModelJson;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CompiledMapPair;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapWriter;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionDisplayDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionEndpoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloorConnection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapClientSyncManagerSecurityTest {
    @TempDir
    Path temp;

    @Test
    void rejectsInvalidAssembledEntryBeforeDiskWriteOrActivation() throws Exception {
        byte[] payload = "not-a-valid-runtime-entry".getBytes(StandardCharsets.UTF_8);
        Sha256 hash = Sha256Digest.of(payload);
        MinimapCacheKey cacheKey = new MinimapCacheKey(
                "server-a",
                NamespacedId.parse("minecraft:overworld"),
                new MapKey("cs", "dust2"),
                NamespacedId.parse("fpsmatch:dust2"),
                1L,
                hash,
                hash,
                "floors/ground/tiles/0/0_0.png"
        );
        TransferKey transfer = new TransferKey(
                cacheKey.stablePath(), hash, payload.length, 1
        );
        RuntimeEntryStore entries = new RuntimeEntryStore();
        MinimapClientSyncManager manager = new MinimapClientSyncManager(
                new FragmentAccumulator(4, 1024 * 1024, 30_000L),
                new MinimapDiskCache(temp.resolve("cache"), 16L * 1024 * 1024),
                entries,
                (key, bytes) -> false
        );

        assertTrue(manager.acceptFragment(cacheKey, transfer, 0, payload, 0L).isEmpty());
        assertTrue(entries.active(cacheKey.mapKey()).isEmpty());
        if (Files.exists(temp.resolve("cache"))) {
            try (var paths = Files.walk(temp.resolve("cache"))) {
                assertEquals(0, paths.filter(Files::isRegularFile).count());
            }
        }
    }

    @Test
    void requiresMatchingManifestBeforeAcceptingDeclaredRuntimeEntries() throws Exception {
        RuntimeFixture fixture = runtimeFixture();
        RuntimeEntryStore entries = new RuntimeEntryStore();
        MinimapClientSyncManager manager = manager(entries);

        assertTrue(manager.acceptEntry(
                fixture.generation(), fixture.tilePath(), fixture.tileTransfer(),
                0, fixture.tileBytes(), 0L
        ).isEmpty());
        assertNoCacheFiles();

        assertTrue(manager.acceptManifest(
                fixture.generation(), fixture.manifestTransfer(),
                0, fixture.manifestBytes(), 1L
        ).isPresent());
        assertTrue(manager.acceptEntry(
                fixture.generation(), fixture.tilePath(), fixture.tileTransfer(),
                0, fixture.tileBytes(), 2L
        ).isPresent());
        assertTrue(entries.activeRuntime(fixture.generation().mapKey()).isEmpty());
        assertFalse(manager.activateGeneration(
                fixture.generation(), java.util.List.of(fixture.tilePath())
        ));

        long now = 3L;
        for (ContainerPath authorityPath : fixture.authorityPaths()) {
            byte[] authorityBytes = fixture.entryBytes(authorityPath);
            assertTrue(manager.acceptEntry(
                    fixture.generation(), authorityPath,
                    transfer(authorityPath.value(), authorityBytes),
                    0, authorityBytes, now++
            ).isPresent());
        }
        assertTrue(manager.activateGeneration(
                fixture.generation(), java.util.List.of(fixture.tilePath())
        ));
        RuntimeEntryStore.ActiveRuntime active = entries.activeRuntime(
                fixture.generation().mapKey()
        ).orElseThrow();
        assertEquals(fixture.generation().revision(), active.revision());
        assertTrue(active.entry("runtime-manifest.json").isPresent());
        for (ContainerPath authorityPath : fixture.authorityPaths()) {
            assertTrue(active.entry(authorityPath.value()).isPresent());
        }
    }

    @Test
    void rejectsCrossEntryAuthorityMismatchBeforeAtomicActivation() throws Exception {
        RuntimeFixture fixture = runtimeFixture().withReplacementEntry(
                ContainerPath.parse("connections.json"),
                CanonicalModelJson.write(
                        new ConnectionsFile(List.of(new MinimapFloorConnection(
                                "invalid-link",
                                new ConnectionEndpoint(
                                        "ground", new CanvasPoint(10, 10)
                                ),
                                new ConnectionEndpoint(
                                        "missing", new CanvasPoint(20, 20)
                                ),
                                ConnectionType.STAIRS,
                                ConnectionDisplayDirection.BIDIRECTIONAL,
                                Optional.empty()
                        ))),
                        MinimapModelCodecs.CONNECTIONS
                )
        );
        RuntimeEntryStore entries = new RuntimeEntryStore();
        MinimapClientSyncManager manager = manager(entries);

        assertTrue(manager.acceptManifest(
                fixture.generation(), fixture.manifestTransfer(),
                0, fixture.manifestBytes(), 0L
        ).isPresent());
        long now = 1L;
        for (Map.Entry<ContainerPath, byte[]> entry : fixture.entries().entrySet()) {
            assertTrue(manager.acceptEntry(
                    fixture.generation(), entry.getKey(),
                    transfer(entry.getKey().value(), entry.getValue()),
                    0, entry.getValue(), now++
            ).isPresent());
        }

        assertFalse(manager.activateGeneration(
                fixture.generation(), List.of(fixture.tilePath())
        ));
        assertTrue(entries.activeRuntime(fixture.generation().mapKey()).isEmpty());
    }

    @Test
    void rejectsManifestIdentityMismatchAndUndeclaredOrSourceOnlyEntries() throws Exception {
        RuntimeFixture fixture = runtimeFixture();
        MinimapClientSyncManager manager = manager(new RuntimeEntryStore());
        RuntimeGeneration wrongRevision = new RuntimeGeneration(
                fixture.generation().connectionEpoch(),
                fixture.generation().serverIdentity(),
                fixture.generation().mapKey(),
                fixture.generation().documentId(),
                fixture.generation().revision() + 1,
                fixture.generation().runtimeHash(),
                fixture.generation().dimension(),
                fixture.generation().localGeneration()
        );

        assertTrue(manager.acceptManifest(
                wrongRevision, fixture.manifestTransfer(),
                0, fixture.manifestBytes(), 0L
        ).isEmpty());
        assertTrue(manager.acceptManifest(
                fixture.generation(), fixture.manifestTransfer(),
                0, fixture.manifestBytes(), 1L
        ).isPresent());

        ContainerPath undeclared = ContainerPath.parse("thumbnail.png");
        byte[] undeclaredBytes = fixture.tileBytes();
        assertTrue(manager.acceptEntry(
                fixture.generation(), undeclared,
                transfer(undeclared.value(), undeclaredBytes),
                0, undeclaredBytes, 2L
        ).isEmpty());

        ContainerPath sourceOnly = ContainerPath.parse("document.json");
        byte[] sourceBytes = "{}".getBytes(StandardCharsets.UTF_8);
        assertTrue(manager.acceptEntry(
                fixture.generation(), sourceOnly,
                transfer(sourceOnly.value(), sourceBytes),
                0, sourceBytes, 3L
        ).isEmpty());
    }

    @Test
    void rejectsDeclaredEntryTransferLengthOrHashMismatchBeforeAssembly() throws Exception {
        RuntimeFixture fixture = runtimeFixture();
        MinimapClientSyncManager manager = manager(new RuntimeEntryStore());
        assertTrue(manager.acceptManifest(
                fixture.generation(), fixture.manifestTransfer(),
                0, fixture.manifestBytes(), 0L
        ).isPresent());

        TransferKey wrongLength = new TransferKey(
                fixture.tilePath().value(),
                fixture.tileTransfer().objectHash(),
                fixture.tileTransfer().totalLength() - 1,
                1
        );
        assertTrue(manager.acceptEntry(
                fixture.generation(), fixture.tilePath(), wrongLength,
                0, fixture.tileBytes(), 1L
        ).isEmpty());

        byte[] other = "other".getBytes(StandardCharsets.UTF_8);
        assertTrue(manager.acceptEntry(
                fixture.generation(), fixture.tilePath(),
                transfer(fixture.tilePath().value(), other),
                0, other, 2L
        ).isEmpty());
        assertNoCacheFilesExceptManifest();
    }

    @Test
    void rejectsMalformedDeclaredThumbnailWithoutEscapingToClientThread() throws Exception {
        RuntimeFixture fixture = runtimeFixture();
        byte[] hostilePng = new byte[] {1, 2, 3, 4, 5};
        RuntimeFixture hostile = fixture.withAdditionalEntry(
                ContainerPath.parse("thumbnail.png"), hostilePng
        );
        RuntimeEntryStore entries = new RuntimeEntryStore();
        MinimapClientSyncManager manager = manager(entries);

        assertTrue(manager.acceptManifest(
                hostile.generation(), hostile.manifestTransfer(),
                0, hostile.manifestBytes(), 0L
        ).isPresent());
        Optional<byte[]> accepted = assertDoesNotThrow(() -> manager.acceptEntry(
                hostile.generation(), ContainerPath.parse("thumbnail.png"),
                transfer("thumbnail.png", hostilePng),
                0, hostilePng, 1L
        ));

        assertTrue(accepted.isEmpty());
        assertTrue(entries.active(hostile.generation().mapKey()).isEmpty());
        assertNoCacheFilesExceptManifest();
    }

    @Test
    void clearingTransientStateDropsActivatedRuntimeMemory() {
        RuntimeEntryStore entries = new RuntimeEntryStore();
        byte[] payload = "active".getBytes(StandardCharsets.UTF_8);
        Sha256 runtimeHash = Sha256Digest.of(
                "runtime".getBytes(StandardCharsets.UTF_8)
        );
        MinimapCacheKey key = new MinimapCacheKey(
                "server-a",
                NamespacedId.parse("minecraft:overworld"),
                new MapKey("cs", "dust2"),
                NamespacedId.parse("fpsmatch:dust2"),
                1L,
                runtimeHash,
                Sha256Digest.of(payload),
                "regions-runtime.json"
        );
        entries.activate(key, payload);
        MinimapClientSyncManager manager = manager(entries);

        manager.clearTransientState();

        assertTrue(entries.activeRuntime(key.mapKey()).isEmpty());
    }

    private MinimapClientSyncManager manager(RuntimeEntryStore entries) {
        return new MinimapClientSyncManager(
                new FragmentAccumulator(8, 16L * 1024 * 1024, 30_000L),
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                entries,
                (key, bytes) -> true
        );
    }

    private RuntimeFixture runtimeFixture() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair pair = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:test-compiler"),
                                    MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            );
            try (RuntimeMap runtime = RuntimeMapReader.read(pair.runtimeBytes())) {
                ContainerPath tilePath = ContainerPath.parse("floors/ground/tiles/0/0_0.png");
                byte[] tileBytes = runtime.entryBytes(tilePath);
                RuntimeGeneration generation = new RuntimeGeneration(
                        1L,
                        "server-a",
                        runtime.manifest().binding(),
                        runtime.manifest().documentId(),
                        runtime.manifest().publishRevision(),
                        runtime.runtimeHash(),
                        NamespacedId.parse("minecraft:overworld"),
                        1L
                );
                return new RuntimeFixture(
                        generation,
                        runtime.manifest(),
                        runtime.manifestBytes(),
                        tilePath,
                        runtimeEntries(runtime)
                );
            }
        }
    }

    private static Map<ContainerPath, byte[]> runtimeEntries(RuntimeMap runtime) {
        LinkedHashMap<ContainerPath, byte[]> entries = new LinkedHashMap<>();
        for (RuntimeEntryDescriptor descriptor : runtime.manifest().entries()) {
            entries.put(descriptor.path(), runtime.entryBytes(descriptor.path()));
        }
        return entries;
    }

    private static TransferKey transfer(String path, byte[] bytes) {
        return new TransferKey(path, Sha256Digest.of(bytes), bytes.length, 1);
    }

    private void assertNoCacheFiles() throws Exception {
        if (!Files.exists(temp.resolve("cache"))) {
            return;
        }
        try (var paths = Files.walk(temp.resolve("cache"))) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    private void assertNoCacheFilesExceptManifest() throws Exception {
        try (var paths = Files.walk(temp.resolve("cache"))) {
            assertEquals(1, paths.filter(Files::isRegularFile).count());
        }
    }

    private record RuntimeFixture(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            byte[] manifestBytes,
            ContainerPath tilePath,
            Map<ContainerPath, byte[]> entries
    ) {
        private RuntimeFixture {
            manifest = java.util.Objects.requireNonNull(manifest, "manifest");
            manifestBytes = manifestBytes.clone();
            LinkedHashMap<ContainerPath, byte[]> copied = new LinkedHashMap<>();
            entries.forEach((path, payload) -> copied.put(path, payload.clone()));
            entries = Map.copyOf(copied);
        }

        TransferKey manifestTransfer() {
            return transfer("runtime-manifest.json", manifestBytes);
        }

        TransferKey tileTransfer() {
            return transfer(tilePath.value(), tileBytes());
        }

        List<ContainerPath> authorityPaths() {
            return List.of(
                    ContainerPath.parse("regions-runtime.json"),
                    ContainerPath.parse("connections.json"),
                    ContainerPath.parse("styles-runtime.json")
            );
        }

        byte[] entryBytes(ContainerPath path) {
            byte[] payload = entries.get(path);
            if (payload == null) {
                throw new IllegalArgumentException("Missing fixture entry: " + path);
            }
            return payload.clone();
        }

        byte[] tileBytes() {
            return entryBytes(tilePath);
        }

        RuntimeFixture withAdditionalEntry(ContainerPath path, byte[] payload) {
            ArrayList<RuntimeEntryDescriptor> descriptors = new ArrayList<>(manifest.entries());
            descriptors.add(new RuntimeEntryDescriptor(
                    path, payload.length, Sha256Digest.of(payload)
            ));
            descriptors.sort(Comparator.comparing(entry -> entry.path().value()));
            RuntimeManifest updated = new RuntimeManifest(
                    manifest.formatVersion(),
                    manifest.documentId(),
                    manifest.binding(),
                    manifest.publishRevision(),
                    manifest.sourceHash(),
                    manifest.compilerProfile(),
                    manifest.canvas(),
                    manifest.defaultViewMode(),
                    manifest.floors(),
                    manifest.tileEdge(),
                    descriptors
            );
            byte[] updatedBytes = CanonicalModelJson.write(
                    updated, MinimapModelCodecs.RUNTIME_MANIFEST
            );
            RuntimeGeneration updatedGeneration = new RuntimeGeneration(
                    generation.connectionEpoch(),
                    generation.serverIdentity(),
                    generation.mapKey(),
                    generation.documentId(),
                    generation.revision(),
                    Sha256Digest.of(updatedBytes),
                    generation.dimension(),
                    generation.localGeneration()
            );
            return new RuntimeFixture(
                    updatedGeneration, updated, updatedBytes, tilePath, entries
            );
        }

        RuntimeFixture withReplacementEntry(ContainerPath path, byte[] payload) {
            LinkedHashMap<ContainerPath, byte[]> updatedEntries = new LinkedHashMap<>(entries);
            updatedEntries.put(path, payload.clone());
            ArrayList<RuntimeEntryDescriptor> descriptors = new ArrayList<>();
            updatedEntries.forEach((entryPath, entryBytes) -> descriptors.add(
                    new RuntimeEntryDescriptor(
                            entryPath, entryBytes.length, Sha256Digest.of(entryBytes)
                    )
            ));
            descriptors.sort(Comparator.comparing(entry -> entry.path().value()));
            RuntimeManifest updated = new RuntimeManifest(
                    manifest.formatVersion(),
                    manifest.documentId(),
                    manifest.binding(),
                    manifest.publishRevision(),
                    manifest.sourceHash(),
                    manifest.compilerProfile(),
                    manifest.canvas(),
                    manifest.defaultViewMode(),
                    manifest.floors(),
                    manifest.tileEdge(),
                    descriptors
            );
            byte[] updatedBytes = CanonicalModelJson.write(
                    updated, MinimapModelCodecs.RUNTIME_MANIFEST
            );
            RuntimeGeneration updatedGeneration = new RuntimeGeneration(
                    generation.connectionEpoch(),
                    generation.serverIdentity(),
                    generation.mapKey(),
                    generation.documentId(),
                    generation.revision(),
                    Sha256Digest.of(updatedBytes),
                    generation.dimension(),
                    generation.localGeneration()
            );
            return new RuntimeFixture(
                    updatedGeneration, updated, updatedBytes, tilePath, updatedEntries
            );
        }

        @Override
        public byte[] manifestBytes() {
            return manifestBytes.clone();
        }

        @Override
        public Map<ContainerPath, byte[]> entries() {
            LinkedHashMap<ContainerPath, byte[]> copied = new LinkedHashMap<>();
            entries.forEach((path, payload) -> copied.put(path, payload.clone()));
            return Map.copyOf(copied);
        }
    }
}
