package com.ptcrys.fpsmatch.core.minimap.format;

public final class ContainerValidationException extends RuntimeException {
    public ContainerValidationException(String message) {
        super(message);
    }

    public ContainerValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
