package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapStorageFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.NioRepositoryFileSystem;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishTransaction;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.RepositoryFileSystem;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryRuntimeMapResolverTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final MapKey MAP = MinimapContainerFixtures.sourceDefinition()
            .manifest().binding();
    private static final NamespacedId DIMENSION = MinimapContainerFixtures
            .sourceDefinition().manifest().dimension();
    private static final NamespacedId DOCUMENT = MinimapContainerFixtures
            .sourceDefinition().manifest().documentId();

    @TempDir
    Path temp;

    @Test
    void opensOnlyTheCurrentCommittedRuntimeAndDoesNotRequireSourceFile() throws Exception {
        MinimapRepository repository = repository();
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1L);
        PublishTransaction reserved = repository.reserve(
                MAP, DIMENSION, DOCUMENT, 0L
        );
        PublishTransaction prepared = repository.prepare(
                reserved, pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path revision = repository.mapDirectory(MAP)
                .resolve("revisions").resolve("1");
        Files.delete(revision.resolve("source.fpsmap"));

        RepositoryRuntimeMapResolver resolver = new RepositoryRuntimeMapResolver(
                repository,
                (actorId, target) -> Optional.of(new RuntimeAuthority(
                        target, DOCUMENT, 1L,
                        prepared.descriptor().sourceHash(),
                        prepared.descriptor().runtimeHash()
                ))
        );
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(MAP, DIMENSION);

        try (RuntimeMapSource source = resolver.resolve(PLAYER, target).orElseThrow()) {
            assertEquals(prepared.descriptor().runtimeHash(), source.identity().runtimeHash());
            assertEquals(
                    Optional.of(prepared.descriptor().runtimeContainerHash()),
                    source.identity().runtimeContainerHash()
            );
            ContainerPath tile = ContainerPath.parse(
                    "floors/ground/tiles/0/0_0.png"
            );
            try (RuntimeMap expected = RuntimeMapReader.read(pair.runtime())) {
                assertArrayEquals(
                        expected.entryBytes(tile),
                        source.openEntry(tile).readAllBytes()
                );
            }
        }
    }

    @Test
    void rejectsAuthorityRevisionHashDimensionOrMapMismatch() {
        MinimapRepository repository = repository();
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1L);
        PublishTransaction prepared = repository.prepare(
                repository.reserve(MAP, DIMENSION, DOCUMENT, 0L),
                pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(MAP, DIMENSION);

        RepositoryRuntimeMapResolver wrongRevision = new RepositoryRuntimeMapResolver(
                repository,
                (actorId, ignored) -> Optional.of(new RuntimeAuthority(
                        target, DOCUMENT, 2L,
                        prepared.descriptor().sourceHash(),
                        prepared.descriptor().runtimeHash()
                ))
        );
        RepositoryRuntimeMapResolver wrongSourceHash = new RepositoryRuntimeMapResolver(
                repository,
                (actorId, ignored) -> Optional.of(new RuntimeAuthority(
                        target, DOCUMENT, 1L,
                        new com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256(
                                "22".repeat(32)
                        ),
                        prepared.descriptor().runtimeHash()
                ))
        );
        RepositoryRuntimeMapResolver wrongHash = new RepositoryRuntimeMapResolver(
                repository,
                (actorId, ignored) -> Optional.of(new RuntimeAuthority(
                        target, DOCUMENT, 1L,
                        prepared.descriptor().sourceHash(),
                        new com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256(
                                "11".repeat(32)
                        )
                ))
        );

        assertTrue(wrongRevision.resolve(PLAYER, target).isEmpty());
        assertTrue(wrongSourceHash.resolve(PLAYER, target).isEmpty());
        assertTrue(wrongHash.resolve(PLAYER, target).isEmpty());
        assertTrue(wrongRevision.resolve(
                PLAYER,
                new WireIdentity.MapTarget(
                        MAP, NamespacedId.parse("minecraft:the_nether")
                )
        ).isEmpty());
    }

    @Test
    void reportsUnavailableAfterAuthorityMatchesButRuntimeContentIsMissing()
            throws Exception {
        MinimapRepository repository = repository();
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1L);
        PublishTransaction prepared = repository.prepare(
                repository.reserve(MAP, DIMENSION, DOCUMENT, 0L),
                pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(MAP, DIMENSION);
        Files.delete(repository.mapDirectory(MAP)
                .resolve("revisions/1/runtime.fpsmapc"));
        RepositoryRuntimeMapResolver authorized = new RepositoryRuntimeMapResolver(
                repository,
                (actorId, requested) -> Optional.of(new RuntimeAuthority(
                        target, DOCUMENT, 1L,
                        prepared.descriptor().sourceHash(),
                        prepared.descriptor().runtimeHash()
                ))
        );

        assertThrows(
                RuntimeMapUnavailableException.class,
                () -> authorized.resolve(PLAYER, target)
        );
        assertTrue(new RepositoryRuntimeMapResolver(
                repository,
                (actorId, requested) -> Optional.empty()
        ).resolve(PLAYER, target).isEmpty());
    }

    private MinimapRepository repository() {
        return new MinimapRepository(
                temp.resolve("repository"),
                new DirectorySyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
    }

    private static final class DirectorySyncTolerantFileSystem
            implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;

        private DirectorySyncTolerantFileSystem(RepositoryFileSystem delegate) {
            this.delegate = delegate;
        }

        @Override
        public void createDirectories(Path directory) throws IOException {
            delegate.createDirectories(directory);
        }

        @Override
        public LockHandle acquireExclusiveLock(Path lockFile) throws IOException {
            return delegate.acquireExclusiveLock(lockFile);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            return delegate.readAllBytes(file);
        }

        @Override
        public BoundedReadChannel openBoundedReadChannel(
                Path file, long maximumBytes
        ) throws IOException {
            return delegate.openBoundedReadChannel(file, maximumBytes);
        }

        @Override
        public void write(Path file, byte[] bytes) throws IOException {
            delegate.write(file, bytes);
        }

        @Override
        public void fsyncFile(Path file) throws IOException {
            delegate.fsyncFile(file);
        }

        @Override
        public void fsyncDirectory(Path directory) throws IOException {
            try {
                delegate.fsyncDirectory(directory);
            } catch (AccessDeniedException ignored) {
            }
        }

        @Override
        public void moveAtomically(Path source, Path target) throws IOException {
            delegate.moveAtomically(source, target);
        }

        @Override
        public void replaceAtomically(Path source, Path target) throws IOException {
            delegate.replaceAtomically(source, target);
        }
    }
}
