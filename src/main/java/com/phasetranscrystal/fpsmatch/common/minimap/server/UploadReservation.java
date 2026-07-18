package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UploadReservation(
        UUID uploadId,
        long totalLength,
        int fragmentCount,
        Sha256 expectedHash,
        Instant expiresAt
) {
    public UploadReservation {
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(expectedHash, "expectedHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (totalLength <= 0 || fragmentCount <= 0) {
            throw new IllegalArgumentException("Upload dimensions must be positive");
        }
    }
}
