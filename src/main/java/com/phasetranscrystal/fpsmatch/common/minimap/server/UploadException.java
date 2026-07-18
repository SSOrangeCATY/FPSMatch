package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;

import java.util.Objects;

public final class UploadException extends RuntimeException {
    private final MinimapErrorCode errorCode;

    public UploadException(MinimapErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public UploadException(MinimapErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public MinimapErrorCode errorCode() {
        return errorCode;
    }
}
