package com.ptcrys.fpsmatch.core.minimap.region;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.PolygonGeometry;
import com.ptcrys.fpsmatch.core.minimap.model.RegionGeometry;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeRegion;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeRegionsFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Merges server-owned gameplay regions into a published runtime document.
 *
 * <p>Published geometry and visual settings remain authoritative for a
 * matching gameplay reference. New gameplay regions borrow one unambiguous
 * published visual profile for their semantic type, or an extension fallback
 * when the published runtime has no such profile.</p>
 */
public final class RuntimeRegionMerger {
    private RuntimeRegionMerger() {
    }

    public static RuntimeRegionsFile merge(
            RuntimeManifest manifest,
            RuntimeRegionsFile published,
            List<RuntimeRegionDescriptor> gameplay
    ) {
        return mergeWithPresentations(
                manifest, published, gameplay, List.of()
        ).regions();
    }

    public static MergeResult mergeWithPresentations(
            RuntimeManifest manifest,
            RuntimeRegionsFile published,
            List<RuntimeRegionDescriptor> gameplay,
            List<RegionPresentation> presentations
    ) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(published, "published");
        Objects.requireNonNull(gameplay, "gameplay");
        Objects.requireNonNull(presentations, "presentations");

        Map<NamespacedId, RegionPresentation> presentationsBySemantic = new HashMap<>();
        for (RegionPresentation presentation : presentations) {
            Objects.requireNonNull(presentation, "region presentation");
            RegionPresentation previous = presentationsBySemantic.putIfAbsent(
                    presentation.semanticType(), presentation
            );
            if (previous != null && !previous.equals(presentation)) {
                throw new IllegalArgumentException(
                        "Ambiguous extension presentation for gameplay semantic type: "
                                + presentation.semanticType()
                );
            }
        }

        Map<String, RuntimeFloor> floors = floorsById(manifest);
        Map<String, Integer> publishedIds = new HashMap<>();
        Map<NamespacedId, Integer> publishedReferences = new HashMap<>();
        Map<NamespacedId, Set<VisualProfile>> profiles = new HashMap<>();
        List<RuntimeRegion> staticRegions = published.regions();
        for (int index = 0; index < staticRegions.size(); index++) {
            RuntimeRegion region = Objects.requireNonNull(
                    staticRegions.get(index), "published region"
            );
            if (publishedIds.putIfAbsent(region.id(), index) != null) {
                throw new IllegalArgumentException("Duplicate published region id: " + region.id());
            }
            Optional<NamespacedId> reference = region.gameplayReference();
            if (reference.isPresent()
                    && publishedReferences.putIfAbsent(reference.orElseThrow(), index) != null) {
                throw new IllegalArgumentException(
                        "Ambiguous published gameplay reference: " + reference.orElseThrow()
                );
            }
            profiles.computeIfAbsent(region.semanticType(), ignored -> new HashSet<>())
                    .add(new VisualProfile(
                            region.styleId(), region.minVisibleScale(), region.maxVisibleScale()
                    ));
        }

        Map<String, RuntimeRegionDescriptor> gameplayIds = new HashMap<>();
        Map<NamespacedId, RuntimeRegionDescriptor> gameplayReferences = new HashMap<>();
        for (RuntimeRegionDescriptor descriptor : gameplay) {
            Objects.requireNonNull(descriptor, "gameplay region");
            if (gameplayIds.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate gameplay region id: " + descriptor.id());
            }
            descriptor.gameplayReference().map(NamespacedId::parse).ifPresent(reference -> {
                RuntimeRegionDescriptor previous = gameplayReferences.putIfAbsent(reference, descriptor);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Ambiguous gameplay reference: " + reference
                    );
                }
            });
            if (!floors.containsKey(descriptor.floorId())) {
                throw new IllegalArgumentException(
                        "Gameplay region references unknown floor: " + descriptor.floorId()
                );
            }
        }

        List<RuntimeRegion> merged = new ArrayList<>(staticRegions);
        Map<NamespacedId, RegionPresentation> usedPresentations = new LinkedHashMap<>();
        Set<Integer> matched = new HashSet<>();
        Set<String> resultIds = new HashSet<>(publishedIds.keySet());
        for (RuntimeRegionDescriptor descriptor : gameplay) {
            Optional<NamespacedId> reference = descriptor.gameplayReference().map(NamespacedId::parse);
            Integer matchingIndex = reference.map(publishedReferences::get).orElse(null);
            if (matchingIndex != null) {
                if (!matched.add(matchingIndex)) {
                    throw new IllegalArgumentException(
                            "Gameplay reference matched more than once: " + reference.orElseThrow()
                    );
                }
                RuntimeRegion authored = staticRegions.get(matchingIndex);
                if (!authored.floorId().equals(descriptor.floorId())) {
                    throw new IllegalArgumentException(
                            "Gameplay reference moved across floors: " + reference.orElseThrow()
                    );
                }
                merged.set(matchingIndex, refreshed(authored, descriptor, reference));
                continue;
            }

            if (!resultIds.add(descriptor.id())) {
                throw new IllegalArgumentException(
                        "Gameplay region id collides with published region: " + descriptor.id()
                );
            }
            NamespacedId semanticType = NamespacedId.parse(descriptor.semanticType());
            VisualProfile profile = uniqueProfile(
                    profiles, semanticType, presentationsBySemantic, usedPresentations
            );
            RuntimeFloor floor = floors.get(descriptor.floorId());
            ProjectedGeometry projection = project(manifest.canvas(), floor, descriptor);
            merged.add(new RuntimeRegion(
                    descriptor.id(), descriptor.floorId(), DisplayLabel.literal(descriptor.label()),
                    projection.geometry(), NamespacedId.parse(descriptor.semanticType()),
                    descriptor.tags().stream().map(NamespacedId::parse).toList(), reference,
                    profile.styleId(), projection.anchor(), descriptor.priority(),
                    profile.minVisibleScale(), profile.maxVisibleScale()
            ));
        }

        if (merged.size() > MinimapHardLimits.MAX_REGIONS) {
            throw new IllegalArgumentException("Merged region count exceeds the hard limit");
        }
        return new MergeResult(
                new RuntimeRegionsFile(merged),
                List.copyOf(usedPresentations.values())
        );
    }

    private static RuntimeRegion refreshed(
            RuntimeRegion authored,
            RuntimeRegionDescriptor descriptor,
            Optional<NamespacedId> reference
    ) {
        return new RuntimeRegion(
                authored.id(), authored.floorId(), DisplayLabel.literal(descriptor.label()),
                authored.geometry(), NamespacedId.parse(descriptor.semanticType()),
                descriptor.tags().stream().map(NamespacedId::parse).toList(), reference,
                authored.styleId(), authored.labelAnchor(), descriptor.priority(),
                authored.minVisibleScale(), authored.maxVisibleScale()
        );
    }

    private static Map<String, RuntimeFloor> floorsById(RuntimeManifest manifest) {
        Map<String, RuntimeFloor> floors = new LinkedHashMap<>();
        for (RuntimeFloor floor : manifest.floors()) {
            Objects.requireNonNull(floor, "manifest floor");
            String id = floor.selection().id();
            if (floors.putIfAbsent(id, floor) != null) {
                throw new IllegalArgumentException("Duplicate manifest floor id: " + id);
            }
        }
        return floors;
    }

    private static VisualProfile uniqueProfile(
            Map<NamespacedId, Set<VisualProfile>> profiles,
            NamespacedId semanticType,
            Map<NamespacedId, RegionPresentation> presentations,
            Map<NamespacedId, RegionPresentation> usedPresentations
    ) {
        Set<VisualProfile> candidates = profiles.getOrDefault(semanticType, Set.of());
        if (candidates.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous published visual profile for gameplay semantic type: "
                            + semanticType
            );
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        RegionPresentation presentation = presentations.get(semanticType);
        if (presentation == null) {
            throw new IllegalArgumentException(
                    "No published visual profile for gameplay semantic type: " + semanticType
            );
        }
        usedPresentations.putIfAbsent(semanticType, presentation);
        return new VisualProfile(
                presentation.styleId(), presentation.minVisibleScale(),
                presentation.maxVisibleScale()
        );
    }

    private static ProjectedGeometry project(
            CanvasBounds canvas,
            RuntimeFloor floor,
            RuntimeRegionDescriptor descriptor
    ) {
        WorldAxisAlignedBounds bounds = descriptor.worldBounds();
        if (bounds.minX() == bounds.maxX() || bounds.minZ() == bounds.maxZ()) {
            throw new IllegalArgumentException("Gameplay region has zero horizontal area: " + descriptor.id());
        }
        CanvasPoint[] corners = new CanvasPoint[]{
                floor.worldToCanvas().transform(new com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D(
                        bounds.minX(), bounds.minZ()
                )),
                floor.worldToCanvas().transform(new com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D(
                        bounds.maxX(), bounds.minZ()
                )),
                floor.worldToCanvas().transform(new com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D(
                        bounds.maxX(), bounds.maxZ()
                )),
                floor.worldToCanvas().transform(new com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D(
                        bounds.minX(), bounds.maxZ()
                ))
        };
        CanvasPoint anchor = center(corners);
        for (CanvasPoint corner : corners) {
            if (!canvas.contains(corner)) {
                throw new IllegalArgumentException(
                        "Projected gameplay region lies outside the published canvas: "
                                + descriptor.id()
                );
            }
        }
        if (!canvas.contains(anchor)) {
            throw new IllegalArgumentException(
                    "Projected gameplay region anchor lies outside the published canvas: "
                            + descriptor.id()
            );
        }
        return new ProjectedGeometry(new PolygonGeometry(List.of(corners)), anchor);
    }

    private static CanvasPoint center(CanvasPoint[] corners) {
        double u = 0;
        double v = 0;
        for (CanvasPoint corner : corners) {
            u += corner.u();
            v += corner.v();
        }
        return new CanvasPoint(u / corners.length, v / corners.length);
    }

    private record VisualProfile(
            NamespacedId styleId,
            double minVisibleScale,
            double maxVisibleScale
    ) {
    }

    private record ProjectedGeometry(RegionGeometry geometry, CanvasPoint anchor) {
    }

    public record MergeResult(
            RuntimeRegionsFile regions,
            List<RegionPresentation> usedPresentations
    ) {
        public MergeResult {
            Objects.requireNonNull(regions, "regions");
            usedPresentations = List.copyOf(usedPresentations);
        }
    }
}
