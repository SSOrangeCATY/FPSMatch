package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.format.ContainerValidationException;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapStorageFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinRuntimeMapResolverTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final NamespacedId RESOURCE = NamespacedId.parse(
            "blockoffensive:dust2"
    );

    @TempDir
    Path temp;

    @Test
    void validatesRegistersAndServesOnlyMatchingRevisionZeroAuthority() throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        Path runtimePath = temp.resolve("dust2.fpsmapc");
        Files.write(runtimePath, pair.runtime());
        BuiltinRuntimeBinding declaration;
        Sha256 sourceHash;
        Sha256 runtimeHash;
        Sha256 containerHash;
        try (var runtime = RuntimeMapReader.read(pair.runtime())) {
            declaration = new BuiltinRuntimeBinding(
                    runtime.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtime.manifest().documentId(),
                    runtime.runtimeHash()
            );
            sourceHash = runtime.manifest().sourceHash();
            runtimeHash = runtime.runtimeHash();
            containerHash = runtime.runtimeContainerHash();
        }
        BuiltinRuntimeMapRegistry registry = BuiltinRuntimeMapRegistry.builder()
                .register(RESOURCE, declaration, runtimePath)
                .build();
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                declaration.binding(), declaration.dimension()
        );
        RuntimeAuthority authority = new RuntimeAuthority(
                target, declaration.documentId(), 0L, sourceHash, runtimeHash
        );
        BuiltinRuntimeMapResolver resolver = new BuiltinRuntimeMapResolver(
                registry,
                (actorId, requested) -> actorId.equals(PLAYER)
                        && requested.equals(target)
                        ? Optional.of(authority)
                        : Optional.empty()
        );

        try (RuntimeMapSource source = resolver.resolve(PLAYER, target).orElseThrow()) {
            assertEquals(0L, source.identity().revision());
            assertEquals(runtimeHash, source.identity().runtimeHash());
            assertEquals(Optional.of(containerHash),
                    source.identity().runtimeContainerHash());
            ContainerPath regions = ContainerPath.parse("regions-runtime.json");
            try (var expected = RuntimeMapReader.read(pair.runtime())) {
                assertArrayEquals(
                        expected.entryBytes(regions),
                        source.openEntry(regions).readAllBytes()
                );
            }
        }

        assertTrue(new BuiltinRuntimeMapResolver(
                registry,
                (actorId, requested) -> Optional.of(new RuntimeAuthority(
                        target, declaration.documentId(), 1L,
                        sourceHash, runtimeHash
                ))
        ).resolve(PLAYER, target).isEmpty());
        assertTrue(new BuiltinRuntimeMapResolver(
                registry,
                (actorId, requested) -> Optional.of(new RuntimeAuthority(
                        target, declaration.documentId(), 0L,
                        Sha256.parse("b".repeat(64)), runtimeHash
                ))
        ).resolve(PLAYER, target).isEmpty());
    }

    @Test
    void rejectsInvalidDeclarationsPositiveRevisionPackagesAndDuplicateTargets()
            throws Exception {
        MinimapStorageFixtures.Pair revisionZero = MinimapStorageFixtures.validPair(0L);
        MinimapStorageFixtures.Pair revisionOne = MinimapStorageFixtures.validPair(1L);
        Path zeroPath = temp.resolve("zero.fpsmapc");
        Path onePath = temp.resolve("one.fpsmapc");
        Files.write(zeroPath, revisionZero.runtime());
        Files.write(onePath, revisionOne.runtime());
        BuiltinRuntimeBinding valid;
        try (var runtime = RuntimeMapReader.read(revisionZero.runtime())) {
            valid = new BuiltinRuntimeBinding(
                    runtime.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtime.manifest().documentId(),
                    runtime.runtimeHash()
            );
        }

        assertThrows(ContainerValidationException.class, () ->
                BuiltinRuntimeMapRegistry.builder().register(
                        RESOURCE,
                        new BuiltinRuntimeBinding(
                                valid.binding(), valid.dimension(), valid.documentId(),
                                Sha256.parse("c".repeat(64))
                        ),
                        zeroPath
                ));
        assertThrows(ContainerValidationException.class, () ->
                BuiltinRuntimeMapRegistry.builder().register(
                        RESOURCE, valid, onePath
                ));
        BuiltinRuntimeMapRegistry.Builder builder = BuiltinRuntimeMapRegistry.builder()
                .register(RESOURCE, valid, zeroPath);
        assertThrows(ContainerValidationException.class, () -> builder.register(
                NamespacedId.parse("another:dust2"), valid, zeroPath
        ));
    }

    @Test
    void reportsUnavailableAfterAuthorityMatchesButBuiltinContentIsMissing()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        Path runtimePath = temp.resolve("missing.fpsmapc");
        Files.write(runtimePath, pair.runtime());
        BuiltinRuntimeBinding declaration;
        Sha256 sourceHash;
        try (var runtime = RuntimeMapReader.read(pair.runtime())) {
            declaration = new BuiltinRuntimeBinding(
                    runtime.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtime.manifest().documentId(),
                    runtime.runtimeHash()
            );
            sourceHash = runtime.manifest().sourceHash();
        }
        BuiltinRuntimeMapRegistry registry = BuiltinRuntimeMapRegistry.builder()
                .register(RESOURCE, declaration, runtimePath)
                .build();
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                declaration.binding(), declaration.dimension()
        );
        RuntimeAuthority authority = new RuntimeAuthority(
                target,
                declaration.documentId(),
                0L,
                sourceHash,
                declaration.runtimeHash()
        );
        Files.delete(runtimePath);

        assertThrows(
                RuntimeMapUnavailableException.class,
                () -> new BuiltinRuntimeMapResolver(
                        registry,
                        (actorId, requested) -> Optional.of(authority)
                ).resolve(PLAYER, target)
        );
        assertTrue(new BuiltinRuntimeMapResolver(
                registry,
                (actorId, requested) -> Optional.empty()
        ).resolve(PLAYER, target).isEmpty());
    }
}
