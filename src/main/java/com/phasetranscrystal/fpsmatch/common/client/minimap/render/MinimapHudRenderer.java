package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;

import java.util.List;
import java.util.Objects;

/**
 * Pure HUD composer: settings + camera + commands -> immutable {@link MinimapFrame}.
 * No GuiGraphics / LDLib2 types.
 */
public final class MinimapHudRenderer {
    public MinimapFrame compose(
            MinimapClientSettings settings,
            ViewportCamera camera,
            FloorViewState floor,
            List<MapDrawCommand> commands,
            PlaceholderKind placeholder
    ) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(commands, "commands");
        MinimapClientSettings clamped = settings.clamp();

        MinimapFrame.Builder builder = MinimapFrame.builder()
                .camera(camera)
                .shape(clamped.shape())
                .backgroundOpacity(
                        clamped.backgroundOpacity() * clamped.opacity()
                )
                .floor(floor);

        float opacity = clamped.opacity();
        boolean showLabels = clamped.showRegionLabels();
        for (MapDrawCommand command : commands) {
            if (command instanceof MapDrawCommand.Label && !showLabels) {
                continue;
            }
            builder.addCommand(scaleOpacity(command, opacity));
        }
        if (placeholder != null) {
            builder.placeholder(placeholder);
        }
        return builder.build();
    }

    public void submit(MinimapFrame frame, MinimapDrawBackend backend) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(backend, "backend");
        backend.submit(frame);
    }

    private static MapDrawCommand scaleOpacity(MapDrawCommand command, float opacity) {
        if (opacity >= 0.999f) {
            return command;
        }
        if (command instanceof MapDrawCommand.Tile tile) {
            return new MapDrawCommand.Tile(
                    tile.textureKey(), tile.x(), tile.y(), tile.width(), tile.height(), tile.opacity() * opacity
            );
        }
        if (command instanceof MapDrawCommand.MarkerIcon marker) {
            return new MapDrawCommand.MarkerIcon(
                    marker.markerId(),
                    marker.typeId(),
                    marker.styleId(),
                    marker.x(),
                    marker.y(),
                    marker.yawDegrees(),
                    marker.opacity() * opacity,
                    marker.adjacent()
            );
        }
        if (command instanceof MapDrawCommand.Label label) {
            return new MapDrawCommand.Label(
                    label.displayLabel(),
                    label.x(),
                    label.y(),
                    label.color(),
                    label.scale(),
                    label.opacity() * opacity
            );
        }
        if (command instanceof MapDrawCommand.RegionOutline region) {
            return new MapDrawCommand.RegionOutline(
                    region.regionId(), region.pointsXY(), region.opacity() * opacity
            );
        }
        return command;
    }
}
