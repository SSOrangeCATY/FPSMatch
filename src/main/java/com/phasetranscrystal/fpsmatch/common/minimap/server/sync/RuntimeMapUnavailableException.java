package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

public final class RuntimeMapUnavailableException extends RuntimeException {
    public RuntimeMapUnavailableException(String message) {
        super(message);
    }

    public RuntimeMapUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
