package com.ptcrys.fpsmatch.core.minimap.editor.command;

public final class EditorCommandException extends RuntimeException {
    public EditorCommandException(String message) {
        super(message);
    }

    public EditorCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
