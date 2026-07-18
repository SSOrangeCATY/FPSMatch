package com.phasetranscrystal.fpsmatch.common.minimap.server;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeoForgeMinimapServerConfigViewTest {
    @Test
    void adapterExposesTheConfiguredEditorSessionTtl() {
        assertEquals(
                Duration.ofMinutes(10),
                new NeoForgeMinimapServerConfigView(() -> 10).editorSessionIdleTtl()
        );
    }
}
