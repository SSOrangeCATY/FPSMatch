package com.ptcrys.fpsmatch.core.minimap.editor.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DraftLease(UUID draftId, Instant expiresAt, DraftSnapshot snapshot) {
    public DraftLease {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(snapshot, "snapshot");
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }
}
