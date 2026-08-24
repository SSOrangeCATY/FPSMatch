package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;

import java.io.IOException;

final class UploadCleanupFailures {
    private UploadCleanupFailures() {
    }

    static Throwable merge(Throwable first, Throwable next) {
        if (next == null) {
            return first;
        }
        Throwable failure = next instanceof IOException exception
                ? new UploadException(
                MinimapErrorCode.PUBLISH_IO_FAILED,
                "Unable to close upload payload",
                exception
        ) : next;
        if (first == null) {
            return failure;
        }
        if (first != failure) {
            first.addSuppressed(failure);
        }
        return first;
    }

    static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
