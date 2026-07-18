package com.phasetranscrystal.fpsmatch.common.minimap.server;

public record UploadLimits(
        long maximumUploadBytes,
        int maximumGlobalInFlightUploads,
        int maximumOwnerInFlightUploads,
        long maximumGlobalDeclaredBytes,
        long maximumOwnerDeclaredBytes
) {
    public UploadLimits {
        if (maximumUploadBytes <= 0
                || maximumGlobalInFlightUploads <= 0
                || maximumOwnerInFlightUploads <= 0
                || maximumGlobalDeclaredBytes <= 0
                || maximumOwnerDeclaredBytes <= 0) {
            throw new IllegalArgumentException("Upload limits must be positive");
        }
    }
}
