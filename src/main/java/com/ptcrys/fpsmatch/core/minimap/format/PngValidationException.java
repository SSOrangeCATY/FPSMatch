package com.ptcrys.fpsmatch.core.minimap.format;

public final class PngValidationException extends RuntimeException {
    public PngValidationException(String message) {
        super(message);
    }

    public PngValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
