package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeMinimapServerConfigViewTest {

    @BeforeAll
    static void initializeServerSpec() {
        ForgeConfigSpec serverSpec = FPSMConfig.initServer();
        serverSpec.setConfig(CommentedConfig.inMemory());
    }

    @Test
    void adapterExposesTheConfiguredEditorSessionTtl() {
        assertEquals(
                Duration.ofMinutes(10),
                new ForgeMinimapServerConfigView().editorSessionIdleTtl()
        );
    }
}
