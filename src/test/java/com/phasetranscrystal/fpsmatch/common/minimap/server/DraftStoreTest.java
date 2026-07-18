package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.StrictJsonParser;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftStoreTest {
    private static final MapKey MAP = new MapKey("fpsmatch:test", "Test Map");
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    private static final NamespacedId DOCUMENT = NamespacedId.parse("fpsmatch:test_map");
    private static final Sha256 BASE = Sha256.parse("1".repeat(64));
    private static final Sha256 EMPTY_ROOT = Sha256Digest.of(new byte[0]);

    @TempDir
    Path temporaryDirectory;

    @Test
    void contiguousOperationsPersistAckRootAndDuplicateIdentityAcrossRestart() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, clock
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);

        DraftAck firstAck = store.apply(
                draft.draftId(), draft.draftRootHash(),
                1, Sha256Digest.of(first), first
        );
        DraftAck duplicate = store.apply(
                draft.draftId(), draft.draftRootHash(),
                1, Sha256Digest.of(first), first
        );
        DraftAck secondAck = store.apply(
                draft.draftId(), firstAck.draftRootHash(),
                2, Sha256Digest.of(second), second
        );

        assertEquals(firstAck, duplicate);
        assertEquals(2, secondAck.ackCursor());
        DraftStore restarted = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, clock
        );
        DraftState restored = restarted.get(draft.draftId()).orElseThrow();
        assertEquals(2, restored.ackCursor());
        assertEquals(secondAck.draftRootHash(), restored.draftRootHash());
        assertEquals(secondAck, restarted.apply(
                draft.draftId(), firstAck.draftRootHash(),
                2, Sha256Digest.of(second), second
        ));
    }

    @Test
    void restartRejectsDraftContentWhoseBytesDoNotMatchItsAddressHash()
            throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );
        byte[] payload = "valid".getBytes(StandardCharsets.UTF_8);
        Sha256 payloadHash = Sha256Digest.of(payload);
        store.apply(
                draft.draftId(), draft.draftRootHash(), 1, payloadHash, payload
        );
        Files.write(
                temporaryDirectory.resolve(draft.draftId().toString())
                        .resolve("entries")
                        .resolve(payloadHash.value() + ".bin"),
                "xxxxx".getBytes(StandardCharsets.UTF_8)
        );

        DraftException failure = assertThrows(
                DraftException.class,
                () -> new DraftStore(
                        temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
                )
        );

        assertEquals(MinimapErrorCode.HASH_MISMATCH, failure.errorCode());
    }

    @Test
    void restartRejectsDraftRootMetadataThatDoesNotMatchItsOperationChain()
            throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );
        byte[] payload = "valid".getBytes(StandardCharsets.UTF_8);
        store.apply(
                draft.draftId(),
                draft.draftRootHash(),
                1,
                Sha256Digest.of(payload),
                payload
        );
        Path stateFile = temporaryDirectory.resolve(draft.draftId().toString())
                .resolve("draft.json");
        var state = StrictJsonParser.parse(Files.readAllBytes(stateFile)).getAsJsonObject();
        state.addProperty("draftRootHash", "f".repeat(64));
        Files.write(stateFile, JcsCanonicalizer.canonicalize(state));

        DraftException failure = assertThrows(
                DraftException.class,
                () -> new DraftStore(
                        temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
                )
        );

        assertEquals(MinimapErrorCode.VALIDATION_FAILED, failure.errorCode());
    }

    @Test
    void draftPersistsAndRestoresItsDimensionAndDocumentBinding() {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState created = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );

        DraftStore restarted = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState restored = restarted.get(created.draftId()).orElseThrow();

        assertEquals(DIMENSION, restored.dimension());
        assertEquals(DOCUMENT, restored.documentId());
    }

    @Test
    void duplicateAfterClosingAnOutOfOrderGapReturnsItsOriginalAck() {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);

        DraftAck originalSecond = store.apply(
                draft.draftId(), draft.draftRootHash(),
                2, Sha256Digest.of(second), second
        );
        assertEquals(0, originalSecond.ackCursor());
        DraftAck gapClosed = store.apply(
                draft.draftId(), draft.draftRootHash(),
                1, Sha256Digest.of(first), first
        );

        assertEquals(2, gapClosed.ackCursor());
        assertEquals(originalSecond, store.apply(
                draft.draftId(), draft.draftRootHash(),
                2, Sha256Digest.of(second), second
        ));
    }

    @Test
    void outOfOrderDuplicateReturnsItsOriginalAckAcrossRestart() {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);

        DraftAck original = store.apply(
                draft.draftId(), draft.draftRootHash(),
                2, Sha256Digest.of(second), second
        );
        store.apply(
                draft.draftId(), draft.draftRootHash(),
                1, Sha256Digest.of(first), first
        );
        DraftStore restarted = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );

        assertEquals(original, restarted.apply(
                draft.draftId(), draft.draftRootHash(),
                2, Sha256Digest.of(second), second
        ));
    }

    @Test
    void operationCountQuotaRejectsBeforePersistAndSurvivesRestart() {
        DraftStoreLimits limits = new DraftStoreLimits(8, 2, 1024);
        DraftStore store = limitedStore(limits);
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        byte[] payload = "same".getBytes(StandardCharsets.UTF_8);
        Sha256 hash = Sha256Digest.of(payload);

        DraftAck firstAck = store.apply(
                draft.draftId(), draft.draftRootHash(), 1, hash, payload
        );
        DraftAck secondAck = store.apply(
                draft.draftId(), firstAck.draftRootHash(), 2, hash, payload
        );
        assertEquals(1, firstAck.ackCursor());
        assertEquals(2, secondAck.ackCursor());

        DraftException quota = assertThrows(
                DraftException.class,
                () -> store.apply(
                        draft.draftId(), secondAck.draftRootHash(), 3, hash, payload
                )
        );
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, quota.errorCode());

        DraftStore restarted = limitedStore(limits);
        DraftException afterRestart = assertThrows(
                DraftException.class,
                () -> restarted.apply(
                        draft.draftId(), secondAck.draftRootHash(), 3, hash, payload
                )
        );
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, afterRestart.errorCode());
        assertEquals(2, restarted.get(draft.draftId()).orElseThrow().ackCursor());
    }

    @Test
    void uniqueContentByteQuotaRejectsBeforeWritingAnEntry() {
        DraftStoreLimits limits = new DraftStoreLimits(8, 8, 4);
        DraftStore store = limitedStore(limits);
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        byte[] first = new byte[]{1, 2, 3};
        byte[] overflow = new byte[]{4, 5};

        DraftAck firstAck = store.apply(
                draft.draftId(), draft.draftRootHash(),
                1, Sha256Digest.of(first), first
        );
        DraftStore restarted = limitedStore(limits);
        DraftException quota = assertThrows(
                DraftException.class,
                () -> restarted.apply(
                        draft.draftId(), firstAck.draftRootHash(),
                        2, Sha256Digest.of(overflow), overflow
                )
        );

        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, quota.errorCode());
        assertFalse(Files.exists(
                temporaryDirectory.resolve(draft.draftId().toString())
                        .resolve("entries")
                        .resolve(Sha256Digest.of(overflow).value() + ".bin")
        ));
        assertEquals(1, restarted.get(draft.draftId()).orElseThrow().ackCursor());
    }

    @Test
    void activeDraftQuotaIsReleasedOnlyAfterDurableDeletion() {
        DraftStoreLimits limits = new DraftStoreLimits(2, 8, 1024);
        DraftStore store = limitedStore(limits);
        DraftState first = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);

        DraftException quota = assertThrows(
                DraftException.class,
                () -> store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT)
        );
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, quota.errorCode());

        assertTrue(store.discard(first.draftId()));
        store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
    }

    @Test
    void randomMissingDraftIdsCannotGrowTheLockRegistryWithoutBound() throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );

        for (int index = 0; index < 1_000; index++) {
            assertTrue(store.get(UUID.randomUUID()).isEmpty());
        }

        Field field = DraftStore.class.getDeclaredField("locks");
        field.setAccessible(true);
        Object locks = field.get(store);
        int retained = locks instanceof java.util.Map<?, ?> map
                ? map.size()
                : Array.getLength(locks);
        assertTrue(retained <= 64, "retained lock count=" + retained);
    }

    @Test
    void publishedAncestorRequiresAPersistentPinProvider() {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );

        assertThrows(
                IllegalStateException.class,
                () -> store.create(MAP, DIMENSION, DOCUMENT, 1, BASE, EMPTY_ROOT)
        );
    }

    @Test
    void createdDraftPersistsItsActiveLifecycle() throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);

        String state = Files.readString(
                temporaryDirectory.resolve(draft.draftId().toString()).resolve("draft.json"),
                StandardCharsets.UTF_8
        );
        assertTrue(state.contains("\"lifecycle\":\"ACTIVE\""));
    }

    @Test
    void duplicateConflictGapWindowAndOptimisticRootMismatchAreRejected() {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 2, Clock.systemUTC()
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        byte[] payload = new byte[]{1};
        Sha256 hash = Sha256Digest.of(payload);
        DraftAck firstAck = store.apply(
                draft.draftId(), draft.draftRootHash(), 1, hash, payload
        );

        DraftException duplicateConflict = assertThrows(
                DraftException.class,
                () -> store.apply(
                        draft.draftId(), draft.draftRootHash(),
                        1, Sha256Digest.of(new byte[]{2}), new byte[]{2}
                )
        );
        assertEquals(MinimapErrorCode.FRAGMENT_CONFLICT, duplicateConflict.errorCode());

        DraftException gap = assertThrows(
                DraftException.class,
                () -> store.apply(
                        draft.draftId(), firstAck.draftRootHash(), 4, hash, payload
                )
        );
        assertEquals(MinimapErrorCode.VALIDATION_FAILED, gap.errorCode());

        DraftException root = assertThrows(
                DraftException.class,
                () -> store.requireRoot(draft.draftId(), Sha256.parse("f".repeat(64)))
        );
        assertEquals(MinimapErrorCode.REVISION_CONFLICT, root.errorCode());
    }

    @Test
    void applyChecksTheExpectedRootAtomicallyWithTheNewOperation() {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        byte[] firstPayload = new byte[]{1};
        byte[] stalePayload = new byte[]{2};

        DraftAck first = store.apply(
                draft.draftId(),
                draft.draftRootHash(),
                1,
                Sha256Digest.of(firstPayload),
                firstPayload
        );
        DraftException stale = assertThrows(
                DraftException.class,
                () -> store.apply(
                        draft.draftId(),
                        draft.draftRootHash(),
                        2,
                        Sha256Digest.of(stalePayload),
                        stalePayload
                )
        );

        assertEquals(MinimapErrorCode.REVISION_CONFLICT, stale.errorCode());
        DraftState current = store.requireRoot(
                draft.draftId(), first.draftRootHash()
        );
        assertEquals(first.ackCursor(), current.ackCursor());
        assertEquals(first.draftRootHash(), current.draftRootHash());
    }

    @Test
    void expirationRemovesDraftOnlyAfterItsTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, clock
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        clock.advance(Duration.ofDays(7));

        assertEquals(1, store.removeExpired());
        assertEquals(true, store.get(draft.draftId()).isEmpty());
    }

    @Test
    void getExpiresDraftAtTtlWithoutWaitingForBackgroundSweep() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, clock, pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        Path draftDirectory = temporaryDirectory.resolve(draft.draftId().toString());
        clock.advance(Duration.ofDays(7));

        assertTrue(store.get(draft.draftId()).isEmpty());
        assertFalse(Files.exists(draftDirectory));
        assertEquals(
                new PinEvent(false, MAP, 3, "draft:" + draft.draftId()),
                pins.events.get(1)
        );
        assertEquals(0, store.removeExpired());
    }

    @Test
    void draftLifetimePinsItsPublishedAncestorUntilDiscardOrExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, clock, pins
        );

        DraftState discarded = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        assertEquals(
                List.of(new PinEvent(true, MAP, 3, "draft:" + discarded.draftId())),
                pins.events
        );
        assertEquals(true, store.discard(discarded.draftId()));
        assertEquals(
                new PinEvent(false, MAP, 3, "draft:" + discarded.draftId()),
                pins.events.get(1)
        );

        DraftState expired = store.create(MAP, DIMENSION, DOCUMENT, 4, BASE, EMPTY_ROOT);
        clock.advance(Duration.ofDays(7));
        assertEquals(1, store.removeExpired());
        assertEquals(
                new PinEvent(false, MAP, 4, "draft:" + expired.draftId()),
                pins.events.get(3)
        );
    }

    @Test
    void draftPinReceivesTheExpectedBaseSourceHash() {
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);

        assertEquals(List.of(BASE), pins.pinSourceHashes);
    }

    @Test
    void createPersistsCreatingBeforePinAndActiveBeforeReturning() throws Exception {
        RecordingPins pins = new RecordingPins(temporaryDirectory);
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);

        assertEquals("CREATING", pins.lifecycleSeenAtPin);
        Path stateFile = temporaryDirectory.resolve(draft.draftId().toString())
                .resolve("draft.json");
        assertEquals(
                "ACTIVE",
                StrictJsonParser.parse(Files.readAllBytes(stateFile))
                        .getAsJsonObject().get("lifecycle").getAsString()
        );
    }

    @Test
    void createSyncsTheNewDraftDirectoryBeforePinningItsAncestor() {
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        fileSystem.observeRootSync(temporaryDirectory);
        List<Boolean> rootSyncedAtPin = new ArrayList<>();
        DraftAncestorPins pins = new DraftAncestorPins() {
            @Override
            public void pin(
                    MapKey mapKey,
                    long revision,
                    Sha256 expectedSourceHash,
                    String pinId
            ) {
                rootSyncedAtPin.add(fileSystem.observedRootSync());
            }

            @Override
            public void unpin(MapKey mapKey, long revision, String pinId) {
            }
        };
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);

        assertEquals(List.of(true), rootSyncedAtPin);
    }

    @Test
    void createRootSyncFailureLeavesCreatingStateWithoutPinning() throws Exception {
        RecordingPins pins = new RecordingPins();
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        fileSystem.failNextObservedRootSync(temporaryDirectory);
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        DraftException failure = assertThrows(
                DraftException.class,
                () -> store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT)
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertTrue(pins.events.isEmpty());
        Path draftDirectory;
        try (var paths = Files.list(temporaryDirectory)) {
            draftDirectory = paths.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        UUID draftId = UUID.fromString(draftDirectory.getFileName().toString());
        assertEquals("CREATING", lifecycle(draftId));
    }

    @Test
    void createFailureAfterActiveReplacementKeepsTheAncestorPinnedForRestart()
            throws Exception {
        RecordingPins pins = new RecordingPins();
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        fileSystem.failAfterNextActiveWrite();
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        DraftException failure = assertThrows(
                DraftException.class,
                () -> store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT)
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertEquals(1, pins.events.size());
        assertTrue(pins.events.get(0).pin());
        Path draftDirectory;
        try (var paths = Files.list(temporaryDirectory)) {
            draftDirectory = paths.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        assertEquals(
                "ACTIVE",
                StrictJsonParser.parse(Files.readAllBytes(draftDirectory.resolve("draft.json")))
                        .getAsJsonObject().get("lifecycle").getAsString()
        );

        new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        assertEquals(2, pins.events.size());
        assertEquals(pins.events.get(0), pins.events.get(1));
    }

    @Test
    void pinFailureAfterEffectLeavesCreatingDraftHiddenUntilRestartCleanup()
            throws Exception {
        RecordingPins pins = new RecordingPins();
        pins.failNextPinAfterEffect();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        assertThrows(
                IllegalStateException.class,
                () -> store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT)
        );

        assertEquals(1, pins.events.size());
        UUID draftId = UUID.fromString(
                pins.events.get(0).pinId().substring("draft:".length())
        );
        assertEquals("CREATING", lifecycle(draftId));
        assertTrue(store.get(draftId).isEmpty());

        new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        assertEquals(2, pins.events.size());
        assertFalse(Files.exists(temporaryDirectory.resolve(draftId.toString())));
    }

    @Test
    void unpinFailureKeepsDeletingDraftHiddenUntilRestartCleanup() throws Exception {
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        pins.failNextUnpinAfterEffect();

        assertThrows(IllegalStateException.class, () -> store.discard(draft.draftId()));

        assertEquals("DELETING", lifecycle(draft.draftId()));
        assertTrue(store.get(draft.draftId()).isEmpty());
        byte[] payload = "after-delete".getBytes(StandardCharsets.UTF_8);
        DraftException rejected = assertThrows(
                DraftException.class,
                () -> store.apply(
                        draft.draftId(), draft.draftRootHash(),
                        1, Sha256Digest.of(payload), payload
                )
        );
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, rejected.errorCode());

        new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        assertEquals(3, pins.events.size());
        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void deleteFailureLeavesDeletingDraftForRestartCleanup() throws Exception {
        RecordingPins pins = new RecordingPins();
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        fileSystem.failNextDelete();

        DraftException failure = assertThrows(
                DraftException.class,
                () -> store.discard(draft.draftId())
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertEquals("DELETING", lifecycle(draft.draftId()));
        assertTrue(store.get(draft.draftId()).isEmpty());

        new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void partialDeleteFailureKeepsDeletingJournalForRestart() throws Exception {
        RecordingPins pins = new RecordingPins();
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        Path draftDirectory = temporaryDirectory.resolve(draft.draftId().toString());
        Path blocked = draftDirectory.resolve("a-blocked.bin");
        Files.writeString(blocked, "partial deletion", StandardCharsets.UTF_8);
        fileSystem.failDeleting(blocked);

        DraftException failure = assertThrows(
                DraftException.class,
                () -> store.discard(draft.draftId())
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertEquals("DELETING", lifecycle(draft.draftId()));
        assertTrue(Files.exists(blocked));

        new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        assertFalse(Files.exists(draftDirectory));
    }

    @Test
    void draftDeletionRejectsAnOverdeepTreeBeforeDeletingAnyContent()
            throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );
        Path draftDirectory = temporaryDirectory.resolve(draft.draftId().toString());
        Path deepest = draftDirectory;
        for (int depth = 0; depth < 17; depth++) {
            deepest = deepest.resolve("nested-" + depth);
        }
        Files.createDirectories(deepest);
        Path sentinel = Files.writeString(
                deepest.resolve("sentinel.bin"), "retain", StandardCharsets.UTF_8
        );
        Path shallow = Files.writeString(
                draftDirectory.resolve("shallow.bin"), "retain", StandardCharsets.UTF_8
        );

        DraftException failure = assertThrows(
                DraftException.class, () -> store.discard(draft.draftId())
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertEquals("DELETING", lifecycle(draft.draftId()));
        assertTrue(Files.exists(sentinel));
        assertTrue(Files.exists(shallow));
    }

    @Test
    void draftDeletionRejectsTooManyNodesBeforeDeletingAnyContent()
            throws Exception {
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                DraftAncestorPins.NONE,
                fileSystem
        );
        DraftState draft = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );
        fileSystem.returnTreeWithNodes(16_385);

        DraftException failure = assertThrows(
                DraftException.class, () -> store.discard(draft.draftId())
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertEquals(0, fileSystem.deleteAttempts());
        assertEquals("DELETING", lifecycle(draft.draftId()));
    }

    @Test
    void undurableRootSyncReplaysDeletingDraftOnRestart() throws Exception {
        RecordingPins pins = new RecordingPins();
        FaultInjectingFileSystem fileSystem = new FaultInjectingFileSystem();
        DraftStore store = new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        fileSystem.simulateUndurableDeleteOnNextRootSync();

        DraftException failure = assertThrows(
                DraftException.class,
                () -> store.discard(draft.draftId())
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        assertEquals("DELETING", lifecycle(draft.draftId()));
        assertTrue(store.get(draft.draftId()).isEmpty());

        new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                pins,
                fileSystem
        );

        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void discardPersistsDeletingBeforeUnpinAndRemovesTheDraft() {
        RecordingPins pins = new RecordingPins(temporaryDirectory);
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);

        assertTrue(store.discard(draft.draftId()));

        assertEquals("DELETING", pins.lifecycleSeenAtUnpin);
        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void expirationPersistsDeletingBeforeUnpinAndRemovesTheDraft() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        RecordingPins pins = new RecordingPins(temporaryDirectory);
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, clock, pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        clock.advance(Duration.ofDays(7));

        assertEquals(1, store.removeExpired());

        assertEquals("DELETING", pins.lifecycleSeenAtUnpin);
        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void restartRevalidatesThePinForAnActiveDraft() {
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        pins.events.clear();

        new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        assertEquals(
                List.of(new PinEvent(true, MAP, 3, "draft:" + draft.draftId())),
                pins.events
        );
    }

    @Test
    void restartCompletesCleanupOfACreatingDraft() throws Exception {
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        setLifecycle(draft.draftId(), "CREATING");
        pins.events.clear();

        new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        assertEquals(
                List.of(new PinEvent(false, MAP, 3, "draft:" + draft.draftId())),
                pins.events
        );
        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void restartCompletesCleanupOfADeletingDraft() throws Exception {
        RecordingPins pins = new RecordingPins();
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 3, BASE, EMPTY_ROOT);
        setLifecycle(draft.draftId(), "DELETING");
        pins.events.clear();

        new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC(), pins
        );

        assertEquals(
                List.of(new PinEvent(false, MAP, 3, "draft:" + draft.draftId())),
                pins.events
        );
        assertFalse(Files.exists(temporaryDirectory.resolve(draft.draftId().toString())));
    }

    @Test
    void oversizedDraftStateFailsBeforeReadingItsContents() throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        UUID draftId = UUID.randomUUID();
        Path directory = temporaryDirectory.resolve(draftId.toString());
        Files.createDirectory(directory);
        try (RandomAccessFile state = new RandomAccessFile(
                directory.resolve("draft.json").toFile(), "rw"
        )) {
            state.setLength(4L * 1024 * 1024 + 1);
        }

        DraftException failure = assertThrows(
                DraftException.class, () -> store.get(draftId)
        );

        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, failure.errorCode());
    }

    @Test
    void uuidNamedDirectorySymlinkIsNotScannedOrCountedAsADraft()
            throws Exception {
        Path unrelated = Files.createDirectory(temporaryDirectory.resolve("unrelated"));
        Files.createSymbolicLink(
                temporaryDirectory.resolve(UUID.randomUUID().toString()), unrelated
        );
        DraftStore store = limitedStore(new DraftStoreLimits(1, 8, 1_024));

        DraftState created = store.create(
                MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT
        );

        assertTrue(store.get(created.draftId()).isPresent());
    }

    @Test
    void draftRootScanRejectsAnUnboundedNumberOfEntries() throws Exception {
        new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        for (int index = 0; index <= 4_096; index++) {
            Files.write(
                    temporaryDirectory.resolve("unrelated-" + index),
                    new byte[0]
            );
        }

        DraftException failure = assertThrows(
                DraftException.class,
                () -> new DraftStore(
                        temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
                )
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
    }

    @Test
    void directDraftLookupCannotFollowAUuidDirectorySymlink() throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        Path draftDirectory = temporaryDirectory.resolve(draft.draftId().toString());
        Path outside = Files.createTempDirectory("fpsmatch-draft-outside-");
        Path outsideState = outside.resolve("draft.json");
        byte[] state = Files.readAllBytes(draftDirectory.resolve("draft.json"));
        Files.write(outsideState, state);
        Files.delete(draftDirectory.resolve("draft.json"));
        Files.delete(draftDirectory);
        Files.createSymbolicLink(draftDirectory, outside);

        DraftException lookup = assertThrows(
                DraftException.class, () -> store.get(draft.draftId())
        );
        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, lookup.errorCode());
        assertArrayEquals(state, Files.readAllBytes(outsideState));
        assertEquals(List.of("draft.json"), Files.list(outside)
                .map(path -> path.getFileName().toString()).sorted().toList());
    }

    @Test
    void draftEntryDirectorySymlinkCannotRedirectPayloadWrites() throws Exception {
        DraftStore store = new DraftStore(
                temporaryDirectory, Duration.ofDays(7), 16, Clock.systemUTC()
        );
        DraftState draft = store.create(MAP, DIMENSION, DOCUMENT, 0, BASE, EMPTY_ROOT);
        Path outside = Files.createTempDirectory("fpsmatch-entry-outside-");
        Path entries = temporaryDirectory.resolve(draft.draftId().toString()).resolve("entries");
        Files.createSymbolicLink(entries, outside);
        byte[] payload = "outside-write".getBytes(StandardCharsets.UTF_8);

        DraftException failure = assertThrows(
                DraftException.class,
                () -> store.apply(
                        draft.draftId(), draft.draftRootHash(),
                        1, Sha256Digest.of(payload), payload
                )
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, failure.errorCode());
        try (var paths = Files.list(outside)) {
            assertEquals(List.of(), paths.toList());
        }
        assertEquals(0, store.get(draft.draftId()).orElseThrow().ackCursor());
    }

    private void setLifecycle(UUID draftId, String lifecycle) throws Exception {
        Path stateFile = temporaryDirectory.resolve(draftId.toString()).resolve("draft.json");
        var state = StrictJsonParser.parse(Files.readAllBytes(stateFile)).getAsJsonObject();
        state.addProperty("lifecycle", lifecycle);
        Files.write(stateFile, JcsCanonicalizer.canonicalize(state));
    }

    private DraftStore limitedStore(DraftStoreLimits limits) {
        return new DraftStore(
                temporaryDirectory,
                Duration.ofDays(7),
                16,
                Clock.systemUTC(),
                DraftAncestorPins.NONE,
                DraftStore.FileSystem.nio(),
                limits
        );
    }

    private String lifecycle(UUID draftId) throws Exception {
        Path stateFile = temporaryDirectory.resolve(draftId.toString()).resolve("draft.json");
        return StrictJsonParser.parse(Files.readAllBytes(stateFile))
                .getAsJsonObject().get("lifecycle").getAsString();
    }

    private record PinEvent(boolean pin, MapKey mapKey, long revision, String pinId) {
    }

    private static final class RecordingPins implements DraftAncestorPins {
        private final List<PinEvent> events = new ArrayList<>();
        private final List<Sha256> pinSourceHashes = new ArrayList<>();
        private final Path draftRoot;
        private String lifecycleSeenAtPin;
        private String lifecycleSeenAtUnpin;
        private boolean failNextPinAfterEffect;
        private boolean failNextUnpinAfterEffect;

        private RecordingPins() {
            this(null);
        }

        private RecordingPins(Path draftRoot) {
            this.draftRoot = draftRoot;
        }

        @Override
        public void pin(
                MapKey mapKey,
                long revision,
                Sha256 expectedSourceHash,
                String pinId
        ) {
            events.add(new PinEvent(true, mapKey, revision, pinId));
            pinSourceHashes.add(expectedSourceHash);
            if (draftRoot != null) {
                lifecycleSeenAtPin = lifecycleAt(pinId);
            }
            if (failNextPinAfterEffect) {
                failNextPinAfterEffect = false;
                throw new IllegalStateException("injected pin failure after effect");
            }
        }

        @Override
        public void unpin(MapKey mapKey, long revision, String pinId) {
            events.add(new PinEvent(false, mapKey, revision, pinId));
            if (draftRoot != null) {
                lifecycleSeenAtUnpin = lifecycleAt(pinId);
            }
            if (failNextUnpinAfterEffect) {
                failNextUnpinAfterEffect = false;
                throw new IllegalStateException("injected unpin failure after effect");
            }
        }

        private void failNextPinAfterEffect() {
            failNextPinAfterEffect = true;
        }

        private void failNextUnpinAfterEffect() {
            failNextUnpinAfterEffect = true;
        }

        private String lifecycleAt(String pinId) {
            Path stateFile = draftRoot.resolve(pinId.substring("draft:".length()))
                    .resolve("draft.json");
            try {
                return Files.exists(stateFile)
                        ? StrictJsonParser.parse(Files.readAllBytes(stateFile))
                        .getAsJsonObject().get("lifecycle").getAsString()
                        : "<missing>";
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to inspect draft lifecycle", exception);
            }
        }
    }

    private static final class FaultInjectingFileSystem
            implements DraftStore.FileSystem {
        private final DraftStore.FileSystem delegate = DraftStore.FileSystem.nio();
        private boolean failAfterActiveWrite;
        private boolean failNextDelete;
        private boolean retainTreeForFailedRootSync;
        private Path failDeleting;
        private Path observedRoot;
        private boolean observedRootSync;
        private boolean failNextObservedRootSync;
        private int syntheticTreeNodes;
        private int deleteAttempts;

        @Override
        public void createDirectories(Path directory) throws IOException {
            delegate.createDirectories(directory);
        }

        @Override
        public void writeAtomically(Path target, byte[] bytes) throws IOException {
            delegate.writeAtomically(target, bytes);
            if (failAfterActiveWrite
                    && target.getFileName().toString().equals("draft.json")
                    && StrictJsonParser.parse(bytes).getAsJsonObject()
                    .get("lifecycle").getAsString().equals("ACTIVE")) {
                failAfterActiveWrite = false;
                throw new IOException("injected failure after ACTIVE replacement");
            }
        }

        @Override
        public List<Path> listTree(Path root) throws IOException {
            if (syntheticTreeNodes > 0) {
                List<Path> paths = new ArrayList<>(syntheticTreeNodes);
                paths.add(root);
                for (int index = 1; index < syntheticTreeNodes; index++) {
                    paths.add(root.resolve("synthetic-" + index));
                }
                return paths;
            }
            return delegate.listTree(root);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            deleteAttempts++;
            if (path.equals(failDeleting)) {
                failDeleting = null;
                throw new IOException("injected partial draft deletion failure");
            }
            if (failNextDelete) {
                failNextDelete = false;
                throw new IOException("injected draft deletion failure");
            }
            if (retainTreeForFailedRootSync) {
                return;
            }
            delegate.deleteIfExists(path);
        }

        @Override
        public void syncDirectory(Path directory) throws IOException {
            if (directory.equals(observedRoot)) {
                observedRootSync = true;
                if (failNextObservedRootSync) {
                    failNextObservedRootSync = false;
                    throw new IOException("injected draft root fsync failure");
                }
            }
            if (retainTreeForFailedRootSync) {
                retainTreeForFailedRootSync = false;
                throw new IOException("injected root fsync failure");
            }
            delegate.syncDirectory(directory);
        }

        private void failAfterNextActiveWrite() {
            failAfterActiveWrite = true;
        }

        private void failNextDelete() {
            failNextDelete = true;
        }

        private void failDeleting(Path path) {
            failDeleting = path;
        }

        private void observeRootSync(Path root) {
            observedRoot = root.toAbsolutePath().normalize();
        }

        private boolean observedRootSync() {
            return observedRootSync;
        }

        private void failNextObservedRootSync(Path root) {
            observeRootSync(root);
            failNextObservedRootSync = true;
        }

        private void simulateUndurableDeleteOnNextRootSync() {
            retainTreeForFailedRootSync = true;
        }

        private void returnTreeWithNodes(int nodes) {
            syntheticTreeNodes = nodes;
        }

        private int deleteAttempts() {
            return deleteAttempts;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
