package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.config.FPSMConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ForgeMinimapClientSettings {
    private ForgeMinimapClientSettings() {
    }

    public static MinimapClientSettings read(FPSMConfig.Client config) {
        Objects.requireNonNull(config, "config");
        boolean loaded = FPSMConfig.clientSpec.isLoaded();
        String filterCsv = value(config.minimapMarkerFilterCsv, loaded);
        List<String> markerFilter = filterCsv.isEmpty()
                ? List.of()
                : Arrays.asList(filterCsv.split(",", -1));
        return new MinimapClientSettings(
                value(config.minimapEnabled, loaded),
                MinimapClientSettings.parseAnchor(
                        value(config.minimapHudAnchor, loaded)
                ),
                value(config.minimapPreferredSize, loaded),
                value(config.minimapMinimumSize, loaded),
                value(config.minimapHudMarginX, loaded),
                value(config.minimapHudMarginY, loaded),
                value(config.minimapHudSafeAreaPriority, loaded),
                MinimapClientSettings.parseShape(
                        value(config.minimapClipShape, loaded)
                ),
                value(config.minimapOpacity, loaded).floatValue(),
                value(config.minimapBackgroundOpacity, loaded).floatValue(),
                MinimapClientSettings.parseOrientation(
                        value(config.minimapDefaultMode, loaded)
                ),
                value(config.minimapFollowZoom, loaded),
                value(config.minimapShowRegionLabels, loaded),
                value(config.minimapShowFloorLabel, loaded),
                value(config.minimapShowCompass, loaded),
                MinimapClientSettings.parseAdjacentStyle(
                        value(config.minimapAdjacentFloorMarkerStyle, loaded)
                ),
                markerFilter,
                value(config.minimapManualFloorTimeoutTicks, loaded)
        ).clamp();
    }

    private static <T> T value(
            ForgeConfigSpec.ConfigValue<T> configValue,
            boolean loaded
    ) {
        return loaded ? configValue.get() : configValue.getDefault();
    }
}
