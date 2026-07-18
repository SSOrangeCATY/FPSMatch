package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;

import java.time.Duration;

/** Forge-backed adapter kept outside the platform-neutral session manager. */
public final class ForgeMinimapServerConfigView implements MinimapServerConfigView {
    @Override
    public Duration editorSessionIdleTtl() {
        return Duration.ofMinutes(
                FPSMConfig.Server.minimapEditorSessionTtlMinutes.get()
        );
    }
}
