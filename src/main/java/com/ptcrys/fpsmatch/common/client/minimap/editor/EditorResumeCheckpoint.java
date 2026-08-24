package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.UUID;

/** Process-local locator for one server-owned draft and its acknowledged model. */
public record EditorResumeCheckpoint(
        UUID actorId,
        WireIdentity.DocumentBinding binding,
        UUID draftId,
        long baseRevision,
        Sha256 baseSourceHash,
        Sha256 draftRootHash,
        long ackCursor,
        byte[] sourceBytes
) {
    public EditorResumeCheckpoint {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(baseSourceHash, "baseSourceHash");
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        if (baseRevision < 0 || ackCursor < 0) {
            throw new IllegalArgumentException("Resume checkpoint coordinates must be non-negative");
        }
        sourceBytes = Objects.requireNonNull(sourceBytes, "sourceBytes").clone();
        if (sourceBytes.length == 0) {
            throw new IllegalArgumentException("Resume checkpoint source must not be empty");
        }
    }

    @Override
    public byte[] sourceBytes() {
        return sourceBytes.clone();
    }

    public MapKey mapKey() {
        return binding.target().mapKey();
    }

    boolean matches(
            UUID actor,
            WireIdentity.DocumentBinding expectedBinding,
            long expectedRevision,
            java.util.Optional<Sha256> expectedSourceHash
    ) {
        return actorId.equals(actor)
                && binding.equals(expectedBinding)
                && baseRevision == expectedRevision
                && expectedSourceHash.map(baseSourceHash::equals).orElse(true);
    }

    static EditorResumeCheckpoint from(
            UUID actorId,
            WireIdentity.EditorContext context,
            byte[] sourceBytes
    ) {
        return new EditorResumeCheckpoint(
                actorId,
                context.binding(),
                context.draftId(),
                context.baseRevision(),
                context.baseSourceHash(),
                context.draftRootHash(),
                context.ackCursor(),
                sourceBytes
        );
    }
}
