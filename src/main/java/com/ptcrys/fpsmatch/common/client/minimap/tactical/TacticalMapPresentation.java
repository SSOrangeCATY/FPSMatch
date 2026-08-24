package com.ptcrys.fpsmatch.common.client.minimap.tactical;

import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.RegionGeometry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TacticalMapPresentation(
        MinimapFrame frame,
        TacticalViewport viewport,
        List<FloorOption> floors,
        List<LegendEntry> legend,
        List<RegionDetail> regions
) {
    public TacticalMapPresentation {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(viewport, "viewport");
        floors = List.copyOf(Objects.requireNonNull(floors, "floors"));
        legend = List.copyOf(Objects.requireNonNull(legend, "legend"));
        regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
    }

    public TacticalMapPresentation(
            MinimapFrame frame,
            TacticalViewport viewport,
            List<FloorOption> floors,
            List<LegendEntry> legend
    ) {
        this(frame, viewport, floors, legend, List.of());
    }

    public Optional<RegionDetail> regionAt(double canvasX, double canvasY) {
        String floorId = frame.floor().effectiveFloorId().orElse(null);
        if (floorId == null) {
            return Optional.empty();
        }
        com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint point =
                new com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint(
                        canvasX, canvasY
                );
        return regions.stream()
                .filter(region -> region.floorId().equals(floorId))
                .filter(region -> region.geometry().contains(point))
                .sorted(Comparator
                        .comparingInt(RegionDetail::priority)
                        .reversed()
                        .thenComparing(RegionDetail::id))
                .findFirst();
    }

    public record FloorOption(String id, DisplayLabel label) {
        public FloorOption {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }

    public record LegendEntry(
            NamespacedId typeId,
            NamespacedId styleId,
            DisplayLabel label
    ) {
        public LegendEntry {
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(styleId, "styleId");
            Objects.requireNonNull(label, "label");
        }
    }

    public record RegionDetail(
            String id,
            String floorId,
            DisplayLabel label,
            RegionGeometry geometry,
            NamespacedId semanticType,
            List<NamespacedId> tags,
            Optional<NamespacedId> gameplayReference,
            NamespacedId styleId,
            int priority
    ) {
        public RegionDetail {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(floorId, "floorId");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(semanticType, "semanticType");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
            gameplayReference = Objects.requireNonNull(
                    gameplayReference, "gameplayReference"
            );
            Objects.requireNonNull(styleId, "styleId");
        }
    }
}
