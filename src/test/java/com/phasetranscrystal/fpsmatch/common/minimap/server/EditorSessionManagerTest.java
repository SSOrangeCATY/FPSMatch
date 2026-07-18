package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorSessionManagerTest {
    private static final MapKey MAP = new MapKey("fpsmatch:test", "Test Map");
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    private static final NamespacedId DOCUMENT = NamespacedId.parse("fpsmatch:test_map");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DRAFT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void everyEditorActionRechecksPermissionAndSuccessfulActionsRenewIdleExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<Boolean> allowed = new AtomicReference<>(true);
        MinimapPermissionPolicy policy = (actor, map, action) -> {
            checks.incrementAndGet();
            return Optional.of(allowed.get());
        };
        EditorSessionManager sessions = new EditorSessionManager(
                policy,
                new FixedServerConfig(Duration.ofMinutes(10)),
                clock
        );

        EditorSession session = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 7
        );
        Instant originalExpiry = session.expiresAt();
        clock.advance(Duration.ofMinutes(9));

        EditorSession renewed = sessions.authorize(
                ACTOR, session.sessionId(), MAP, DIMENSION,
                DOCUMENT, DRAFT, 7, MinimapAction.UPLOAD
        );

        assertEquals(2, checks.get());
        assertEquals(session.sessionId(), renewed.sessionId());
        assertEquals(7, renewed.baseRevision());
        assertEquals(DRAFT, renewed.draftId());
        assertNotEquals(originalExpiry, renewed.expiresAt());

        allowed.set(false);
        SessionAccessException denied = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR, session.sessionId(), MAP, DIMENSION,
                        DOCUMENT, DRAFT, 7, MinimapAction.COMMIT_PUBLISH
                )
        );
        assertEquals(MinimapErrorCode.UNAUTHORIZED, denied.errorCode());
        assertEquals(3, checks.get());

        allowed.set(true);
        SessionAccessException invalidated = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR, session.sessionId(), MAP, DIMENSION,
                        DOCUMENT, DRAFT, 7, MinimapAction.COMMIT_PUBLISH
                )
        );
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, invalidated.errorCode());
        assertEquals(3, checks.get());
    }

    @Test
    void emptyOrThrowingPermissionPoliciesDenyWithoutCreatingASession() {
        EditorSessionManager empty = manager((actor, map, action) -> Optional.empty());
        SessionAccessException emptyDenied = assertThrows(
                SessionAccessException.class,
                () -> empty.open(ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0)
        );
        assertEquals(MinimapErrorCode.UNAUTHORIZED, emptyDenied.errorCode());

        EditorSessionManager throwing = manager((actor, map, action) -> {
            throw new IllegalStateException("permission provider unavailable");
        });
        SessionAccessException throwingDenied = assertThrows(
                SessionAccessException.class,
                () -> throwing.open(ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0)
        );
        assertEquals(MinimapErrorCode.UNAUTHORIZED, throwingDenied.errorCode());
    }

    @Test
    void expiryScopeChangesAndLogoutInvalidateTheSession() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        EditorSessionManager sessions = new EditorSessionManager(
                (actor, map, action) -> Optional.of(true),
                new FixedServerConfig(Duration.ofMinutes(10)),
                clock
        );
        EditorSession scopedSession = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0
        );

        SessionAccessException wrongDimension = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR, scopedSession.sessionId(), MAP,
                        NamespacedId.parse("minecraft:the_nether"),
                        DOCUMENT, DRAFT, 0,
                        MinimapAction.REQUEST_WORLD_SNAPSHOT
                )
        );
        assertEquals(MinimapErrorCode.SCOPE_MISMATCH, wrongDimension.errorCode());

        EditorSession session = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0
        );
        clock.advance(Duration.ofMinutes(10));
        EditorSession expiring = session;
        SessionAccessException expired = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR, expiring.sessionId(), MAP, DIMENSION,
                        DOCUMENT, DRAFT, 0, MinimapAction.SAVE_DRAFT
                )
        );
        assertEquals(MinimapErrorCode.SESSION_EXPIRED, expired.errorCode());

        session = sessions.open(ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0);
        sessions.invalidateActor(ACTOR);
        EditorSession loggedOut = session;
        SessionAccessException missing = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR, loggedOut.sessionId(), MAP, DIMENSION,
                        DOCUMENT, DRAFT, 0, MinimapAction.FETCH_SOURCE
                )
        );
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, missing.errorCode());
    }

    @Test
    void authorizationRequiresTheExactDraftAndBaseRevision() {
        EditorSessionManager sessions = manager(
                (actor, map, action) -> Optional.of(true)
        );
        EditorSession wrongDraftSession = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 7
        );

        SessionAccessException wrongDraft = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR,
                        wrongDraftSession.sessionId(),
                        MAP,
                        DIMENSION,
                        DOCUMENT,
                        UUID.randomUUID(),
                        7,
                        MinimapAction.UPLOAD
                )
        );
        assertEquals(MinimapErrorCode.SCOPE_MISMATCH, wrongDraft.errorCode());

        EditorSession wrongBaseSession = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 7
        );
        SessionAccessException wrongBase = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR,
                        wrongBaseSession.sessionId(),
                        MAP,
                        DIMENSION,
                        DOCUMENT,
                        DRAFT,
                        8,
                        MinimapAction.UPLOAD
                )
        );
        assertEquals(MinimapErrorCode.SCOPE_MISMATCH, wrongBase.errorCode());

        EditorSession exact = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 7
        );
        EditorSession authorized = sessions.authorize(
                ACTOR,
                exact.sessionId(),
                MAP,
                DIMENSION,
                DOCUMENT,
                DRAFT,
                7,
                MinimapAction.UPLOAD
        );
        assertEquals(exact.sessionId(), authorized.sessionId());
    }

    @Test
    void authorizationRequiresTheExactDocumentBinding() {
        EditorSessionManager sessions = manager(
                (actor, map, action) -> Optional.of(true)
        );
        EditorSession session = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 7
        );

        SessionAccessException mismatch = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR,
                        session.sessionId(),
                        MAP,
                        DIMENSION,
                        NamespacedId.parse("fpsmatch:other_map"),
                        DRAFT,
                        7,
                        MinimapAction.UPLOAD
                )
        );

        assertEquals(MinimapErrorCode.SCOPE_MISMATCH, mismatch.errorCode());
    }

    @Test
    void publicAuthorizationApiCannotOmitDocumentDraftOrBaseScope() {
        for (Method method : EditorSessionManager.class.getMethods()) {
            if (method.getName().equals("authorize")) {
                assertEquals(8, method.getParameterCount(), method.toString());
            }
        }
    }

    @Test
    void openingANewSessionReplacesTheActorsPreviousSession() {
        EditorSessionManager sessions = manager(
                (actor, map, action) -> Optional.of(true)
        );
        EditorSession first = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 3
        );
        UUID replacementDraft = UUID.randomUUID();

        EditorSession replacement = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, replacementDraft, 4
        );

        assertNotEquals(first.sessionId(), replacement.sessionId());
        SessionAccessException stale = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR,
                        first.sessionId(),
                        MAP,
                        DIMENSION,
                        DOCUMENT,
                        DRAFT,
                        3,
                        MinimapAction.UPLOAD
                )
        );
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, stale.errorCode());
        assertEquals(
                replacement.sessionId(),
                sessions.authorize(
                        ACTOR,
                        replacement.sessionId(),
                        MAP,
                        DIMENSION,
                        DOCUMENT,
                        replacementDraft,
                        4,
                        MinimapAction.UPLOAD
                ).sessionId()
        );
    }

    @Test
    void expiredSessionsAreSweptAndReleaseTheActorIndex() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        EditorSessionManager sessions = new EditorSessionManager(
                (actor, map, action) -> Optional.of(true),
                new FixedServerConfig(Duration.ofMinutes(10)),
                clock
        );
        EditorSession expired = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 3
        );
        clock.advance(Duration.ofMinutes(10));

        assertEquals(1, sessions.removeExpired());
        SessionAccessException missing = assertThrows(
                SessionAccessException.class,
                () -> sessions.authorize(
                        ACTOR,
                        expired.sessionId(),
                        MAP,
                        DIMENSION,
                        DOCUMENT,
                        DRAFT,
                        3,
                        MinimapAction.UPLOAD
                )
        );
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, missing.errorCode());

        EditorSession replacement = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 4
        );
        assertEquals(
                replacement.sessionId(),
                sessions.authorize(
                        ACTOR,
                        replacement.sessionId(),
                        MAP,
                        DIMENSION,
                        DOCUMENT,
                        DRAFT,
                        4,
                        MinimapAction.UPLOAD
                ).sessionId()
        );
        assertEquals(0, sessions.removeExpired());
    }

    @Test
    void replacementDuringAuthorizationCannotRenewTheOldSession() throws Exception {
        CountDownLatch policyEntered = new CountDownLatch(1);
        CountDownLatch releasePolicy = new CountDownLatch(1);
        MinimapPermissionPolicy policy = (actor, map, action) -> {
            if (action == MinimapAction.UPLOAD) {
                policyEntered.countDown();
                try {
                    if (!releasePolicy.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("permission test timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "permission test interrupted", interrupted
                    );
                }
            }
            return Optional.of(true);
        };
        EditorSessionManager sessions = manager(policy);
        EditorSession old = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 3
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<EditorSession> authorization = executor.submit(
                    () -> sessions.authorize(
                            ACTOR,
                            old.sessionId(),
                            MAP,
                            DIMENSION,
                            DOCUMENT,
                            DRAFT,
                            3,
                            MinimapAction.UPLOAD
                    )
            );
            assertTrue(policyEntered.await(5, TimeUnit.SECONDS));

            UUID replacementDraft = UUID.randomUUID();
            EditorSession replacement = sessions.open(
                    ACTOR, MAP, DIMENSION, DOCUMENT, replacementDraft, 4
            );
            releasePolicy.countDown();

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> authorization.get(5, TimeUnit.SECONDS)
            );
            SessionAccessException missing = assertInstanceOf(
                    SessionAccessException.class, failure.getCause()
            );
            assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, missing.errorCode());
            assertEquals(
                    replacement.sessionId(),
                    sessions.authorize(
                            ACTOR,
                            replacement.sessionId(),
                            MAP,
                            DIMENSION,
                            DOCUMENT,
                            replacementDraft,
                            4,
                            MinimapAction.FETCH_SOURCE
                    ).sessionId()
            );
        } finally {
            releasePolicy.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void actorInvalidationDuringAuthorizationCannotReviveTheSession() throws Exception {
        assertConcurrentInvalidationCannotRevive(sessions -> sessions.invalidateActor(ACTOR));
    }

    @Test
    void mapInvalidationDuringAuthorizationCannotReviveTheSession() throws Exception {
        assertConcurrentInvalidationCannotRevive(sessions -> sessions.invalidateMap(MAP));
    }

    @Test
    void actorInvalidationDuringOpenCannotCreateAStaleSession() throws Exception {
        assertConcurrentOpenInvalidationCannotCreate(sessions -> sessions.invalidateActor(ACTOR));
    }

    @Test
    void mapInvalidationDuringOpenCannotCreateAStaleSession() throws Exception {
        assertConcurrentOpenInvalidationCannotCreate(sessions -> sessions.invalidateMap(MAP));
    }

    @Test
    void concurrentRenewalCannotDefeatScopeMismatchInvalidation() throws Exception {
        BlockingRenewalClock clock = new BlockingRenewalClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        EditorSessionManager sessions = new EditorSessionManager(
                (actor, map, action) -> Optional.of(true),
                new FixedServerConfig(Duration.ofMinutes(10)),
                clock
        );
        EditorSession session = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Thread> mismatchThread = new AtomicReference<>();
        try {
            Future<EditorSession> renewal = executor.submit(() -> sessions.authorize(
                    ACTOR,
                    session.sessionId(),
                    MAP,
                    DIMENSION,
                    DOCUMENT,
                    DRAFT,
                    0,
                    MinimapAction.UPLOAD
            ));
            assertTrue(clock.renewalEntered.await(5, TimeUnit.SECONDS));

            Future<EditorSession> mismatch = executor.submit(() -> {
                mismatchThread.set(Thread.currentThread());
                return sessions.authorize(
                        ACTOR,
                        session.sessionId(),
                        MAP,
                        NamespacedId.parse("minecraft:the_nether"),
                        DOCUMENT,
                        DRAFT,
                        0,
                        MinimapAction.SAVE_DRAFT
                );
            });
            assertTrue(clock.mismatchReadOldSession.await(5, TimeUnit.SECONDS));
            awaitBlocked(mismatchThread);
            clock.releaseRenewal.countDown();

            renewal.get(5, TimeUnit.SECONDS);
            ExecutionException mismatchFailure = assertThrows(
                    ExecutionException.class,
                    () -> mismatch.get(5, TimeUnit.SECONDS)
            );
            SessionAccessException scopeFailure = assertInstanceOf(
                    SessionAccessException.class,
                    mismatchFailure.getCause()
            );
            assertEquals(MinimapErrorCode.SCOPE_MISMATCH, scopeFailure.errorCode());

            SessionAccessException missing = assertThrows(
                    SessionAccessException.class,
                    () -> sessions.authorize(
                            ACTOR,
                            session.sessionId(),
                            MAP,
                            DIMENSION,
                            DOCUMENT,
                            DRAFT,
                            0,
                            MinimapAction.FETCH_SOURCE
                    )
            );
            assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, missing.errorCode());
        } finally {
            clock.releaseRenewal.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitBlocked(AtomicReference<Thread> threadReference)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
            if (thread != null && thread.getState() == Thread.State.BLOCKED) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("scope-mismatch authorization did not block on lifecycle lock");
    }

    private static void assertConcurrentOpenInvalidationCannotCreate(
            Consumer<EditorSessionManager> invalidation
    ) throws Exception {
        CountDownLatch policyEntered = new CountDownLatch(1);
        CountDownLatch releasePolicy = new CountDownLatch(1);
        MinimapPermissionPolicy blockingPolicy = (actor, map, action) -> {
            if (action == MinimapAction.OPEN_EDITOR) {
                policyEntered.countDown();
                try {
                    if (!releasePolicy.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("permission test timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("permission test interrupted", interrupted);
                }
            }
            return Optional.of(true);
        };
        EditorSessionManager sessions = manager(blockingPolicy);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EditorSession> opening = executor.submit(
                    () -> sessions.open(
                            ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0
                    )
            );
            assertTrue(policyEntered.await(5, TimeUnit.SECONDS));

            Future<?> invalidating = executor.submit(() -> invalidation.accept(sessions));
            invalidating.get(1, TimeUnit.SECONDS);
            releasePolicy.countDown();

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> opening.get(5, TimeUnit.SECONDS)
            );
            SessionAccessException rejected = assertInstanceOf(
                    SessionAccessException.class,
                    failure.getCause()
            );
            assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, rejected.errorCode());
        } finally {
            releasePolicy.countDown();
            executor.shutdownNow();
        }
    }

    private static void assertConcurrentInvalidationCannotRevive(
            Consumer<EditorSessionManager> invalidation
    ) throws Exception {
        CountDownLatch policyEntered = new CountDownLatch(1);
        CountDownLatch releasePolicy = new CountDownLatch(1);
        MinimapPermissionPolicy blockingPolicy = (actor, map, action) -> {
            if (action == MinimapAction.UPLOAD) {
                policyEntered.countDown();
                try {
                    if (!releasePolicy.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("permission test timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("permission test interrupted", interrupted);
                }
            }
            return Optional.of(true);
        };
        EditorSessionManager sessions = manager(blockingPolicy);
        EditorSession session = sessions.open(
                ACTOR, MAP, DIMENSION, DOCUMENT, DRAFT, 0
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<EditorSession> authorization = executor.submit(() -> sessions.authorize(
                    ACTOR,
                    session.sessionId(),
                    MAP,
                    DIMENSION,
                    DOCUMENT,
                    DRAFT,
                    0,
                    MinimapAction.UPLOAD
            ));
            assertTrue(policyEntered.await(5, TimeUnit.SECONDS));

            invalidation.accept(sessions);
            releasePolicy.countDown();

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> authorization.get(5, TimeUnit.SECONDS)
            );
            SessionAccessException rejected = assertInstanceOf(
                    SessionAccessException.class,
                    failure.getCause()
            );
            assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, rejected.errorCode());

            SessionAccessException stillMissing = assertThrows(
                    SessionAccessException.class,
                    () -> sessions.authorize(
                            ACTOR,
                            session.sessionId(),
                            MAP,
                            DIMENSION,
                            DOCUMENT,
                            DRAFT,
                            0,
                            MinimapAction.FETCH_SOURCE
                    )
            );
            assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, stillMissing.errorCode());
        } finally {
            releasePolicy.countDown();
            executor.shutdownNow();
        }
    }

    private static EditorSessionManager manager(MinimapPermissionPolicy policy) {
        return new EditorSessionManager(
                policy,
                new FixedServerConfig(Duration.ofMinutes(10)),
                Clock.systemUTC()
        );
    }

    private record FixedServerConfig(Duration editorSessionIdleTtl)
            implements MinimapServerConfigView {
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

    private static final class BlockingRenewalClock extends Clock {
        private final Instant instant;
        private final Instant renewalInstant;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch renewalEntered = new CountDownLatch(1);
        private final CountDownLatch mismatchReadOldSession = new CountDownLatch(1);
        private final CountDownLatch releaseRenewal = new CountDownLatch(1);

        private BlockingRenewalClock(Instant instant) {
            this.instant = instant;
            this.renewalInstant = instant.plus(Duration.ofMinutes(1));
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
            int call = calls.incrementAndGet();
            if (call == 3) {
                renewalEntered.countDown();
                try {
                    if (!releaseRenewal.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("renewal clock timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("renewal clock interrupted", interrupted);
                }
            } else if (call == 4) {
                mismatchReadOldSession.countDown();
            }
            return call == 3 || call >= 5 ? renewalInstant : instant;
        }
    }
}
