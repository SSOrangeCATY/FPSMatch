package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;

import java.util.Objects;

final class EditorCloseHandshake {
    private WireEditor.CloseMode mode;
    private Runnable listener = () -> {
    };

    void begin(WireEditor.CloseMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    boolean pending() {
        return mode != null;
    }

    WireEditor.CloseMode mode() {
        return Objects.requireNonNull(mode, "pending close mode");
    }

    boolean retain(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (!pending()) {
            return false;
        }
        this.listener = listener;
        return true;
    }

    void complete() {
        mode = null;
        Runnable completed = listener;
        listener = () -> {
        };
        completed.run();
    }
}
