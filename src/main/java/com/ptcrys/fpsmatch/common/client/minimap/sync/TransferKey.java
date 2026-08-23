package com.ptcrys.fpsmatch.common.client.minimap.sync;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public record TransferKey(
        String stablePath,
        Sha256 objectHash,
        int totalLength,
        int fragmentCount
) {
    public TransferKey {
        Objects.requireNonNull(stablePath, "stablePath");
        Objects.requireNonNull(objectHash, "objectHash");
        if (totalLength <= 0 || fragmentCount <= 0) {
            throw new IllegalArgumentException("Transfer geometry must be positive");
        }
    }
}