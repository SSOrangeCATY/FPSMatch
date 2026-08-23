package com.ptcrys.fpsmatch.core.minimap.view;

import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

/**
 * Immutable draw intents independent of GuiGraphics / LDLib2 widgets.
 */
public sealed interface MapDrawCommand permits
        MapDrawCommand.Tile,
        MapDrawCommand.MarkerIcon,
        MapDrawCommand.Label,
        MapDrawCommand.RegionOutline {
    record Tile(String textureKey, double x, double y, double width, double height, float opacity)
            implements MapDrawCommand {
        public Tile {
            Objects.requireNonNull(textureKey, "textureKey");
            if (!(width > 0) || !(height > 0) || opacity < 0f || opacity > 1f) {
                throw new IllegalArgumentException("Invalid tile command");
            }
        }
    }

    record MarkerIcon(
            String markerId,
            NamespacedId typeId,
            NamespacedId styleId,
            double x,
            double y,
            float yawDegrees,
            float opacity,
            boolean adjacent
    )
            implements MapDrawCommand {
        public MarkerIcon {
            Objects.requireNonNull(markerId, "markerId");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(styleId, "styleId");
            if (opacity < 0f || opacity > 1f || !Float.isFinite(yawDegrees)) {
                throw new IllegalArgumentException("Invalid marker icon command");
            }
        }

        public MarkerIcon(
                String markerId,
                double x,
                double y,
                float yawDegrees,
                float opacity,
                boolean adjacent
        ) {
            this(
                    markerId,
                    NamespacedId.parse("fpsmatch:type/unknown"),
                    NamespacedId.parse("fpsmatch:style/fallback"),
                    x,
                    y,
                    yawDegrees,
                    opacity,
                    adjacent
            );
        }
    }

    record Label(
            DisplayLabel displayLabel,
            double x,
            double y,
            int color,
            double scale,
            float opacity
    ) implements MapDrawCommand {
        public Label {
            Objects.requireNonNull(displayLabel, "displayLabel");
            if (!Double.isFinite(scale)
                    || scale <= 0
                    || opacity < 0f
                    || opacity > 1f) {
                throw new IllegalArgumentException("Invalid label command");
            }
        }

        public Label(String text, double x, double y, float opacity) {
            this(
                    DisplayLabel.literal(text),
                    x,
                    y,
                    0xFFFFFFFF,
                    1.0,
                    opacity
            );
        }

        public String text() {
            return displayLabel.value();
        }
    }

    record RegionOutline(String regionId, double[] pointsXY, float opacity) implements MapDrawCommand {
        public RegionOutline {
            Objects.requireNonNull(regionId, "regionId");
            Objects.requireNonNull(pointsXY, "pointsXY");
            if (pointsXY.length < 6 || (pointsXY.length & 1) != 0 || opacity < 0f || opacity > 1f) {
                throw new IllegalArgumentException("Invalid region outline command");
            }
            pointsXY = pointsXY.clone();
        }

        @Override
        public double[] pointsXY() {
            return pointsXY.clone();
        }
    }
}
