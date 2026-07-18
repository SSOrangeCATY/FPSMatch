package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.function.IntSupplier;

public final class NeoForgeMinimapServerConfigView implements MinimapServerConfigView {
    private final IntSupplier editorSessionTtlMinutes;

    public NeoForgeMinimapServerConfigView() {
        this(() -> FPSMConfig.Server.minimapEditorSessionTtlMinutes.get());
    }

    NeoForgeMinimapServerConfigView(IntSupplier editorSessionTtlMinutes) {
        this.editorSessionTtlMinutes = Objects.requireNonNull(
                editorSessionTtlMinutes, "editorSessionTtlMinutes"
        );
    }

    @Override
    public Duration editorSessionIdleTtl() {
        return Duration.ofMinutes(editorSessionTtlMinutes.getAsInt());
    }
}
