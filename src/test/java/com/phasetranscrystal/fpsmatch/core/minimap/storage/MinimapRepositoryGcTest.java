package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.StrictJsonParser;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapRepositoryGcTest {
    private static PublishTransaction reserve(
            MinimapRepository repository, MapKey key, long baseRevision
    ) {
        return PublishTargetFixture.reserve(repository, key, baseRevision);
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedAncestorSurvivesCollectionUntilThePinIsReleased() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        assertTrue(Files.isDirectory(revisions.resolve("1")));

        repository.pinRevision(key, 1, "draft-ancestor");
        repository.collectGarbage(key);
        assertTrue(Files.isDirectory(revisions.resolve("1")));
        assertTrue(Files.isDirectory(revisions.resolve("2")));
        assertTrue(Files.isDirectory(revisions.resolve("3")));

        repository.unpinRevision(key, 1, "draft-ancestor");
        repository.collectGarbage(key);
        assertFalse(Files.exists(revisions.resolve("1")));
        assertTrue(Files.isDirectory(revisions.resolve("2")));
        assertTrue(Files.isDirectory(revisions.resolve("3")));
    }

    @Test
    void aPinIdIsIdempotentButCannotBeRetargetedToAnotherRevision() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        Path revisions = repository.mapDirectory(key).resolve("revisions");

        repository.pinRevision(key, 1, "stable-pin");
        repository.pinRevision(key, 1, "stable-pin");
        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinRevision(key, 2, "stable-pin")
        );
        repository.collectGarbage(key);

        assertTrue(Files.isDirectory(revisions.resolve("1")));
    }

    @Test
    void fullPinDirectoryRejectsANewPinBeforeCreatingTemporaryContent()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path pins = repository.mapDirectory(key).resolve("pins");
        Files.createDirectories(pins);
        for (int index = 0; index < 4_096; index++) {
            Files.writeString(
                    pins.resolve(String.format("%064x.json", index)),
                    "occupied",
                    StandardCharsets.UTF_8
            );
        }

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinRevision(key, 1, "new-pin")
        );

        assertFalse(Files.exists(pinPath(repository, key, "new-pin")));
        try (var entries = Files.list(pins)) {
            assertTrue(entries.count() == 4_096);
        }
    }

    @Test
    void committedRevisionCanBePinnedWithItsExactSourceHash() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());

        repository.pinCommittedRevision(
                key, 1, Sha256Digest.of(pair.source()), "verified-draft-ancestor"
        );

        assertTrue(Files.isRegularFile(pinPath(
                repository, key, "verified-draft-ancestor"
        )));
    }

    @Test
    void committedRevisionRejectsAPinWithTheWrongSourceHash() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinCommittedRevision(
                        key, 1, Sha256Digest.of(new byte[]{1}), "wrong-source"
                )
        );
        assertFalse(Files.exists(pinPath(repository, key, "wrong-source")));
    }

    @Test
    void committedRevisionWithDamagedSourceCannotBePinnedAsAnAncestor()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Files.write(
                repository.mapDirectory(key).resolve("revisions").resolve("1")
                        .resolve("source.fpsmap"),
                new byte[]{1, 2, 3}
        );

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinCommittedRevision(
                        key,
                        1,
                        Sha256Digest.of(pair.source()),
                        "damaged-source"
                )
        );

        assertFalse(Files.exists(pinPath(repository, key, "damaged-source")));
    }

    @Test
    void preparedRevisionCannotBePinnedAsADraftAncestor() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path record = repository.mapDirectory(key).resolve("revisions")
                .resolve("1").resolve("publish-record.json");
        JsonObject value = StrictJsonParser.parse(Files.readAllBytes(record)).getAsJsonObject();
        value.addProperty("state", "PREPARED");
        Files.write(record, JcsCanonicalizer.canonicalize(value));

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinCommittedRevision(
                        key, 1, Sha256Digest.of(pair.source()), "prepared-ancestor"
                )
        );
        assertFalse(Files.exists(pinPath(repository, key, "prepared-ancestor")));
    }

    @Test
    void committedRecordCannotBePinnedUnderADifferentRevisionDirectory() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Path wrongRevision = revisions.resolve("2");
        Files.createDirectories(wrongRevision);
        Files.copy(
                revisions.resolve("1").resolve("publish-record.json"),
                wrongRevision.resolve("publish-record.json")
        );

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinCommittedRevision(
                        key, 2, Sha256Digest.of(pair.source()), "wrong-revision"
                )
        );
        assertFalse(Files.exists(pinPath(repository, key, "wrong-revision")));
    }

    @Test
    void garbageCollectionDoesNotKeepAPreparedRevisionAsThePreviousFallback() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Path record = revisions.resolve("2").resolve("publish-record.json");
        JsonObject value = StrictJsonParser.parse(Files.readAllBytes(record)).getAsJsonObject();
        value.addProperty("state", "PREPARED");
        Files.write(record, JcsCanonicalizer.canonicalize(value));

        repository.collectGarbage(key);

        assertTrue(Files.isDirectory(revisions.resolve("1")));
        assertFalse(Files.exists(revisions.resolve("2")));
        assertTrue(Files.isDirectory(revisions.resolve("3")));
    }

    @Test
    void pinReadFailureIsNotMaskedAsMissing() {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path pin = pinPath(repository, key, "unreadable-pin");
        fileSystem.failNextRead(pin);

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinRevision(key, 1, "unreadable-pin")
        );
        assertFalse(Files.exists(pin));
    }

    @Test
    void unpinReadFailureIsNotMaskedAsMissing() throws Exception {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        repository.pinRevision(key, 1, "unreadable-unpin");
        Path pin = pinPath(repository, key, "unreadable-unpin");
        Files.delete(pin);
        fileSystem.failNextRead(pin);

        assertThrows(
                ContainerStorageException.class,
                () -> repository.unpinRevision(key, 1, "unreadable-unpin")
        );
    }

    @Test
    void completePinTemporaryDoesNotKeepARevisionAlive() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        Path pins = repository.mapDirectory(key).resolve("pins");
        Files.createDirectories(pins);
        String pinId = "crashed-draft-pin";
        JsonObject value = new JsonObject();
        value.addProperty("pinId", pinId);
        value.addProperty("revision", "1");
        Path temporary = pins.resolve(
                Sha256Digest.of(pinId.getBytes(StandardCharsets.UTF_8)).value()
                        + ".json.crashed.tmp"
        );
        Files.write(temporary, JcsCanonicalizer.canonicalize(value));

        repository.collectGarbage(key);

        assertFalse(Files.exists(
                repository.mapDirectory(key).resolve("revisions").resolve("1")
        ));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void truncatedPinTemporaryCannotBlockGarbageCollection() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        Path pins = repository.mapDirectory(key).resolve("pins");
        Files.createDirectories(pins);
        Path temporary = pins.resolve("f".repeat(64) + ".json.partial.tmp");
        Files.writeString(temporary, "{", StandardCharsets.UTF_8);

        repository.collectGarbage(key);

        assertFalse(Files.exists(
                repository.mapDirectory(key).resolve("revisions").resolve("1")
        ));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void pinFilenameMustMatchTheHashOfItsDeclaredId() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        Path pins = repository.mapDirectory(key).resolve("pins");
        Files.createDirectories(pins);
        JsonObject value = new JsonObject();
        value.addProperty("pinId", "mismatched-pin-id");
        value.addProperty("revision", "1");
        Path mismatched = pins.resolve("a".repeat(64) + ".json");
        Files.write(mismatched, JcsCanonicalizer.canonicalize(value));

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));

        assertTrue(Files.isDirectory(
                repository.mapDirectory(key).resolve("revisions").resolve("1")
        ));
        assertTrue(Files.isRegularFile(mismatched));
    }

    @Test
    void randomMapMissesDoNotGrowTheRepositoryLockTableWithoutBound()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);

        for (int index = 0; index < 257; index++) {
            repository.collectGarbage(new MapKey("fpsmatch:test", "Map " + index));
        }

        Field locksField = MinimapRepository.class.getDeclaredField("locks");
        locksField.setAccessible(true);
        Map<?, ?> locks = (Map<?, ?>) locksField.get(repository);
        assertTrue(locks.size() <= 64, "repository lock table retained " + locks.size());
    }

    @Test
    void nonDirectoryPinsContainerIsNotTreatedAsMissing() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path pins = repository.mapDirectory(key).resolve("pins");
        Files.write(pins, new byte[]{1});

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));
        assertTrue(Files.isRegularFile(pins));
    }

    @Test
    void pinsDirectorySymlinkCannotRedirectPinWrites() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path pins = repository.mapDirectory(key).resolve("pins");
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside-pins"));
        try {
            Files.createSymbolicLink(pins, outside.toAbsolutePath());
        } catch (UnsupportedOperationException | java.io.IOException
                 | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }

        assertThrows(
                ContainerStorageException.class,
                () -> repository.pinRevision(key, 1, "outside-pin")
        );
        try (var paths = Files.list(outside)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void pinRevisionAttributeFailureIsNotMaskedAsMissing() {
        TargetAttributeReadFailingRepositoryFileSystem fileSystem =
                new TargetAttributeReadFailingRepositoryFileSystem(
                        new NioRepositoryFileSystem()
                );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        Path revision = repository.mapDirectory(key).resolve("revisions").resolve("1");
        fileSystem.failNextAttributeRead(revision);

        ContainerStorageException failure = assertThrows(
                ContainerStorageException.class,
                () -> repository.pinRevision(key, 1, "unreadable-revision")
        );
        assertTrue(failure.getCause() instanceof AccessDeniedException);
    }

    @Test
    void nonDirectoryRevisionsContainerIsNotTreatedAsMissing() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        Path mapDirectory = repository.mapDirectory(key);
        Files.createDirectories(mapDirectory);
        Path revisions = mapDirectory.resolve("revisions");
        Files.write(revisions, new byte[]{1});

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));
        assertTrue(Files.isRegularFile(revisions));
    }

    @Test
    void nonDirectoryRevisionEntryIsNotSilentlySkippedByGarbageCollection()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Path unexpectedEntry = revisions.resolve("unexpected");
        Files.write(unexpectedEntry, new byte[]{1});

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));
        assertTrue(Files.isRegularFile(unexpectedEntry));
    }

    @Test
    void nonCanonicalNumericRevisionDirectoryIsNotSilentlyRetained()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Path alias = Files.createDirectory(revisions.resolve("01"));

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));
        assertTrue(Files.isDirectory(alias));
    }

    @Test
    void missingCurrentRequiresRecoveryBeforeGarbageCollection()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        Path mapDirectory = repository.mapDirectory(key);
        Path revisions = mapDirectory.resolve("revisions");
        Files.delete(mapDirectory.resolve("CURRENT"));

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));

        assertTrue(Files.isDirectory(revisions.resolve("1")));
        assertTrue(Files.isDirectory(revisions.resolve("2")));
    }

    @Test
    void garbageCollectionPrevalidatesEveryDeletionTreeBeforeDeletingAnyRevision()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        publish(repository, key, 3);
        publish(repository, key, 4);
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Path deep = revisions.resolve("2");
        for (int depth = 0; depth < 17; depth++) {
            deep = deep.resolve("level-" + depth);
        }
        Files.createDirectories(deep);
        Files.write(deep.resolve("sentinel.bin"), new byte[]{1});

        assertThrows(ContainerStorageException.class, () -> repository.collectGarbage(key));

        assertTrue(Files.isDirectory(revisions.resolve("1")));
        assertTrue(Files.isDirectory(revisions.resolve("2")));
        assertTrue(Files.isRegularFile(deep.resolve("sentinel.bin")));
    }

    private static Path pinPath(MinimapRepository repository, MapKey key, String pinId) {
        String name = Sha256Digest.of(pinId.getBytes(StandardCharsets.UTF_8)).value()
                + ".json";
        return repository.mapDirectory(key).resolve("pins").resolve(name);
    }

    private static void publish(MinimapRepository repository, MapKey key, long revision) {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(revision);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, revision - 1), pair.source(), pair.runtime()
        );
        if (!repository.commit(prepared).committed()) {
            throw new AssertionError("Fixture publish did not commit");
        }
    }
}
