package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;

import java.util.Objects;

/** A protocol failure whose numeric meaning is stable across loader adapters. */
public final class MinimapWireError extends RuntimeException {
    private final MinimapErrorCode code;

    public MinimapWireError(MinimapErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MinimapWireError(MinimapErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MinimapErrorCode code() {
        return code;
    }
}
