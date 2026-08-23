package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WireIdentity {
    private WireIdentity() {
    }

    public enum Scope {
        MATCH_HUD(0),
        TACTICAL_SCREEN(1),
        EDITOR(2);

        private final int code;

        Scope(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Scope fromCode(int code) {
            for (Scope value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown minimap scope"
            );
        }
    }

    public record ScopeLease(Scope scope, long scopeEpoch, long runtimeGeneration) {
        public ScopeLease {
            Objects.requireNonNull(scope, "scope");
            requireNonNegative(scopeEpoch, "scopeEpoch");
            requireNonNegative(runtimeGeneration, "runtimeGeneration");
        }
    }

    public record MapTarget(MapKey mapKey, NamespacedId dimension) {
        public MapTarget {
            Objects.requireNonNull(mapKey, "mapKey");
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    public record DocumentBinding(MapTarget target, NamespacedId documentId) {
        public DocumentBinding {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(documentId, "documentId");
        }
    }

    public record RuntimeIdentity(
            DocumentBinding binding,
            long revision,
            Sha256 runtimeHash,
            Optional<Sha256> runtimeContainerHash
    ) {
        public RuntimeIdentity {
            Objects.requireNonNull(binding, "binding");
            requireNonNegative(revision, "revision");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
        }
    }

    public record RuntimeHint(NamespacedId documentId, long revision, Sha256 runtimeHash) {
        public RuntimeHint {
            Objects.requireNonNull(documentId, "documentId");
            requireNonNegative(revision, "revision");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
        }
    }

    public record MarkerStreamCursor(UUID streamEpoch, long lastSequence) {
        public MarkerStreamCursor {
            Objects.requireNonNull(streamEpoch, "streamEpoch");
            requireNonNegative(lastSequence, "lastSequence");
        }
    }

    public record EditorContext(
            ScopeLease lease,
            DocumentBinding binding,
            UUID sessionId,
            UUID draftId,
            long baseRevision,
            Sha256 baseSourceHash,
            Sha256 draftRootHash,
            long ackCursor
    ) {
        public EditorContext {
            Objects.requireNonNull(lease, "lease");
            if (lease.scope() != Scope.EDITOR) {
                throw new IllegalArgumentException("Editor context requires editor scope");
            }
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(draftId, "draftId");
            requireNonNegative(baseRevision, "baseRevision");
            Objects.requireNonNull(baseSourceHash, "baseSourceHash");
            Objects.requireNonNull(draftRootHash, "draftRootHash");
            requireNonNegative(ackCursor, "ackCursor");
        }
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
    }
}
