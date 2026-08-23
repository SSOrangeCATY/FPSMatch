package com.ptcrys.fpsmatch.common.minimap.server;

import java.util.UUID;

public record UploadProgress(
        UUID uploadId,
        int receivedFragments,
        int fragmentCount,
        long receivedBytes,
        long totalLength,
        boolean complete
) {
}
