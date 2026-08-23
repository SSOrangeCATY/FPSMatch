package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;

import java.util.Objects;

public final class SessionAccessException extends RuntimeException {
    private final MinimapErrorCode errorCode;

    public SessionAccessException(MinimapErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public MinimapErrorCode errorCode() {
        return errorCode;
    }
}
