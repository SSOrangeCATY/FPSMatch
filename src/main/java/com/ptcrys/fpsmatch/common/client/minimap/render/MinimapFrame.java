package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.core.minimap.view.FloorViewState;
import com.ptcrys.fpsmatch.core.minimap.view.MapDrawCommand;
import com.ptcrys.fpsmatch.core.minimap.view.PlaceholderKind;
import com.ptcrys.fpsmatch.core.minimap.view.ShapeMode;
import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable frame snapshot for HUD/tactical presentation adapters.
 */
public final class MinimapFrame {
    private final ViewportCamera camera;
    private final ShapeMode shape;
    private final float backgroundOpacity;
    private final FloorViewState floor;
    private final List<MapDrawCommand> commands;
    private final Optional<PlaceholderKind> placeholder;

    private MinimapFrame(
            ViewportCamera camera,
            ShapeMode shape,
            float backgroundOpacity,
            FloorViewState floor,
            List<MapDrawCommand> commands,
            Optional<PlaceholderKind> placeholder
    ) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.shape = Objects.requireNonNull(shape, "shape");
        if (!Float.isFinite(backgroundOpacity)
                || backgroundOpacity < 0f
                || backgroundOpacity > 1f) {
            throw new IllegalArgumentException(
                    "background opacity must be in [0, 1]"
            );
        }
        this.backgroundOpacity = backgroundOpacity;
        this.floor = Objects.requireNonNull(floor, "floor");
        this.commands = List.copyOf(commands);
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    }

    public static Builder builder() {
        return new Builder();
    }

    public ViewportCamera camera() { return camera; }
    public ShapeMode shape() { return shape; }
    public float backgroundOpacity() { return backgroundOpacity; }
    public FloorViewState floor() { return floor; }
    public List<MapDrawCommand> commands() { return commands; }
    public Optional<PlaceholderKind> placeholder() { return placeholder; }

    public static final class Builder {
        private ViewportCamera camera;
        private ShapeMode shape = ShapeMode.SQUARE;
        private float backgroundOpacity = 0.6f;
        private FloorViewState floor = FloorViewState.automatic("ground");
        private final List<MapDrawCommand> commands = new ArrayList<>();
        private Optional<PlaceholderKind> placeholder = Optional.empty();

        public Builder camera(ViewportCamera camera) {
            this.camera = camera;
            return this;
        }

        public Builder shape(ShapeMode shape) {
            this.shape = shape;
            return this;
        }

        public Builder backgroundOpacity(float backgroundOpacity) {
            this.backgroundOpacity = backgroundOpacity;
            return this;
        }

        public Builder floor(FloorViewState floor) {
            this.floor = floor;
            return this;
        }

        public Builder addCommand(MapDrawCommand command) {
            this.commands.add(Objects.requireNonNull(command, "command"));
            return this;
        }

        public Builder placeholder(PlaceholderKind kind) {
            this.placeholder = Optional.of(Objects.requireNonNull(kind, "kind"));
            return this;
        }

        public MinimapFrame build() {
            return new MinimapFrame(
                    camera,
                    shape,
                    backgroundOpacity,
                    floor,
                    commands,
                    placeholder
            );
        }
    }
}
