package com.phasetranscrystal.fpsmatch.core.minimap.format;

/**
 * Signals that a model JSON document is not a canonical, lossless authority
 * representation for the supplied Codec.
 */
public final class CanonicalModelJsonException extends CanonicalJsonException {
    public CanonicalModelJsonException(String message) {
        super(message);
    }

    public CanonicalModelJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
