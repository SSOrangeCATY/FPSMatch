package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Small process-local registry; a draft locator is intentionally not server storage. */
public final class EditorResumeCheckpointRegistry {
    private static final EditorResumeCheckpointRegistry GLOBAL =
            new EditorResumeCheckpointRegistry();

    private final Map<Key, EditorResumeCheckpoint> checkpoints = new HashMap<>();

    public static EditorResumeCheckpointRegistry global() {
        return GLOBAL;
    }

    public synchronized void remember(
            UUID actorId,
            WireIdentity.EditorContext context,
            byte[] sourceBytes
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(context, "context");
        Key key = new Key(actorId, context.binding().target().mapKey());
        EditorResumeCheckpoint next = EditorResumeCheckpoint.from(
                actorId, context, sourceBytes
        );
        EditorResumeCheckpoint current = checkpoints.get(key);
        // A stale screen must not replace a newer/different draft without an
        // explicit discard/commit clearing the old locator first.
        if (current == null || current.draftId().equals(next.draftId())) {
            checkpoints.put(key, next);
        }
    }

    public synchronized Lookup lookup(
            UUID actorId,
            WireIdentity.DocumentBinding binding,
            long expectedRevision,
            Optional<Sha256> expectedSourceHash
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(expectedSourceHash, "expectedSourceHash");
        EditorResumeCheckpoint checkpoint = checkpoints.get(
                new Key(actorId, binding.target().mapKey())
        );
        if (checkpoint == null) {
            return new Lookup(LookupState.NONE, Optional.empty());
        }
        return checkpoint.matches(actorId, binding, expectedRevision, expectedSourceHash)
                ? new Lookup(LookupState.MATCH, Optional.of(checkpoint))
                : new Lookup(LookupState.MISMATCH, Optional.of(checkpoint));
    }

    public synchronized void discard(UUID actorId, MapKey mapKey) {
        checkpoints.remove(new Key(
                Objects.requireNonNull(actorId, "actorId"),
                Objects.requireNonNull(mapKey, "mapKey")
        ));
    }

    public synchronized void clearAll() {
        checkpoints.clear();
    }

    public enum LookupState { NONE, MATCH, MISMATCH }

    public record Lookup(LookupState state, Optional<EditorResumeCheckpoint> checkpoint) {
        public Lookup {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(checkpoint, "checkpoint");
            if ((state == LookupState.NONE) != checkpoint.isEmpty()) {
                throw new IllegalArgumentException("Lookup state and checkpoint disagree");
            }
        }
    }

    private record Key(UUID actorId, MapKey mapKey) {
        private Key {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(mapKey, "mapKey");
        }
    }
}
