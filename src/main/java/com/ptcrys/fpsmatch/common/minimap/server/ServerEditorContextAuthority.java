package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks editor contexts emitted by this server and revalidates their canonical state. */
public final class ServerEditorContextAuthority implements AutoCloseable {
    private final EditorSessionManager sessions;
    private final DraftStore drafts;
    private final MinimapBindingCoordinator bindings;
    private final ConcurrentHashMap<UUID, WireIdentity.EditorContext> active =
            new ConcurrentHashMap<>();

    public ServerEditorContextAuthority(
            EditorSessionManager sessions,
            DraftStore drafts,
            MinimapBindingCoordinator bindings
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    public void activate(UUID actorId, WireIdentity.EditorContext context) {
        active.put(
                Objects.requireNonNull(actorId, "actorId"),
                Objects.requireNonNull(context, "context")
        );
    }

    public boolean matchesActiveEditorContext(
            UUID actorId,
            WireIdentity.EditorContext candidate
    ) {
        if (actorId == null || candidate == null) {
            return false;
        }
        WireIdentity.EditorContext expected = active.get(actorId);
        if (expected == null || !sameStableFields(expected, candidate)) {
            return false;
        }

        DraftState draft;
        try {
            draft = drafts.get(expected.draftId()).orElse(null);
        } catch (RuntimeException failure) {
            retire(actorId, expected);
            return false;
        }
        if (draft == null || !matchesCanonicalDraft(expected, draft)) {
            retire(actorId, expected);
            return false;
        }
        if (!draft.draftRootHash().equals(candidate.draftRootHash())
                || draft.ackCursor() != candidate.ackCursor()) {
            return false;
        }

        try {
            MinimapCapability.Binding binding = bindings.preflight(
                    expected.binding().target().mapKey(),
                    expected.binding().target().dimension(),
                    expected.binding().documentId(),
                    expected.baseRevision()
            );
            if (binding != null
                    && !binding.sourceHash().equals(expected.baseSourceHash())) {
                retire(actorId, expected);
                return false;
            }
            sessions.authorize(
                    actorId,
                    expected.sessionId(),
                    expected.binding().target().mapKey(),
                    expected.binding().target().dimension(),
                    expected.binding().documentId(),
                    expected.draftId(),
                    expected.baseRevision(),
                    MinimapAction.SAVE_DRAFT
            );
            return true;
        } catch (RuntimeException failure) {
            retire(actorId, expected);
            return false;
        }
    }

    public void clearIfMatches(UUID actorId, WireIdentity.EditorContext context) {
        if (actorId == null || context == null) {
            return;
        }
        WireIdentity.EditorContext current = active.get(actorId);
        if (current != null && sameStableFields(current, context)) {
            active.remove(actorId, current);
        }
    }

    public void clearActor(UUID actorId) {
        if (actorId != null) {
            active.remove(actorId);
        }
    }

    public void clearMap(MapKey mapKey) {
        if (mapKey == null) {
            return;
        }
        active.forEach((actorId, context) -> {
            if (mapKey.equals(context.binding().target().mapKey())) {
                active.remove(actorId, context);
            }
        });
    }

    public void clearAll() {
        active.clear();
    }

    @Override
    public void close() {
        clearAll();
    }

    private void retire(UUID actorId, WireIdentity.EditorContext expected) {
        active.remove(actorId, expected);
    }

    private static boolean matchesCanonicalDraft(
            WireIdentity.EditorContext expected,
            DraftState draft
    ) {
        return expected.draftId().equals(draft.draftId())
                && expected.binding().target().mapKey().equals(draft.mapKey())
                && expected.binding().target().dimension().equals(draft.dimension())
                && expected.binding().documentId().equals(draft.documentId())
                && expected.baseRevision() == draft.baseRevision()
                && expected.baseSourceHash().equals(draft.baseSourceHash());
    }

    private static boolean sameStableFields(
            WireIdentity.EditorContext expected,
            WireIdentity.EditorContext candidate
    ) {
        return expected.lease().equals(candidate.lease())
                && expected.binding().equals(candidate.binding())
                && expected.sessionId().equals(candidate.sessionId())
                && expected.draftId().equals(candidate.draftId())
                && expected.baseRevision() == candidate.baseRevision()
                && expected.baseSourceHash().equals(candidate.baseSourceHash());
    }
}
