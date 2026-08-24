package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EditorSessionManager {
    private final MinimapPermissionPolicy permissionPolicy;
    private final Duration idleTtl;
    private final Clock clock;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> actorSessions = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private final Set<OpenAttempt> pendingOpens = new HashSet<>();

    public EditorSessionManager(
            MinimapPermissionPolicy permissionPolicy,
            MinimapServerConfigView config,
            Clock clock
    ) {
        this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy");
        Objects.requireNonNull(config, "config");
        this.idleTtl = Objects.requireNonNull(
                config.editorSessionIdleTtl(), "editorSessionIdleTtl"
        );
        if (idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("Editor session TTL must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public EditorSession open(
            UUID actorId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            UUID draftId,
            long baseRevision
    ) {
        Objects.requireNonNull(documentId, "documentId");
        OpenAttempt attempt = new OpenAttempt(actorId, mapKey);
        synchronized (lifecycleLock) {
            pendingOpens.add(attempt);
        }
        try {
            requireAllowed(actorId, mapKey, MinimapAction.OPEN_EDITOR);
            EditorSession session = new EditorSession(
                    UUID.randomUUID(),
                    actorId,
                    mapKey,
                    dimension,
                    documentId,
                    draftId,
                    baseRevision,
                    nextExpiry()
            );
            synchronized (lifecycleLock) {
                if (attempt.invalidated) {
                    throw failure(
                            MinimapErrorCode.SESSION_NOT_FOUND,
                            "Editor session scope was invalidated while opening"
                    );
                }
                UUID previousSessionId = actorSessions.get(actorId);
                sessions.put(session.sessionId(), session);
                actorSessions.put(actorId, session.sessionId());
                if (previousSessionId != null) {
                    sessions.remove(previousSessionId);
                }
            }
            return session;
        } finally {
            synchronized (lifecycleLock) {
                pendingOpens.remove(attempt);
            }
        }
    }

    public EditorSession authorize(
            UUID actorId,
            UUID sessionId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            UUID draftId,
            long baseRevision,
            MinimapAction action
    ) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(draftId, "draftId");
        if (baseRevision < 0) {
            throw new IllegalArgumentException(
                    "Editor session base revision must be non-negative"
            );
        }
        return authorizeInternal(
                actorId, sessionId, mapKey, dimension,
                documentId, draftId, baseRevision, action
        );
    }

    private EditorSession authorizeInternal(
            UUID actorId,
            UUID sessionId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId expectedDocumentId,
            UUID expectedDraftId,
            long expectedBaseRevision,
            MinimapAction action
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(action, "action");
        EditorSession session = sessions.get(sessionId);
        if (session == null || !session.actorId().equals(actorId)
                || !sessionId.equals(actorSessions.get(actorId))) {
            throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Editor session was not found");
        }
        Instant now = clock.instant();
        if (!now.isBefore(session.expiresAt())) {
            invalidateSession(sessionId);
            throw failure(MinimapErrorCode.SESSION_EXPIRED, "Editor session has expired");
        }
        if (!matchesScope(
                session, mapKey, dimension, expectedDocumentId,
                expectedDraftId, expectedBaseRevision
        )) {
            invalidateSession(sessionId);
            throw failure(MinimapErrorCode.SCOPE_MISMATCH, "Editor session scope changed");
        }
        try {
            requireAllowed(actorId, mapKey, action);
        } catch (SessionAccessException denied) {
            invalidateSession(sessionId);
            throw denied;
        }
        synchronized (lifecycleLock) {
            EditorSession current = sessions.get(sessionId);
            if (current == null || !current.actorId().equals(actorId)
                    || !sessionId.equals(actorSessions.get(actorId))) {
                throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Editor session was not found");
            }
            Instant renewalTime = clock.instant();
            if (!renewalTime.isBefore(current.expiresAt())) {
                removeSessionLocked(sessionId, current);
                throw failure(MinimapErrorCode.SESSION_EXPIRED, "Editor session has expired");
            }
            if (!matchesScope(
                    current, mapKey, dimension,
                    expectedDocumentId, expectedDraftId, expectedBaseRevision
            )) {
                removeSessionLocked(sessionId, current);
                throw failure(MinimapErrorCode.SCOPE_MISMATCH, "Editor session scope changed");
            }
            EditorSession renewed = current.renew(renewalTime.plus(idleTtl));
            sessions.put(sessionId, renewed);
            return renewed;
        }
    }

    private static boolean matchesScope(
            EditorSession session,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId expectedDocumentId,
            UUID expectedDraftId,
            long expectedBaseRevision
    ) {
        return session.mapKey().equals(mapKey)
                && session.dimension().equals(dimension)
                && (expectedDocumentId == null
                || session.documentId().equals(expectedDocumentId))
                && (expectedDraftId == null
                || session.draftId().equals(expectedDraftId)
                && session.baseRevision() == expectedBaseRevision);
    }

    public void invalidateActor(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        synchronized (lifecycleLock) {
            pendingOpens.stream()
                    .filter(attempt -> actorId.equals(attempt.actorId))
                    .forEach(attempt -> attempt.invalidated = true);
            actorSessions.remove(actorId);
            sessions.entrySet().removeIf(entry -> entry.getValue().actorId().equals(actorId));
        }
    }

    public void close(
            UUID actorId,
            UUID sessionId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            UUID draftId,
            long baseRevision,
            MinimapAction action
    ) {
        EditorSession authorized = authorize(
                actorId, sessionId, mapKey, dimension, documentId,
                draftId, baseRevision, action
        );
        synchronized (lifecycleLock) {
            removeSessionLocked(sessionId, authorized);
        }
    }

    public void invalidateMap(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        synchronized (lifecycleLock) {
            pendingOpens.stream()
                    .filter(attempt -> mapKey.equals(attempt.mapKey))
                    .forEach(attempt -> attempt.invalidated = true);
            sessions.entrySet().removeIf(entry -> {
                EditorSession session = entry.getValue();
                if (!session.mapKey().equals(mapKey)) {
                    return false;
                }
                actorSessions.remove(session.actorId(), entry.getKey());
                return true;
            });
        }
    }

    public void invalidateAll() {
        synchronized (lifecycleLock) {
            pendingOpens.forEach(attempt -> attempt.invalidated = true);
            sessions.clear();
            actorSessions.clear();
        }
    }

    public int removeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        synchronized (lifecycleLock) {
            for (Map.Entry<UUID, EditorSession> entry : sessions.entrySet()) {
                EditorSession session = entry.getValue();
                if (!now.isBefore(session.expiresAt())
                        && sessions.remove(entry.getKey(), session)) {
                    actorSessions.remove(session.actorId(), entry.getKey());
                    removed++;
                }
            }
        }
        return removed;
    }

    private void invalidateSession(UUID sessionId) {
        synchronized (lifecycleLock) {
            EditorSession session = sessions.get(sessionId);
            if (session != null) {
                removeSessionLocked(sessionId, session);
            }
        }
    }

    private void removeSessionLocked(UUID sessionId, EditorSession session) {
        if (sessions.remove(sessionId, session)) {
            actorSessions.remove(session.actorId(), sessionId);
        }
    }

    private Instant nextExpiry() {
        return clock.instant().plus(idleTtl);
    }

    private void requireAllowed(UUID actorId, MapKey mapKey, MinimapAction action) {
        boolean allowed;
        try {
            allowed = permissionPolicy.mayPerform(actorId, mapKey, action)
                    .orElse(false);
        } catch (RuntimeException policyFailure) {
            allowed = false;
        }
        if (!allowed) {
            throw failure(MinimapErrorCode.UNAUTHORIZED, "Minimap editor action denied");
        }
    }

    private static SessionAccessException failure(
            MinimapErrorCode errorCode,
            String message
    ) {
        return new SessionAccessException(errorCode, message);
    }

    private static final class OpenAttempt {
        private final UUID actorId;
        private final MapKey mapKey;
        private boolean invalidated;

        private OpenAttempt(UUID actorId, MapKey mapKey) {
            this.actorId = actorId;
            this.mapKey = mapKey;
        }
    }
}
