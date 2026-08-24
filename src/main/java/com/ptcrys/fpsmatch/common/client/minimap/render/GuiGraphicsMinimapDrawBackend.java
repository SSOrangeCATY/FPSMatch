package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.core.minimap.view.MapDrawCommand;
import com.ptcrys.fpsmatch.core.minimap.view.PlaceholderKind;
import com.ptcrys.fpsmatch.core.minimap.view.ProjectedPose;
import com.ptcrys.fpsmatch.core.minimap.view.ShapeMode;
import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;

import java.util.Objects;
import java.util.Optional;

public final class GuiGraphicsMinimapDrawBackend implements MinimapDrawBackend {
    private final DrawTarget target;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final Optional<MarkerPresentationResolver> markerPresentations;

    public GuiGraphicsMinimapDrawBackend(
            DrawTarget target,
            double x,
            double y,
            double width,
            double height
    ) {
        this(target, x, y, width, height, null);
    }

    public GuiGraphicsMinimapDrawBackend(
            DrawTarget target,
            double x,
            double y,
            double width,
            double height,
            MarkerPresentationResolver markerPresentations
    ) {
        this.target = Objects.requireNonNull(target, "target");
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !(width > 0)
                || !(height > 0)
                || !Double.isFinite(width)
                || !Double.isFinite(height)) {
            throw new IllegalArgumentException("Draw viewport must be finite and non-empty");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.markerPresentations = Optional.ofNullable(markerPresentations);
    }

    @Override
    public void submit(MinimapFrame frame) {
        Objects.requireNonNull(frame, "frame");
        ViewportCamera camera = frame.camera();
        double centerX = x + width * 0.5;
        double centerY = y + height * 0.5;
        target.begin(
                frame.shape(),
                x,
                y,
                width,
                height,
                frame.backgroundOpacity()
        );
        try {
            for (MapDrawCommand command : frame.commands()) {
                if (command instanceof MapDrawCommand.Tile tile) {
                    target.texture(
                            tile.textureKey(),
                            centerX + (tile.x() - camera.panX()) * camera.zoom(),
                            centerY + (tile.y() - camera.panY()) * camera.zoom(),
                            tile.width() * camera.zoom(),
                            tile.height() * camera.zoom(),
                            tile.opacity(),
                            camera.rotationDegrees(),
                            centerX,
                            centerY
                    );
                } else if (command instanceof MapDrawCommand.MarkerIcon marker) {
                    ProjectedPose projected = camera.projectCanvas(
                            marker.x(), marker.y(), marker.yawDegrees()
                    );
                    target.marker(
                            marker.markerId(),
                            marker.typeId(),
                            marker.styleId(),
                            centerX + projected.canvasX(),
                            centerY + projected.canvasY(),
                            projected.displayYawDegrees(),
                            marker.opacity(),
                            marker.adjacent(),
                            markerPresentations.flatMap(resolver -> resolver.resolve(
                                    marker.typeId(), marker.styleId()
                            ))
                    );
                } else if (command instanceof MapDrawCommand.Label label) {
                    ProjectedPose projected = camera.projectCanvas(
                            label.x(), label.y(), 0f
                    );
                    target.label(
                            label.displayLabel(),
                            centerX + projected.canvasX(),
                            centerY + projected.canvasY(),
                            label.color(),
                            label.scale(),
                            label.opacity()
                    );
                } else if (command instanceof MapDrawCommand.RegionOutline region) {
                    double[] points = region.pointsXY();
                    double[] projected = new double[points.length];
                    for (int index = 0; index < points.length; index += 2) {
                        ProjectedPose point = camera.projectCanvas(
                                points[index], points[index + 1], 0f
                        );
                        projected[index] = centerX + point.canvasX();
                        projected[index + 1] = centerY + point.canvasY();
                    }
                    target.region(region.regionId(), projected, region.opacity());
                }
            }
            frame.placeholder().ifPresent(placeholder ->
                    target.placeholder(placeholder, centerX, centerY)
            );
            frame.hudOverlay().ifPresent(overlay -> {
                overlay.floorLabel().ifPresent(label -> target.floorLabel(
                        label,
                        centerX,
                        y + height - 8
                ));
                overlay.compassRotationDegrees().ifPresent(rotation ->
                        target.compass(
                                rotation,
                                x + width - 12,
                                y + 12
                        )
                );
            });
        } finally {
            target.end();
        }
    }

    public interface DrawTarget {
        void begin(
                ShapeMode shape,
                double x,
                double y,
                double width,
                double height,
                float backgroundOpacity
        );

        void texture(
                String textureKey,
                double x,
                double y,
                double width,
                double height,
                float opacity,
                float rotationDegrees,
                double rotationCenterX,
                double rotationCenterY
        );

        void marker(
                String markerId,
                com.ptcrys.fpsmatch.core.minimap.model.NamespacedId typeId,
                com.ptcrys.fpsmatch.core.minimap.model.NamespacedId styleId,
                double x,
                double y,
                float yawDegrees,
                float opacity,
                boolean adjacent,
                Optional<MarkerPresentationResolver.Resolved> presentation
        );

        void label(String text, double x, double y, float opacity);

        default void label(
                com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel label,
                double x,
                double y,
                int color,
                double scale,
                float opacity
        ) {
            label(label.value(), x, y, opacity);
        }

        void region(String regionId, double[] pointsXY, float opacity);

        void placeholder(PlaceholderKind placeholder, double centerX, double centerY);

        void floorLabel(
                com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel label,
                double centerX,
                double baselineY
        );

        void compass(
                float rotationDegrees,
                double centerX,
                double centerY
        );

        void end();
    }
}
