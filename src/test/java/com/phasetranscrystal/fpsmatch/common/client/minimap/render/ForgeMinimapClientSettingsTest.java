package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudAnchor;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeMinimapClientSettingsTest {
    @Test
    void mapsEveryForgeClientOptionIntoClampedSettings() {
        CommentedConfig values = CommentedConfig.inMemory();
        FPSMConfig.clientSpec.correct(values);
        FPSMConfig.clientSpec.setConfig(values);
        FPSMConfig.Client config = FPSMConfig.client;
        boolean enabled = config.minimapEnabled.get();
        int preferred = config.minimapPreferredSize.get();
        int minimum = config.minimapMinimumSize.get();
        String anchor = config.minimapHudAnchor.get();
        int marginX = config.minimapHudMarginX.get();
        int marginY = config.minimapHudMarginY.get();
        int priority = config.minimapHudSafeAreaPriority.get();
        String shape = config.minimapClipShape.get();
        double opacity = config.minimapOpacity.get();
        double backgroundOpacity = config.minimapBackgroundOpacity.get();
        String mode = config.minimapDefaultMode.get();
        double zoom = config.minimapFollowZoom.get();
        boolean regionLabels = config.minimapShowRegionLabels.get();
        boolean floorLabel = config.minimapShowFloorLabel.get();
        boolean compass = config.minimapShowCompass.get();
        String adjacent = config.minimapAdjacentFloorMarkerStyle.get();
        String filters = config.minimapMarkerFilterCsv.get();
        int timeout = config.minimapManualFloorTimeoutTicks.get();
        try {
            config.minimapEnabled.set(false);
            config.minimapPreferredSize.set(192);
            config.minimapMinimumSize.set(80);
            config.minimapHudAnchor.set("BOTTOM_RIGHT");
            config.minimapHudMarginX.set(24);
            config.minimapHudMarginY.set(16);
            config.minimapHudSafeAreaPriority.set(75);
            config.minimapClipShape.set("CIRCLE");
            config.minimapOpacity.set(0.75);
            config.minimapBackgroundOpacity.set(0.25);
            config.minimapDefaultMode.set("FOLLOW_PLAYER");
            config.minimapFollowZoom.set(2.5);
            config.minimapShowRegionLabels.set(false);
            config.minimapShowFloorLabel.set(false);
            config.minimapShowCompass.set(false);
            config.minimapAdjacentFloorMarkerStyle.set("HIDDEN");
            config.minimapMarkerFilterCsv.set(
                    "fpsmatch:type/player,fpsmatch:type/objective"
            );
            config.minimapManualFloorTimeoutTicks.set(240);

            MinimapClientSettings settings =
                    ForgeMinimapClientSettings.read(config);

            assertFalse(settings.enabled());
            assertEquals(192, settings.preferredSize());
            assertEquals(80, settings.minSize());
            assertEquals(HudAnchor.BOTTOM_RIGHT, settings.anchor());
            assertEquals(24, settings.marginX());
            assertEquals(16, settings.marginY());
            assertEquals(75, settings.safeAreaPriority());
            assertEquals(ShapeMode.CIRCLE, settings.shape());
            assertEquals(0.75f, settings.opacity());
            assertEquals(0.25f, settings.backgroundOpacity());
            assertEquals(MinimapOrientation.FOLLOW_PLAYER, settings.orientation());
            assertEquals(2.5, settings.followZoom());
            assertFalse(settings.showRegionLabels());
            assertFalse(settings.showFloorLabel());
            assertFalse(settings.showCompass());
            assertEquals(
                    AdjacentFloorMarkerStyle.HIDDEN,
                    settings.adjacentFloorMarkerStyle()
            );
            assertEquals(
                    List.of(
                            "fpsmatch:type/player",
                            "fpsmatch:type/objective"
                    ),
                    settings.markerFilter()
            );
            assertEquals(240, settings.manualFloorTimeoutTicks());
        } finally {
            config.minimapEnabled.set(enabled);
            config.minimapPreferredSize.set(preferred);
            config.minimapMinimumSize.set(minimum);
            config.minimapHudAnchor.set(anchor);
            config.minimapHudMarginX.set(marginX);
            config.minimapHudMarginY.set(marginY);
            config.minimapHudSafeAreaPriority.set(priority);
            config.minimapClipShape.set(shape);
            config.minimapOpacity.set(opacity);
            config.minimapBackgroundOpacity.set(backgroundOpacity);
            config.minimapDefaultMode.set(mode);
            config.minimapFollowZoom.set(zoom);
            config.minimapShowRegionLabels.set(regionLabels);
            config.minimapShowFloorLabel.set(floorLabel);
            config.minimapShowCompass.set(compass);
            config.minimapAdjacentFloorMarkerStyle.set(adjacent);
            config.minimapMarkerFilterCsv.set(filters);
            config.minimapManualFloorTimeoutTicks.set(timeout);
            FPSMConfig.clientSpec.setConfig(null);
        }
    }

    @Test
    void unloadedConfigUsesDeclaredDefaults() {
        FPSMConfig.clientSpec.setConfig(null);

        MinimapClientSettings settings =
                ForgeMinimapClientSettings.read(FPSMConfig.client);

        assertTrue(settings.enabled());
        assertEquals(128, settings.preferredSize());
        assertEquals(96, settings.minSize());
        assertEquals(HudAnchor.TOP_LEFT, settings.anchor());
        assertEquals(ShapeMode.SQUARE, settings.shape());
        assertEquals(MinimapOrientation.DOCUMENT, settings.orientation());
    }
}
