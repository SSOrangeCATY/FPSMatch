package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudAnchor;
import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapHudRendererTest {
    @Test
    void projectsSettingsIntoImmutableFrameCommandsWithoutGuiGraphics() {
        MinimapClientSettings settings = MinimapClientSettings.defaults()
                .withAnchor(HudAnchor.TOP_RIGHT)
                .withPreferredSize(128)
                .withMinSize(96)
                .withShape(ShapeMode.CIRCLE)
                .withOpacity(0.8f)
                .withShowLabels(true)
                .withOrientation(MinimapOrientation.FOLLOW_PLAYER)
                .withFollowZoom(2.0)
                .clamp();

        MinimapHudRenderer renderer = new MinimapHudRenderer();
        MinimapFrame frame = renderer.compose(
                settings,
                ViewportCamera.playerUp(0, 0, settings.followZoom(), 128, 128, 45f),
                FloorViewState.automatic("ground"),
                List.of(
                        new MapDrawCommand.Tile("tiles/ground.png", 0, 0, 64, 64, 1f),
                        new MapDrawCommand.MarkerIcon("fpsmatch:m1", 10, 12, 0f, 1f, false),
                        new MapDrawCommand.Label("A", 8, 4, 1f)
                ),
                null
        );

        assertEquals(ShapeMode.CIRCLE, frame.shape());
        assertEquals(0.48f, frame.backgroundOpacity(), 1e-6);
        assertEquals(0.8f, ((MapDrawCommand.Tile) frame.commands().get(0)).opacity(), 1e-6);
        assertTrue(frame.commands().stream().anyMatch(c -> c instanceof MapDrawCommand.Label));
        assertTrue(frame.placeholder().isEmpty());
    }

    @Test
    void hidesLabelsWhenDisabledAndAppliesPlaceholder() {
        MinimapClientSettings settings = MinimapClientSettings.defaults().withShowLabels(false).clamp();
        MinimapHudRenderer renderer = new MinimapHudRenderer();
        MinimapFrame frame = renderer.compose(
                settings,
                ViewportCamera.fixedNorth(0, 0, 1.0, 96, 96),
                FloorViewState.automatic("ground"),
                List.of(
                        new MapDrawCommand.Tile("tiles/ground.png", 0, 0, 32, 32, 1f),
                        new MapDrawCommand.Label("hidden", 1, 1, 1f)
                ),
                PlaceholderKind.LOADING
        );
        assertTrue(frame.commands().stream().noneMatch(c -> c instanceof MapDrawCommand.Label));
        assertEquals(PlaceholderKind.LOADING, frame.placeholder().orElseThrow());
    }

    @Test
    void clampsInvalidSettingsThroughDeclaredSpec() {
        MinimapClientSettings raw = new MinimapClientSettings(
                true,
                HudAnchor.TOP_LEFT,
                9999,
                10,
                8,
                8,
                50,
                ShapeMode.SQUARE,
                2.5f,
                1.5f,
                MinimapOrientation.FIXED_NORTH,
                99.0,
                true,
                true,
                true,
                AdjacentFloorMarkerStyle.FADED_ARROWS,
                List.of("bad id"),
                5
        ).clamp();
        assertEquals(512, raw.preferredSize());
        assertEquals(64, raw.minSize());
        assertEquals(1.0f, raw.opacity(), 1e-6);
        assertEquals(1.0f, raw.backgroundOpacity(), 1e-6);
        assertEquals(8.0, raw.followZoom(), 1e-6);
        assertEquals(20, raw.manualFloorTimeoutTicks());
        assertTrue(raw.markerFilter().isEmpty());
    }

    @Test
    void pureBackendRecordsDrawSubmissionWithoutMinecraftTypes() {
        RecordingMinimapDrawBackend backend = new RecordingMinimapDrawBackend();
        MinimapHudRenderer renderer = new MinimapHudRenderer();
        MinimapFrame frame = renderer.compose(
                MinimapClientSettings.defaults().clamp(),
                ViewportCamera.fixedNorth(0, 0, 1, 128, 128),
                FloorViewState.automatic("ground"),
                List.of(new MapDrawCommand.Tile("t", 0, 0, 16, 16, 1f)),
                null
        );
        renderer.submit(frame, backend);
        assertEquals(1, backend.submitted().size());
        assertEquals(ShapeMode.SQUARE, backend.lastShape());
    }
}
