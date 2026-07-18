package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalJsonException;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSectionRevisionIndexTest {
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    private static final WorldSectionKey FIRST = new WorldSectionKey(DIMENSION, 1, 2, 3);
    private static final WorldSectionKey SECOND = new WorldSectionKey(DIMENSION, 1, 3, 3);

    @TempDir
    Path temporaryDirectory;

    @Test
    void mutationsAdvanceAMonotonicEpochAndSnapshotsDetectConcurrentChanges() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();

        assertEquals(1, index.markMutated(FIRST));
        assertEquals(2, index.markMutated(SECOND));
        assertEquals(2, index.worldMutationEpoch(DIMENSION));
        SectionSnapshotStamp before = index.beginSnapshot(42, FIRST);
        assertEquals(1, before.sectionRevision());
        assertFalse(index.finishSnapshot(before).stale());

        index.markMutated(FIRST);
        assertTrue(index.finishSnapshot(before).stale());
        assertEquals(3, index.sectionRevision(FIRST));
    }

    @Test
    void dirtyBaselinesClearOnlyWhenThePublishedRevisionStillMatches() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();
        long first = index.markMutated(FIRST);
        long second = index.markMutated(SECOND);

        assertEquals(Map.of(FIRST, first, SECOND, second), index.dirtySections());
        assertTrue(index.clearDirtyIfUnchanged(FIRST, first));
        assertFalse(index.clearDirtyIfUnchanged(SECOND, first));
        assertEquals(Map.of(SECOND, second), index.dirtySections());

        index.markMutated(SECOND);
        assertFalse(index.clearDirtyIfUnchanged(SECOND, second));
        assertTrue(index.dirtySections().containsKey(SECOND));
    }

    @Test
    void saveStateRestoresSectionAndDirtyRevisionsAfterRestart() {
        WorldSectionRevisionIndex index =
                new WorldSectionRevisionIndex(temporaryDirectory);
        long firstRevision = index.markMutated(FIRST);
        long secondRevision = index.markMutated(SECOND);
        assertTrue(index.clearDirtyIfUnchanged(FIRST, firstRevision));

        index.saveState();
        index.abandonWithoutCleanForTest();

        WorldSectionRevisionIndex restarted =
                new WorldSectionRevisionIndex(temporaryDirectory);
        assertEquals(firstRevision, restarted.sectionRevision(FIRST));
        assertEquals(secondRevision, restarted.sectionRevision(SECOND));
        assertEquals(Map.of(SECOND, secondRevision), restarted.dirtySections());
    }

    @Test
    void uncleanRestartMarksPublishedCoverageAtTheDurableHighWater() {
        WorldSectionRevisionIndex crashed =
                new WorldSectionRevisionIndex(temporaryDirectory);
        assertEquals(1, crashed.markMutated(FIRST));
        crashed.abandonWithoutCleanForTest();

        WorldSectionRevisionIndex restarted = new WorldSectionRevisionIndex(
                temporaryDirectory, Set.of(FIRST, SECOND)
        );

        assertEquals(4096, restarted.sectionRevision(FIRST));
        assertEquals(4096, restarted.sectionRevision(SECOND));
        assertEquals(Map.of(FIRST, 4096L, SECOND, 4096L), restarted.dirtySections());
    }

    @Test
    void uncleanCoverageWithoutAHighWaterReservesTheFirstSegment() throws Exception {
        WorldSectionRevisionIndex crashed =
                new WorldSectionRevisionIndex(temporaryDirectory);
        crashed.abandonWithoutCleanForTest();

        WorldSectionRevisionIndex restarted = new WorldSectionRevisionIndex(
                temporaryDirectory, Set.of(FIRST)
        );

        assertEquals(1, restarted.sectionRevision(FIRST));
        assertEquals(Map.of(FIRST, 1L), restarted.dirtySections());
        assertEquals(
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                Files.readString(
                        temporaryDirectory.resolve("section-revision-state.json"),
                        StandardCharsets.UTF_8
                )
        );
        assertEquals(2, restarted.markMutated(FIRST));
    }

    @Test
    void closeCleanlyRestoresStateWithoutDirtyingPublishedCoverage() {
        WorldSectionRevisionIndex index =
                new WorldSectionRevisionIndex(temporaryDirectory);
        long revision = index.markMutated(FIRST);
        assertTrue(index.clearDirtyIfUnchanged(FIRST, revision));

        index.closeCleanly();

        WorldSectionRevisionIndex restarted = new WorldSectionRevisionIndex(
                temporaryDirectory, Set.of(FIRST, SECOND)
        );
        assertEquals(revision, restarted.sectionRevision(FIRST));
        assertEquals(0, restarted.sectionRevision(SECOND));
        assertTrue(restarted.dirtySections().isEmpty());
    }

    @Test
    void publicDurableOwnerRejectsSecondOpenUntilCloseCleanlyReleasesLease() {
        WorldSectionRevisionIndex first =
                new WorldSectionRevisionIndex(temporaryDirectory);
        AtomicReference<WorldSectionRevisionIndex> unexpectedSecond =
                new AtomicReference<>();
        boolean firstClosed = false;
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedSecond.set(
                            new WorldSectionRevisionIndex(temporaryDirectory)
                    )
            );

            first.closeCleanly();
            firstClosed = true;
            WorldSectionRevisionIndex reopened =
                    new WorldSectionRevisionIndex(temporaryDirectory);
            reopened.closeCleanly();
        } finally {
            WorldSectionRevisionIndex second = unexpectedSecond.get();
            if (second != null) {
                second.closeCleanly();
            }
            if (!firstClosed) {
                first.closeCleanly();
            }
        }
    }

    @Test
    void closeCleanlyRejectsFurtherMutationClearAndSave() throws Exception {
        WorldSectionRevisionIndex index =
                new WorldSectionRevisionIndex(temporaryDirectory);
        long revision = index.markMutated(FIRST);

        index.closeCleanly();
        index.closeCleanly();

        assertAll(
                () -> assertClosed(() -> index.markMutated(FIRST)),
                () -> assertClosed(
                        () -> index.clearDirtyIfUnchanged(FIRST, revision)
                ),
                () -> assertClosed(index::saveState),
                () -> assertTrue(Files.readString(
                        temporaryDirectory.resolve(
                                "section-revision-snapshot.json"
                        ),
                        StandardCharsets.UTF_8
                ).contains("\"cleanShutdown\":true"))
        );
    }

    @Test
    void publishedCoverageAboveTheHardLimitFailsBeforeCopying() {
        AtomicBoolean iterated = new AtomicBoolean();
        Set<WorldSectionKey> oversizedCoverage = new AbstractSet<>() {
            @Override
            public Iterator<WorldSectionKey> iterator() {
                iterated.set(true);
                throw new AssertionError("Oversized coverage must not be copied");
            }

            @Override
            public int size() {
                return 2;
            }
        };
        WorldSectionRevisionLimits limits = new WorldSectionRevisionLimits(
                1_024, 1, 1, 1
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldSectionRevisionIndex(
                        temporaryDirectory,
                        oversizedCoverage,
                        NioSnapshotFileAccess.INSTANCE,
                        limits
                )
        );

        assertAll(
                () -> assertEquals(
                        "Published coverage exceeds its hard limit",
                        failure.getMessage()
                ),
                () -> assertFalse(iterated.get()),
                () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                        "section-revision-owner.lock"
                ))),
                () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                        "section-revision-snapshot.json"
                )))
        );
    }

    @Test
    void publishedCoverageDimensionQuotaFailsBeforeCreatingStateDirectory() {
        Path stateDirectory = temporaryDirectory.resolve("state");
        WorldSectionKey otherDimension = new WorldSectionKey(
                NamespacedId.parse("minecraft:the_nether"), 4, 5, 6
        );
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            stateDirectory,
                            Set.of(FIRST, otherDimension),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(1_024, 1, 2, 2)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Published coverage dimension quota is exhausted",
                            failure.getMessage()
                    ),
                    () -> assertFalse(Files.exists(stateDirectory))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void newDimensionAboveTheHardLimitFailsBeforeChangingState() throws Exception {
        WorldSectionKey otherDimension = new WorldSectionKey(
                NamespacedId.parse("minecraft:the_nether"), 4, 5, 6
        );
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex(
                temporaryDirectory,
                Set.of(),
                NioSnapshotFileAccess.INSTANCE,
                new WorldSectionRevisionLimits(1_024, 1, 2, 2)
        );
        long firstRevision = index.markMutated(FIRST);
        byte[] highWaterBefore = Files.readAllBytes(
                temporaryDirectory.resolve("section-revision-state.json")
        );
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> index.markMutated(otherDimension)
            );

            assertAll(
                    () -> assertEquals(
                            "World section dimension quota is exhausted",
                            failure.getMessage()
                    ),
                    () -> assertEquals(0, index.worldMutationEpoch(
                            otherDimension.dimension()
                    )),
                    () -> assertEquals(0, index.sectionRevision(otherDimension)),
                    () -> assertEquals(
                            Map.of(FIRST, firstRevision), index.dirtySections()
                    ),
                    () -> assertArrayEquals(
                            highWaterBefore,
                            Files.readAllBytes(temporaryDirectory.resolve(
                                    "section-revision-state.json"
                            ))
                    )
            );
        } finally {
            index.abandonWithoutCleanForTest();
        }
    }

    @Test
    void newSectionAboveTheHardLimitFailsBeforeChangingState() throws Exception {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex(
                temporaryDirectory,
                Set.of(),
                NioSnapshotFileAccess.INSTANCE,
                new WorldSectionRevisionLimits(1_024, 1, 1, 2)
        );
        long firstRevision = index.markMutated(FIRST);
        byte[] highWaterBefore = Files.readAllBytes(
                temporaryDirectory.resolve("section-revision-state.json")
        );
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> index.markMutated(SECOND)
            );

            assertAll(
                    () -> assertEquals(
                            "World section quota is exhausted",
                            failure.getMessage()
                    ),
                    () -> assertEquals(
                            firstRevision, index.worldMutationEpoch(DIMENSION)
                    ),
                    () -> assertEquals(
                            firstRevision, index.sectionRevision(FIRST)
                    ),
                    () -> assertEquals(0, index.sectionRevision(SECOND)),
                    () -> assertEquals(
                            Map.of(FIRST, firstRevision), index.dirtySections()
                    ),
                    () -> assertArrayEquals(
                            highWaterBefore,
                            Files.readAllBytes(temporaryDirectory.resolve(
                                    "section-revision-state.json"
                            ))
                    )
            );
        } finally {
            index.abandonWithoutCleanForTest();
        }
    }

    @Test
    void dirtyQuotaRejectsAtomicallyAndClearReleasesItsSlot() throws Exception {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex(
                temporaryDirectory,
                Set.of(),
                NioSnapshotFileAccess.INSTANCE,
                new WorldSectionRevisionLimits(1_024, 1, 2, 1)
        );
        long firstRevision = index.markMutated(FIRST);
        byte[] highWaterBefore = Files.readAllBytes(
                temporaryDirectory.resolve("section-revision-state.json")
        );
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> index.markMutated(SECOND)
            );

            assertAll(
                    () -> assertEquals(
                            "World section dirty quota is exhausted",
                            failure.getMessage()
                    ),
                    () -> assertEquals(
                            firstRevision, index.worldMutationEpoch(DIMENSION)
                    ),
                    () -> assertEquals(0, index.sectionRevision(SECOND)),
                    () -> assertEquals(
                            Map.of(FIRST, firstRevision), index.dirtySections()
                    ),
                    () -> assertArrayEquals(
                            highWaterBefore,
                            Files.readAllBytes(temporaryDirectory.resolve(
                                    "section-revision-state.json"
                            ))
                    )
            );

            assertTrue(index.clearDirtyIfUnchanged(FIRST, firstRevision));
            long secondRevision = index.markMutated(SECOND);

            assertAll(
                    () -> assertEquals(firstRevision + 1, secondRevision),
                    () -> assertEquals(
                            secondRevision, index.sectionRevision(SECOND)
                    ),
                    () -> assertEquals(
                            Map.of(SECOND, secondRevision), index.dirtySections()
                    )
            );
        } finally {
            index.abandonWithoutCleanForTest();
        }
    }

    @Test
    void declaredSectionCountAboveTheHardLimitFailsBeforeSectionAllocation()
            throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{},\"cleanShutdown\":true,"
                        + "\"dimensionCount\":\"0\",\"dirty\":[],"
                        + "\"dirtyCount\":\"0\",\"sectionCount\":\"262145\","
                        + "\"sections\":[]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot section count exceeds its hard limit",
                failure.getCause().getMessage()
        );
    }

    @Test
    void declaredDirtyCountAboveTheHardLimitFailsBeforeDirtyAllocation()
            throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{},\"cleanShutdown\":true,"
                        + "\"dimensionCount\":\"0\",\"dirty\":[],"
                        + "\"dirtyCount\":\"262145\",\"sectionCount\":\"0\","
                        + "\"sections\":[]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot dirty count exceeds its hard limit",
                failure.getCause().getMessage()
        );
    }

    @Test
    void declaredDimensionCountAboveTheHardLimitFailsBeforeEpochAllocation()
            throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{},\"cleanShutdown\":true,"
                        + "\"dimensionCount\":\"1025\",\"dirty\":[],"
                        + "\"dirtyCount\":\"0\",\"sectionCount\":\"0\","
                        + "\"sections\":[]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot dimension count exceeds its hard limit",
                failure.getCause().getMessage()
        );
    }

    @Test
    void malformedSnapshotParserFailureUsesTheStablePersistenceExceptionShape()
            throws Exception {
        Files.write(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                new byte[0]
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals("Unable to open section revision state", failure.getMessage());
        IOException formatFailure = assertInstanceOf(
                IOException.class, failure.getCause()
        );
        assertEquals(
                "Section revision snapshot format is invalid",
                formatFailure.getMessage()
        );
        assertInstanceOf(CanonicalJsonException.class, formatFailure.getCause());
    }

    @Test
    void snapshotAboveTheByteHardLimitFailsBeforeReadingItsContents() throws Exception {
        Path snapshot = temporaryDirectory.resolve("section-revision-snapshot.json");
        try (RandomAccessFile file = new RandomAccessFile(snapshot.toFile(), "rw")) {
            file.setLength(64L * 1024 * 1024 + 1);
        }

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot exceeds its byte hard limit",
                failure.getCause().getMessage()
        );
    }

    @Test
    void highWaterStateAboveTheByteHardLimitFailsBeforeReadingItsContents()
            throws Exception {
        Path state = temporaryDirectory.resolve("section-revision-state.json");
        try (RandomAccessFile file = new RandomAccessFile(state.toFile(), "rw")) {
            file.setLength(1024L * 1024 + 1);
        }

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision state exceeds its byte hard limit",
                failure.getCause().getMessage()
        );
    }

    @Test
    void malformedHighWaterParserFailureUsesTheStablePersistenceExceptionShape()
            throws Exception {
        Files.write(
                temporaryDirectory.resolve("section-revision-state.json"),
                new byte[0]
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals("Unable to open section revision state", failure.getMessage());
        IOException formatFailure = assertInstanceOf(
                IOException.class, failure.getCause()
        );
        assertEquals(
                "Section revision state format is invalid",
                formatFailure.getMessage()
        );
        assertInstanceOf(CanonicalJsonException.class, formatFailure.getCause());
    }

    @Test
    void oversizedHighWaterTemporaryFailsClosedInsteadOfBeingIgnored()
            throws Exception {
        Path state = temporaryDirectory.resolve("section-revision-state.json");
        Files.writeString(
                state,
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        Path temporary = temporaryDirectory.resolve(
                "section-revision-state.json.oversized.tmp"
        );
        try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) {
            file.setLength(1024L * 1024 + 1);
        }
        byte[] stateBefore = Files.readAllBytes(state);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertAll(
                () -> assertEquals(
                        "Section revision state exceeds its byte hard limit",
                        failure.getCause().getMessage()
                ),
                () -> assertArrayEquals(stateBefore, Files.readAllBytes(state)),
                () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                        "section-revision-snapshot.json"
                )))
        );
    }

    @Test
    void tooManyHighWaterTemporariesFailBeforeCollectionOrPersistence()
            throws Exception {
        Path state = temporaryDirectory.resolve("section-revision-state.json");
        Files.writeString(
                state,
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        byte[] stateBefore = Files.readAllBytes(state);
        for (int index = 0; index < 129; index++) {
            Files.write(
                    temporaryDirectory.resolve(
                            "section-revision-state.json." + index + ".tmp"
                    ),
                    new byte[0]
            );
        }

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertAll(
                () -> assertEquals(
                        "Section revision state temporary file count exceeds its hard limit",
                        failure.getCause().getMessage()
                ),
                () -> assertArrayEquals(stateBefore, Files.readAllBytes(state)),
                () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                        "section-revision-snapshot.json"
                )))
        );
    }

    @Test
    void mergedHighWaterCandidatesRespectTheDimensionQuotaBeforePersistence()
            throws Exception {
        Path state = temporaryDirectory.resolve("section-revision-state.json");
        Files.writeString(
                state,
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                temporaryDirectory.resolve(
                        "section-revision-state.json.crashed-after-fsync.tmp"
                ),
                "{\"dimensions\":{\"minecraft:the_nether\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        byte[] stateBefore = Files.readAllBytes(state);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(1_024, 1, 1, 1)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision state dimension quota is exhausted",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(stateBefore, Files.readAllBytes(state)),
                    () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                            "section-revision-snapshot.json"
                    )))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void highWaterStateDimensionQuotaFailsBeforeSnapshotPersistence()
            throws Exception {
        Path state = temporaryDirectory.resolve("section-revision-state.json");
        Files.writeString(
                state,
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\","
                        + "\"minecraft:the_nether\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        byte[] stateBefore = Files.readAllBytes(state);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(1_024, 1, 1, 1)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision state dimension quota is exhausted",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(stateBefore, Files.readAllBytes(state)),
                    () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                            "section-revision-snapshot.json"
                    )))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void recoveryUnionDimensionQuotaFailsBeforeAnyStatePersistence()
            throws Exception {
        Path snapshot = temporaryDirectory.resolve("section-revision-snapshot.json");
        Files.writeString(
                snapshot,
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":false,\"dimensionCount\":\"1\","
                        + "\"dirty\":[],\"dirtyCount\":\"0\","
                        + "\"sectionCount\":\"1\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,"
                        + "\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );
        WorldSectionKey nether = new WorldSectionKey(
                NamespacedId.parse("minecraft:the_nether"), 4, 5, 6
        );
        byte[] snapshotBefore = Files.readAllBytes(snapshot);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(nether),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(4_096, 1, 2, 2)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision snapshot dimension quota is exhausted",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(
                            snapshotBefore, Files.readAllBytes(snapshot)
                    ),
                    () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                            "section-revision-state.json"
                    )))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void recoveredTemporaryIsNotPersistedBeforeRecoveryUnionValidation()
            throws Exception {
        Path state = temporaryDirectory.resolve("section-revision-state.json");
        Files.writeString(
                state,
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                temporaryDirectory.resolve(
                        "section-revision-state.json.crashed-after-fsync.tmp"
                ),
                "{\"dimensions\":{\"minecraft:the_nether\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        Path snapshot = temporaryDirectory.resolve("section-revision-snapshot.json");
        Files.writeString(
                snapshot,
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":false,\"dimensionCount\":\"1\","
                        + "\"dirty\":[],\"dirtyCount\":\"0\","
                        + "\"sectionCount\":\"1\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,"
                        + "\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );
        WorldSectionKey end = new WorldSectionKey(
                NamespacedId.parse("minecraft:the_end"), 4, 5, 6
        );
        byte[] stateBefore = Files.readAllBytes(state);
        byte[] snapshotBefore = Files.readAllBytes(snapshot);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(end),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(4_096, 2, 2, 2)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision snapshot dimension quota is exhausted",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(stateBefore, Files.readAllBytes(state)),
                    () -> assertArrayEquals(
                            snapshotBefore, Files.readAllBytes(snapshot)
                    )
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void recoveryUnionSectionQuotaFailsBeforeAnyStatePersistence()
            throws Exception {
        Path snapshot = temporaryDirectory.resolve("section-revision-snapshot.json");
        Files.writeString(
                snapshot,
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":false,\"dimensionCount\":\"1\","
                        + "\"dirty\":[],\"dirtyCount\":\"0\","
                        + "\"sectionCount\":\"1\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,"
                        + "\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );
        byte[] snapshotBefore = Files.readAllBytes(snapshot);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(SECOND),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(4_096, 1, 1, 2)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision snapshot section quota is exhausted",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(
                            snapshotBefore, Files.readAllBytes(snapshot)
                    ),
                    () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                            "section-revision-state.json"
                    )))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void recoveryUnionDirtyQuotaFailsBeforeAnyStatePersistence()
            throws Exception {
        Path snapshot = temporaryDirectory.resolve("section-revision-snapshot.json");
        Files.writeString(
                snapshot,
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":false,\"dimensionCount\":\"1\","
                        + "\"dirty\":[{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,"
                        + "\"sectionY\":2,\"sectionZ\":3}],"
                        + "\"dirtyCount\":\"1\",\"sectionCount\":\"1\","
                        + "\"sections\":[{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,"
                        + "\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );
        byte[] snapshotBefore = Files.readAllBytes(snapshot);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(SECOND),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(4_096, 1, 2, 1)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision snapshot dirty quota is exhausted",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(
                            snapshotBefore, Files.readAllBytes(snapshot)
                    ),
                    () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                            "section-revision-state.json"
                    )))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void recoverySnapshotByteQuotaFailsBeforeAnyStatePersistence()
            throws Exception {
        Path snapshot = temporaryDirectory.resolve("section-revision-snapshot.json");
        Files.writeString(
                snapshot,
                "{\"actualEpochs\":{},\"cleanShutdown\":false,"
                        + "\"dimensionCount\":\"0\",\"dirty\":[],"
                        + "\"dirtyCount\":\"0\",\"sectionCount\":\"0\","
                        + "\"sections\":[]}",
                StandardCharsets.UTF_8
        );
        byte[] snapshotBefore = Files.readAllBytes(snapshot);
        AtomicReference<WorldSectionRevisionIndex> unexpectedIndex =
                new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> unexpectedIndex.set(new WorldSectionRevisionIndex(
                            temporaryDirectory,
                            Set.of(),
                            NioSnapshotFileAccess.INSTANCE,
                            new WorldSectionRevisionLimits(122, 1, 1, 1)
                    ))
            );

            assertAll(
                    () -> assertEquals(
                            "Section revision snapshot exceeds its byte hard limit",
                            failure.getCause().getMessage()
                    ),
                    () -> assertArrayEquals(
                            snapshotBefore, Files.readAllBytes(snapshot)
                    ),
                    () -> assertFalse(Files.exists(temporaryDirectory.resolve(
                            "section-revision-state.json"
                    )))
            );
        } finally {
            WorldSectionRevisionIndex index = unexpectedIndex.get();
            if (index != null) {
                index.abandonWithoutCleanForTest();
            }
        }
    }

    @Test
    void snapshotAccessUsesOneBoundedHandleAndAlwaysClosesIt() {
        byte[] snapshot = ("{\"actualEpochs\":{},\"cleanShutdown\":true,"
                + "\"dimensionCount\":\"0\",\"dirty\":[],\"dirtyCount\":\"0\","
                + "\"sectionCount\":\"0\",\"sections\":[]}")
                .getBytes(StandardCharsets.UTF_8);
        TrackingSnapshotReadHandle readable =
                new TrackingSnapshotReadHandle(snapshot.length, snapshot);
        TrackingSnapshotFileAccess readableAccess =
                new TrackingSnapshotFileAccess(readable);

        WorldSectionRevisionIndex readableIndex = new WorldSectionRevisionIndex(
                temporaryDirectory, Set.of(), readableAccess
        );

        assertEquals(1, readableAccess.openCalls);
        assertEquals(
                temporaryDirectory.resolve("section-revision-snapshot.json")
                        .toAbsolutePath().normalize(),
                readableAccess.openedPath
        );
        assertEquals(1, readable.sizeCalls);
        assertEquals(List.of(snapshot.length, 1), readable.readRequests);
        assertEquals(1, readable.closeCalls);
        readableIndex.abandonWithoutCleanForTest();

        TrackingSnapshotReadHandle oversized = new TrackingSnapshotReadHandle(
                64L * 1024 * 1024 + 1, new byte[0]
        );
        TrackingSnapshotFileAccess oversizedAccess =
                new TrackingSnapshotFileAccess(oversized);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(
                        temporaryDirectory, Set.of(), oversizedAccess
                )
        );

        assertEquals(
                "Section revision snapshot exceeds its byte hard limit",
                failure.getCause().getMessage()
        );
        assertEquals(1, oversizedAccess.openCalls);
        assertEquals(1, oversized.sizeCalls);
        assertTrue(oversized.readRequests.isEmpty());
        assertEquals(1, oversized.closeCalls);
    }

    @Test
    void duplicateSectionEntriesFailClosed() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":true,\"dimensionCount\":\"1\","
                        + "\"dirty\":[],\"dirtyCount\":\"0\","
                        + "\"sectionCount\":\"2\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\",\"revision\":\"1\","
                        + "\"sectionX\":1,\"sectionY\":2,\"sectionZ\":3},"
                        + "{\"dimension\":\"minecraft:overworld\",\"revision\":\"1\","
                        + "\"sectionX\":1,\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot entry is duplicated",
                failure.getCause().getMessage()
        );
    }

    @Test
    void stringEncodedSectionCoordinateFailsClosed() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":true,\"dimensionCount\":\"1\","
                        + "\"dirty\":[],\"dirtyCount\":\"0\","
                        + "\"sectionCount\":\"1\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\",\"revision\":\"1\","
                        + "\"sectionX\":\"1\",\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot coordinate is invalid",
                failure.getCause().getMessage()
        );
    }

    @Test
    void actualEpochCannotPrecedeASectionRevision() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":true,\"dimensionCount\":\"1\","
                        + "\"dirty\":[],\"dirtyCount\":\"0\","
                        + "\"sectionCount\":\"1\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\",\"revision\":\"2\","
                        + "\"sectionX\":1,\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot epoch precedes a section revision",
                failure.getCause().getMessage()
        );
    }

    @Test
    void dirtyEntryMissingFromSectionsFailsClosed() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{\"minecraft:overworld\":\"1\"},"
                        + "\"cleanShutdown\":true,\"dimensionCount\":\"1\","
                        + "\"dirty\":[{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,\"sectionY\":2,"
                        + "\"sectionZ\":3}],\"dirtyCount\":\"1\","
                        + "\"sectionCount\":\"0\",\"sections\":[]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot dirty entry is missing from sections",
                failure.getCause().getMessage()
        );
    }

    @Test
    void dirtyRevisionMustMatchItsSectionRevision() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-snapshot.json"),
                "{\"actualEpochs\":{\"minecraft:overworld\":\"2\"},"
                        + "\"cleanShutdown\":true,\"dimensionCount\":\"1\","
                        + "\"dirty\":[{\"dimension\":\"minecraft:overworld\","
                        + "\"revision\":\"1\",\"sectionX\":1,\"sectionY\":2,"
                        + "\"sectionZ\":3}],\"dirtyCount\":\"1\","
                        + "\"sectionCount\":\"1\",\"sections\":["
                        + "{\"dimension\":\"minecraft:overworld\",\"revision\":\"2\","
                        + "\"sectionX\":1,\"sectionY\":2,\"sectionZ\":3}]}",
                StandardCharsets.UTF_8
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new WorldSectionRevisionIndex(temporaryDirectory)
        );

        assertEquals(
                "Section revision snapshot dirty revision does not match its section revision",
                failure.getCause().getMessage()
        );
    }

    @Test
    void missingSnapshotIsAFirstStartAndDoesNotDirtyPublishedCoverage() {
        WorldSectionRevisionIndex firstStart = new WorldSectionRevisionIndex(
                temporaryDirectory, Set.of(FIRST, SECOND)
        );

        assertEquals(0, firstStart.sectionRevision(FIRST));
        assertEquals(0, firstStart.sectionRevision(SECOND));
        assertTrue(firstStart.dirtySections().isEmpty());
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("section-revision-snapshot.json")
        ));
    }

    @Test
    void saveStateWaitsForAnInFlightMutationBeforeTakingItsSnapshot() throws Exception {
        WorldSectionRevisionIndex index =
                new WorldSectionRevisionIndex(temporaryDirectory);
        BlockingSectionMap sections = new BlockingSectionMap();
        replaceMap(index, "sectionRevisions", sections);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Long> mutation = executor.submit(() -> index.markMutated(FIRST));
        try {
            assertTrue(sections.putEntered.await(5, TimeUnit.SECONDS));
            Future<?> saving = executor.submit(index::saveState);

            assertThrows(
                    TimeoutException.class,
                    () -> saving.get(200, TimeUnit.MILLISECONDS)
            );
            sections.releasePut.countDown();

            assertEquals(1, mutation.get(5, TimeUnit.SECONDS));
            saving.get(5, TimeUnit.SECONDS);
            index.abandonWithoutCleanForTest();
            WorldSectionRevisionIndex restarted =
                    new WorldSectionRevisionIndex(temporaryDirectory);
            assertEquals(1, restarted.sectionRevision(FIRST));
            assertEquals(Map.of(FIRST, 1L), restarted.dirtySections());
        } finally {
            sections.releasePut.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void exhaustionFailsInsteadOfWrapping() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex(
                Map.of(DIMENSION, Long.MAX_VALUE), Map.of()
        );

        assertThrows(IllegalStateException.class, () -> index.markMutated(FIRST));
    }

    @Test
    void durableAllocationReserves4096BeforeUseAndSkipsTheRemainderAfterRestart()
            throws Exception {
        WorldSectionRevisionIndex first = new WorldSectionRevisionIndex(temporaryDirectory);

        assertEquals(1, first.markMutated(FIRST));
        String firstState = Files.readString(
                temporaryDirectory.resolve("section-revision-state.json"),
                StandardCharsets.UTF_8
        );
        assertEquals(
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                firstState
        );
        first.abandonWithoutCleanForTest();

        WorldSectionRevisionIndex restarted =
                new WorldSectionRevisionIndex(temporaryDirectory);
        assertEquals(4097, restarted.markMutated(FIRST));
        String restartedState = Files.readString(
                temporaryDirectory.resolve("section-revision-state.json"),
                StandardCharsets.UTF_8
        );
        assertEquals(
                "{\"dimensions\":{\"minecraft:overworld\":\"8192\"}}",
                restartedState
        );
    }

    @Test
    void restartConsumesACompleteHighWaterTemporaryLeftAfterItsFsync()
            throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-state.json"),
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                temporaryDirectory.resolve(
                        "section-revision-state.json.crashed-after-fsync.tmp"
                ),
                "{\"dimensions\":{\"minecraft:overworld\":\"8192\"}}",
                StandardCharsets.UTF_8
        );

        WorldSectionRevisionIndex restarted =
                new WorldSectionRevisionIndex(temporaryDirectory);

        assertEquals(8193, restarted.markMutated(FIRST));
        assertEquals(
                "{\"dimensions\":{\"minecraft:overworld\":\"12288\"}}",
                Files.readString(
                        temporaryDirectory.resolve("section-revision-state.json"),
                        StandardCharsets.UTF_8
                )
        );
    }

    @Test
    void durableInstancesReserveDisjointSegmentsEvenWhenConstructedBeforeFirstUse() {
        WorldSectionRevisionIndex first =
                WorldSectionRevisionIndex.allocatorOnly(temporaryDirectory);
        WorldSectionRevisionIndex second =
                WorldSectionRevisionIndex.allocatorOnly(temporaryDirectory);

        assertEquals(1, first.markMutated(FIRST));
        assertEquals(4097, second.markMutated(SECOND));
        assertEquals(2, first.markMutated(SECOND));
    }

    @Test
    void constructorRecoveryUsesTheSamePersistenceLockAsSegmentReservation()
            throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("section-revision-state.json"),
                "{\"dimensions\":{\"minecraft:overworld\":\"4096\"}}",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                temporaryDirectory.resolve("section-revision-state.json.fsynced.tmp"),
                "{\"dimensions\":{\"minecraft:overworld\":\"8192\"}}",
                StandardCharsets.UTF_8
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        Future<WorldSectionRevisionIndex> recovery;
        try (WorldSectionRevisionIndex.PersistenceLockHandle ignored =
                     WorldSectionRevisionIndex.holdPersistenceLockForTest(
                             temporaryDirectory
                     )) {
            recovery = executor.submit(() -> {
                started.countDown();
                return new WorldSectionRevisionIndex(temporaryDirectory);
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,
                    () -> recovery.get(100, TimeUnit.MILLISECONDS));
        }
        try {
            assertEquals(8193, recovery.get(5, TimeUnit.SECONDS).markMutated(FIRST));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closedStateDirectoriesDoNotRemainInJvmPersistenceLockRegistry()
            throws Exception {
        ConcurrentMap<Path, ?> persistenceLocks = persistenceLocks();
        int baseline = persistenceLocks.size();

        for (int index = 0; index < 70; index++) {
            WorldSectionRevisionIndex revisionIndex =
                    new WorldSectionRevisionIndex(
                            temporaryDirectory.resolve("lock-retention-" + index)
                    );
            revisionIndex.closeCleanly();
        }

        assertEquals(baseline, persistenceLocks.size());
    }

    @Test
    void rejectedDimensionsDoNotGrowTheInstanceLockRegistryWithoutBound()
            throws Exception {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex(
                temporaryDirectory,
                Set.of(),
                NioSnapshotFileAccess.INSTANCE,
                new WorldSectionRevisionLimits(1_024, 1, 1, 1)
        );
        index.markMutated(FIRST);
        try {
            for (int rejected = 0; rejected < 257; rejected++) {
                WorldSectionKey rejectedSection = new WorldSectionKey(
                        NamespacedId.parse(
                                "fpsmatch:rejected_dimension_" + rejected
                        ),
                        rejected,
                        0,
                        0
                );
                IllegalStateException failure = assertThrows(
                        IllegalStateException.class,
                        () -> index.markMutated(rejectedSection)
                );
                assertEquals(
                        "World section dimension quota is exhausted",
                        failure.getMessage()
                );
            }

            Field locksField = WorldSectionRevisionIndex.class
                    .getDeclaredField("locks");
            locksField.setAccessible(true);
            Object[] locks = (Object[]) locksField.get(index);
            assertEquals(64, locks.length);
        } finally {
            index.abandonWithoutCleanForTest();
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<Path, ?> persistenceLocks()
            throws Exception {
        Field field = WorldSectionRevisionIndex.class.getDeclaredField("JVM_STATE_LOCKS");
        field.setAccessible(true);
        return (ConcurrentMap<Path, ?>) field.get(null);
    }

    private static void assertClosed(org.junit.jupiter.api.function.Executable action) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, action
        );
        assertEquals("Section revision state is closed", failure.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static <K, V> void replaceMap(
            WorldSectionRevisionIndex index,
            String fieldName,
            Map<K, V> replacement
    ) throws Exception {
        Field field = WorldSectionRevisionIndex.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(index, replacement);
    }

    private static final class BlockingSectionMap
            extends ConcurrentHashMap<WorldSectionKey, Long> {
        private final CountDownLatch putEntered = new CountDownLatch(1);
        private final CountDownLatch releasePut = new CountDownLatch(1);
        private final AtomicBoolean blockNextPut = new AtomicBoolean(true);

        @Override
        public Long put(WorldSectionKey key, Long value) {
            if (blockNextPut.compareAndSet(true, false)) {
                putEntered.countDown();
                try {
                    if (!releasePut.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("section put timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("section put interrupted", interrupted);
                }
            }
            return super.put(key, value);
        }
    }

    private static final class TrackingSnapshotFileAccess
            implements SnapshotFileAccess {
        private final SnapshotReadHandle handle;
        private int openCalls;
        private Path openedPath;

        private TrackingSnapshotFileAccess(SnapshotReadHandle handle) {
            this.handle = handle;
        }

        @Override
        public SnapshotReadHandle openNoFollow(Path snapshotFile) {
            openCalls++;
            openedPath = snapshotFile;
            return handle;
        }
    }

    private static final class TrackingSnapshotReadHandle
            implements SnapshotReadHandle {
        private final long reportedSize;
        private final byte[] bytes;
        private final List<Integer> readRequests = new ArrayList<>();
        private int offset;
        private int sizeCalls;
        private int closeCalls;

        private TrackingSnapshotReadHandle(long reportedSize, byte[] bytes) {
            this.reportedSize = reportedSize;
            this.bytes = bytes.clone();
        }

        @Override
        public long size() {
            sizeCalls++;
            return reportedSize;
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            readRequests.add(destination.remaining());
            if (offset == bytes.length) {
                return -1;
            }
            int count = Math.min(destination.remaining(), bytes.length - offset);
            destination.put(bytes, offset, count);
            offset += count;
            return count;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }
}
