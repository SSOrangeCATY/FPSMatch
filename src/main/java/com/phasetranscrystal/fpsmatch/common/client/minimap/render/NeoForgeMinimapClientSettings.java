package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class NeoForgeMinimapClientSettings {
    private NeoForgeMinimapClientSettings() {
    }

    public static MinimapClientSettings read(FPSMConfig.Client config) {
        Objects.requireNonNull(config, "config");
        return read(new Values(
                value(config.minimapEnabled),
                value(config.minimapHudAnchor),
                value(config.minimapPreferredSize),
                value(config.minimapMinimumSize),
                value(config.minimapHudMarginX),
                value(config.minimapHudMarginY),
                value(config.minimapHudSafeAreaPriority),
                value(config.minimapClipShape),
                value(config.minimapOpacity),
                value(config.minimapBackgroundOpacity),
                value(config.minimapDefaultMode),
                value(config.minimapFollowZoom),
                value(config.minimapShowRegionLabels),
                value(config.minimapShowFloorLabel),
                value(config.minimapShowCompass),
                value(config.minimapAdjacentFloorMarkerStyle),
                value(config.minimapMarkerFilterCsv),
                value(config.minimapManualFloorTimeoutTicks)
        ));
    }

    public static MinimapClientSettings read(Values values) {
        Objects.requireNonNull(values, "values");
        List<String> markerFilter = values.markerFilterCsv().isEmpty()
                ? List.of()
                : Arrays.asList(values.markerFilterCsv().split(",", -1));
        return new MinimapClientSettings(
                values.enabled(),
                MinimapClientSettings.parseAnchor(values.anchor()),
                values.preferredSize(),
                values.minimumSize(),
                values.marginX(),
                values.marginY(),
                values.safeAreaPriority(),
                MinimapClientSettings.parseShape(values.shape()),
                values.opacity().floatValue(),
                values.backgroundOpacity().floatValue(),
                MinimapClientSettings.parseOrientation(values.defaultMode()),
                values.followZoom().doubleValue(),
                values.showRegionLabels(),
                values.showFloorLabel(),
                values.showCompass(),
                MinimapClientSettings.parseAdjacentStyle(values.adjacentStyle()),
                markerFilter,
                values.manualFloorTimeoutTicks()
        ).clamp();
    }

    private static <T> T value(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value) {
        return value.get();
    }

    private static boolean value(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        return value.get();
    }

    private static int value(net.neoforged.neoforge.common.ModConfigSpec.IntValue value) {
        return value.get();
    }

    private static double value(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue value) {
        return value.get();
    }

    public record Values(
            boolean enabled,
            String anchor,
            int preferredSize,
            int minimumSize,
            int marginX,
            int marginY,
            int safeAreaPriority,
            String shape,
            Number opacity,
            Number backgroundOpacity,
            String defaultMode,
            Number followZoom,
            boolean showRegionLabels,
            boolean showFloorLabel,
            boolean showCompass,
            String adjacentStyle,
            String markerFilterCsv,
            int manualFloorTimeoutTicks
    ) {
        public Values {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(opacity, "opacity");
            Objects.requireNonNull(backgroundOpacity, "backgroundOpacity");
            Objects.requireNonNull(defaultMode, "defaultMode");
            Objects.requireNonNull(followZoom, "followZoom");
            Objects.requireNonNull(adjacentStyle, "adjacentStyle");
            Objects.requireNonNull(markerFilterCsv, "markerFilterCsv");
        }
    }
}
