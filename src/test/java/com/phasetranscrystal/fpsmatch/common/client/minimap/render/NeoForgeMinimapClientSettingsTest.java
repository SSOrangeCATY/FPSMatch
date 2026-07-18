package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudAnchor;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeMinimapClientSettingsTest {
    @Test
    void readsInjectedValuesWithoutLoadingTheGameConfigRuntime() {
        MinimapClientSettings settings = NeoForgeMinimapClientSettings.read(
                new NeoForgeMinimapClientSettings.Values(
                        true,
                        "BOTTOM_RIGHT",
                        256,
                        96,
                        8,
                        10,
                        50,
                        "CIRCLE",
                        0.8,
                        0.4,
                        "FOLLOW_PLAYER",
                        1.5,
                        true,
                        true,
                        true,
                        "FADED_ARROWS",
                        "fpsmatch:player,fpsmatch:objective/c4",
                        100
                )
        );

        assertTrue(settings.enabled());
        assertEquals(HudAnchor.BOTTOM_RIGHT, settings.anchor());
        assertEquals(ShapeMode.CIRCLE, settings.shape());
        assertEquals(1.5, settings.followZoom());
        assertEquals(List.of("fpsmatch:player", "fpsmatch:objective/c4"), settings.markerFilter());
    }
}
