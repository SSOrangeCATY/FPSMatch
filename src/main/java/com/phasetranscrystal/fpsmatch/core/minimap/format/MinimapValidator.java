package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineFit;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionEndpoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ControlPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloorConnection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.PolygonGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionVisualLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MinimapValidator {
    private MinimapValidator() {
    }

    public static List<MinimapValidationIssue> validate(SourceDocument document) {
        List<MinimapValidationIssue> issues = new ArrayList<>();
        if (document.floors().size() > MinimapHardLimits.MAX_FLOORS) {
            add(issues, MinimapValidationCode.HARD_LIMIT_EXCEEDED, "/floors", "Floor count exceeds the hard limit");
        }

        Set<String> floorIds = new HashSet<>();
        int totalLayers = 0;
        for (int floorIndex = 0; floorIndex < document.floors().size(); floorIndex++) {
            SourceFloor floor = document.floors().get(floorIndex);
            String floorPath = "/floors/" + floorIndex;
            if (!floorIds.add(floor.selection().id())) {
                add(issues, MinimapValidationCode.DUPLICATE_FLOOR_ID, floorPath + "/selection/id",
                        "Floor ID is duplicated");
            }
            totalLayers += floor.layers().size();
            validateFloor(document, floor, floorPath, issues);
            validateLayerOrder(document.layerOrder(), floor, floorPath, issues);
        }
        if (totalLayers > MinimapHardLimits.MAX_SOURCE_LAYERS) {
            add(issues, MinimapValidationCode.HARD_LIMIT_EXCEEDED, "/floors",
                    "Layer count exceeds the hard limit");
        }

        if (!document.layerOrder().keySet().equals(floorIds)) {
            add(issues, MinimapValidationCode.LAYER_ORDER_SCOPE_MISMATCH, "/layerOrder",
                    "Layer order keys must exactly match floor IDs");
        }
        validateFloorOverlap(document.floors(), issues);
        return List.copyOf(issues);
    }

    public static List<MinimapValidationIssue> validate(SourceManifest manifest) {
        List<MinimapValidationIssue> issues = new ArrayList<>();
        Set<ContainerPath> paths = new HashSet<>();
        for (int index = 0; index < manifest.entries().size(); index++) {
            SourceEntryDescriptor entry = manifest.entries().get(index);
            String path = "/entries/" + index + "/path";
            if (entry.path().value().equals("manifest.json")) {
                add(issues, MinimapValidationCode.SELF_MANIFEST_ENTRY, path,
                        "Source manifest cannot list itself");
            }
            if (!paths.add(entry.path())) {
                add(issues, MinimapValidationCode.DUPLICATE_ENTRY_PATH, path,
                        "Source entry path is duplicated");
            }
        }
        return List.copyOf(issues);
    }

    public static List<MinimapValidationIssue> validate(RuntimeManifest manifest) {
        List<MinimapValidationIssue> issues = new ArrayList<>();
        Set<ContainerPath> paths = new HashSet<>();
        for (int index = 0; index < manifest.entries().size(); index++) {
            RuntimeEntryDescriptor entry = manifest.entries().get(index);
            String path = "/entries/" + index + "/path";
            if (entry.path().value().equals("runtime-manifest.json")) {
                add(issues, MinimapValidationCode.SELF_MANIFEST_ENTRY, path,
                        "Runtime manifest cannot list itself");
            }
            if (!paths.add(entry.path())) {
                add(issues, MinimapValidationCode.DUPLICATE_ENTRY_PATH, path,
                        "Runtime entry path is duplicated");
            }
        }
        return List.copyOf(issues);
    }

    public static List<MinimapValidationIssue> validate(MinimapDefinition definition) {
        List<MinimapValidationIssue> issues = new ArrayList<>();
        issues.addAll(validate(definition.manifest()));
        issues.addAll(validate(definition.document()));

        Set<String> floorIds = definition.document().floors().stream()
                .map(floor -> floor.selection().id())
                .collect(Collectors.toSet());
        Map<NamespacedId, MinimapStyle> stylesById = new HashMap<>();
        for (int index = 0; index < definition.styles().styles().size(); index++) {
            MinimapStyle style = definition.styles().styles().get(index);
            if (stylesById.putIfAbsent(style.id(), style) != null) {
                add(issues, MinimapValidationCode.DUPLICATE_STYLE_ID, "/styles/" + index + "/id",
                        "Style ID is duplicated");
            }
        }

        if (definition.regions().regions().size() > MinimapHardLimits.MAX_REGIONS) {
            add(issues, MinimapValidationCode.HARD_LIMIT_EXCEEDED, "/regions",
                    "Region count exceeds the hard limit");
        }
        Map<String, MinimapRegion> regionsById = new HashMap<>();
        long totalRegionVertices = 0;
        for (int index = 0; index < definition.regions().regions().size(); index++) {
            MinimapRegion region = definition.regions().regions().get(index);
            String path = "/regions/" + index;
            if (regionsById.putIfAbsent(region.id(), region) != null) {
                add(issues, MinimapValidationCode.DUPLICATE_REGION_ID, path + "/id",
                        "Region ID is duplicated");
            }
            if (!floorIds.contains(region.floorId())) {
                add(issues, MinimapValidationCode.MISSING_FLOOR_REFERENCE, path + "/floorId",
                        "Region floor does not exist");
            }
            MinimapStyle style = stylesById.get(region.styleId());
            if (style == null) {
                add(issues, MinimapValidationCode.MISSING_STYLE_REFERENCE, path + "/styleId",
                        "Region style does not exist");
            } else if (style.type() != com.phasetranscrystal.fpsmatch.core.minimap.model.StyleType.REGION) {
                add(issues, MinimapValidationCode.STYLE_TYPE_MISMATCH, path + "/styleId",
                        "Region must reference a region style");
            }
            validateRegionBounds(definition.document(), region, path, issues);
            if (region.geometry() instanceof PolygonGeometry polygon) {
                totalRegionVertices += polygon.vertices().size();
            }
        }
        if (totalRegionVertices > MinimapHardLimits.MAX_VECTOR_VERTICES) {
            add(issues, MinimapValidationCode.HARD_LIMIT_EXCEEDED, "/regions",
                    "Region vertex count exceeds the hard limit");
        }

        Set<String> connectionIds = new HashSet<>();
        for (int index = 0; index < definition.connections().connections().size(); index++) {
            MinimapFloorConnection connection = definition.connections().connections().get(index);
            String path = "/connections/" + index;
            if (!connectionIds.add(connection.id())) {
                add(issues, MinimapValidationCode.DUPLICATE_CONNECTION_ID, path + "/id",
                        "Connection ID is duplicated");
            }
            validateEndpoint(definition.document(), floorIds, connection.from(), path + "/from", issues);
            validateEndpoint(definition.document(), floorIds, connection.to(), path + "/to", issues);
        }

        for (int floorIndex = 0; floorIndex < definition.document().floors().size(); floorIndex++) {
            SourceFloor floor = definition.document().floors().get(floorIndex);
            for (int layerIndex = 0; layerIndex < floor.layers().size(); layerIndex++) {
                MinimapLayer layer = floor.layers().get(layerIndex);
                if (layer instanceof RegionVisualLayer regionLayer) {
                    for (int regionIndex = 0; regionIndex < regionLayer.regionIds().size(); regionIndex++) {
                        String regionId = regionLayer.regionIds().get(regionIndex);
                        String referencePath = "/floors/" + floorIndex + "/layers/" + layerIndex
                                + "/regionIds/" + regionIndex;
                        MinimapRegion region = regionsById.get(regionId);
                        if (region == null) {
                            add(issues, MinimapValidationCode.MISSING_REGION_REFERENCE,
                                    referencePath,
                                    "Region visual layer references a missing region");
                        } else if (!region.floorId().equals(floor.selection().id())) {
                            add(issues, MinimapValidationCode.REGION_LAYER_FLOOR_MISMATCH,
                                    referencePath,
                                    "Region visual layer references a region on another floor");
                        }
                    }
                }
            }
        }
        return List.copyOf(issues);
    }

    public static List<MinimapValidationIssue> validate(RuntimeDefinition definition) {
        List<MinimapValidationIssue> issues = new ArrayList<>();
        issues.addAll(validate(definition.manifest()));

        Set<String> floorIds = new HashSet<>();
        for (int index = 0; index < definition.manifest().floors().size(); index++) {
            RuntimeFloor floor = definition.manifest().floors().get(index);
            String path = "/floors/" + index;
            if (!floorIds.add(floor.selection().id())) {
                add(issues, MinimapValidationCode.DUPLICATE_FLOOR_ID, path + "/selection/id",
                        "Floor ID is duplicated");
            }
            floor.contentBounds().ifPresent(bounds -> validateCanvasRect(
                    definition.manifest().canvas(), bounds, path + "/contentBounds", issues
            ));
        }
        validateRuntimeFloorOverlap(definition.manifest().floors(), issues);

        Map<NamespacedId, RuntimeStyle> stylesById = new HashMap<>();
        for (int index = 0; index < definition.styles().styles().size(); index++) {
            RuntimeStyle style = definition.styles().styles().get(index);
            if (stylesById.putIfAbsent(style.id(), style) != null) {
                add(issues, MinimapValidationCode.DUPLICATE_STYLE_ID, "/styles/" + index + "/id",
                        "Style ID is duplicated");
            }
        }

        if (definition.regions().regions().size() > MinimapHardLimits.MAX_REGIONS) {
            add(issues, MinimapValidationCode.HARD_LIMIT_EXCEEDED, "/regions",
                    "Region count exceeds the hard limit");
        }
        Set<String> regionIds = new HashSet<>();
        long totalRegionVertices = 0;
        for (int index = 0; index < definition.regions().regions().size(); index++) {
            RuntimeRegion region = definition.regions().regions().get(index);
            String path = "/regions/" + index;
            if (!regionIds.add(region.id())) {
                add(issues, MinimapValidationCode.DUPLICATE_REGION_ID, path + "/id",
                        "Region ID is duplicated");
            }
            if (!floorIds.contains(region.floorId())) {
                add(issues, MinimapValidationCode.MISSING_FLOOR_REFERENCE, path + "/floorId",
                        "Region floor does not exist");
            }
            RuntimeStyle style = stylesById.get(region.styleId());
            if (style == null) {
                add(issues, MinimapValidationCode.MISSING_STYLE_REFERENCE, path + "/styleId",
                        "Region style does not exist");
            } else if (style.label().isEmpty()) {
                add(issues, MinimapValidationCode.MISSING_STYLE_APPEARANCE, path + "/styleId",
                        "Region style does not define a label appearance");
            }
            validateRegionBounds(
                    definition.manifest().canvas(), region.geometry(), region.labelAnchor(), path, issues
            );
            if (region.geometry() instanceof PolygonGeometry polygon) {
                totalRegionVertices += polygon.vertices().size();
            }
        }
        if (totalRegionVertices > MinimapHardLimits.MAX_VECTOR_VERTICES) {
            add(issues, MinimapValidationCode.HARD_LIMIT_EXCEEDED, "/regions",
                    "Region vertex count exceeds the hard limit");
        }

        Set<String> connectionIds = new HashSet<>();
        for (int index = 0; index < definition.connections().connections().size(); index++) {
            MinimapFloorConnection connection = definition.connections().connections().get(index);
            String path = "/connections/" + index;
            if (!connectionIds.add(connection.id())) {
                add(issues, MinimapValidationCode.DUPLICATE_CONNECTION_ID, path + "/id",
                        "Connection ID is duplicated");
            }
            validateEndpoint(definition.manifest().canvas(), floorIds, connection.from(), path + "/from", issues);
            validateEndpoint(definition.manifest().canvas(), floorIds, connection.to(), path + "/to", issues);
        }
        return List.copyOf(issues);
    }

    private static void validateRegionBounds(
            SourceDocument document,
            MinimapRegion region,
            String path,
            List<MinimapValidationIssue> issues
    ) {
        validateRegionBounds(document.canvas(), region.geometry(), region.labelAnchor(), path, issues);
    }

    private static void validateRegionBounds(
            CanvasBounds canvas,
            com.phasetranscrystal.fpsmatch.core.minimap.model.RegionGeometry geometry,
            com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint labelAnchor,
            String path,
            List<MinimapValidationIssue> issues
    ) {
        boolean geometryInside;
        if (geometry instanceof RectangleGeometry rectangle) {
            geometryInside = canvas.contains(rectangle.bounds());
        } else {
            PolygonGeometry polygon = (PolygonGeometry) geometry;
            geometryInside = polygon.vertices().stream().allMatch(canvas::contains);
        }
        if (!geometryInside) {
            add(issues, MinimapValidationCode.GEOMETRY_OUTSIDE_CANVAS, path + "/geometry",
                    "Region geometry is outside canvas bounds");
        }
        if (!canvas.contains(labelAnchor)) {
            add(issues, MinimapValidationCode.GEOMETRY_OUTSIDE_CANVAS, path + "/labelAnchor",
                    "Region label anchor is outside canvas bounds");
        }
    }

    private static void validateEndpoint(
            SourceDocument document,
            Set<String> floorIds,
            ConnectionEndpoint endpoint,
            String path,
            List<MinimapValidationIssue> issues
    ) {
        validateEndpoint(document.canvas(), floorIds, endpoint, path, issues);
    }

    private static void validateEndpoint(
            CanvasBounds canvas,
            Set<String> floorIds,
            ConnectionEndpoint endpoint,
            String path,
            List<MinimapValidationIssue> issues
    ) {
        if (!floorIds.contains(endpoint.floorId())) {
            add(issues, MinimapValidationCode.MISSING_FLOOR_REFERENCE, path + "/floorId",
                    "Connection floor does not exist");
        }
        if (!canvas.contains(endpoint.point())) {
            add(issues, MinimapValidationCode.GEOMETRY_OUTSIDE_CANVAS, path + "/point",
                    "Connection endpoint is outside canvas bounds");
        }
    }

    private static void validateFloor(
            SourceDocument document,
            SourceFloor floor,
            String floorPath,
            List<MinimapValidationIssue> issues
    ) {
        floor.contentBounds().ifPresent(bounds -> validateCanvasRect(
                document, bounds, floorPath + "/contentBounds", issues
        ));

        Set<String> layerIds = new HashSet<>();
        for (int layerIndex = 0; layerIndex < floor.layers().size(); layerIndex++) {
            MinimapLayer layer = floor.layers().get(layerIndex);
            String layerPath = floorPath + "/layers/" + layerIndex;
            if (!layerIds.add(layer.common().id())) {
                add(issues, MinimapValidationCode.DUPLICATE_LAYER_ID, layerPath + "/common/id",
                        "Layer ID is duplicated within its floor");
            }
            layer.common().clip().ifPresent(clip -> validateCanvasRect(
                    document, clip, layerPath + "/common/clip", issues
            ));
        }

        for (int pointIndex = 0; pointIndex < floor.calibration().controlPoints().size(); pointIndex++) {
            ControlPoint point = floor.calibration().controlPoints().get(pointIndex);
            String pointPath = floorPath + "/calibration/controlPoints/" + pointIndex;
            if (!document.worldBounds().contains(point.world())) {
                add(issues, MinimapValidationCode.CONTROL_POINT_OUTSIDE_WORLD, pointPath + "/world",
                        "Calibration world point is outside world bounds");
            }
            if (!document.canvas().contains(point.canvas())) {
                add(issues, MinimapValidationCode.CONTROL_POINT_OUTSIDE_CANVAS, pointPath + "/canvas",
                        "Calibration canvas point is outside canvas bounds");
            }
        }

        try {
            AffineFit fit = floor.calibration().fit();
            if (fit.hasResidualDegreesOfFreedom()
                    && fit.maxResidual() > floor.calibration().maxResidualPixels()) {
                add(issues, MinimapValidationCode.CALIBRATION_RESIDUAL_EXCEEDED,
                        floorPath + "/calibration", "Calibration residual exceeds the configured maximum");
            }
        } catch (IllegalArgumentException exception) {
            add(issues, MinimapValidationCode.CALIBRATION_INVALID,
                    floorPath + "/calibration", "Calibration does not define an allowed stable transform");
        }
    }

    private static void validateLayerOrder(
            Map<String, List<String>> layerOrder,
            SourceFloor floor,
            String floorPath,
            List<MinimapValidationIssue> issues
    ) {
        List<String> order = layerOrder.getOrDefault(floor.selection().id(), List.of());
        Set<String> uniqueOrder = new HashSet<>(order);
        if (uniqueOrder.size() != order.size()) {
            add(issues, MinimapValidationCode.LAYER_ORDER_DUPLICATE,
                    "/layerOrder/" + floor.selection().id(), "Layer order contains duplicate IDs");
        }
        Set<String> expected = floor.layers().stream()
                .map(layer -> layer.common().id())
                .collect(Collectors.toSet());
        if (order.size() != floor.layers().size() || !uniqueOrder.equals(expected)) {
            add(issues, MinimapValidationCode.LAYER_ORDER_NOT_PERMUTATION,
                    "/layerOrder/" + floor.selection().id(),
                    "Layer order must be a complete permutation of the floor layers");
        }
    }

    private static void validateFloorOverlap(
            List<SourceFloor> floors,
            List<MinimapValidationIssue> issues
    ) {
        for (int leftIndex = 0; leftIndex < floors.size(); leftIndex++) {
            MinimapFloor left = floors.get(leftIndex).selection();
            for (int rightIndex = leftIndex + 1; rightIndex < floors.size(); rightIndex++) {
                MinimapFloor right = floors.get(rightIndex).selection();
                if (!left.id().equals(right.id())
                        && left.autoPriority() == right.autoPriority()
                        && left.minY() < right.maxY()
                        && right.minY() < left.maxY()) {
                    add(issues, MinimapValidationCode.SAME_PRIORITY_FLOOR_OVERLAP,
                            "/floors/" + rightIndex + "/selection",
                            "Same-priority floor base range overlaps floor '" + left.id()
                                    + "' at index " + leftIndex);
                }
            }
        }
    }

    private static void validateRuntimeFloorOverlap(
            List<RuntimeFloor> floors,
            List<MinimapValidationIssue> issues
    ) {
        for (int leftIndex = 0; leftIndex < floors.size(); leftIndex++) {
            MinimapFloor left = floors.get(leftIndex).selection();
            for (int rightIndex = leftIndex + 1; rightIndex < floors.size(); rightIndex++) {
                MinimapFloor right = floors.get(rightIndex).selection();
                if (!left.id().equals(right.id())
                        && left.autoPriority() == right.autoPriority()
                        && left.minY() < right.maxY()
                        && right.minY() < left.maxY()) {
                    add(issues, MinimapValidationCode.SAME_PRIORITY_FLOOR_OVERLAP,
                            "/floors/" + rightIndex + "/selection",
                            "Same-priority floor base range overlaps floor '" + left.id()
                                    + "' at index " + leftIndex);
                }
            }
        }
    }

    private static void validateCanvasRect(
            SourceDocument document,
            CanvasRect rect,
            String path,
            List<MinimapValidationIssue> issues
    ) {
        validateCanvasRect(document.canvas(), rect, path, issues);
    }

    private static void validateCanvasRect(
            CanvasBounds canvas,
            CanvasRect rect,
            String path,
            List<MinimapValidationIssue> issues
    ) {
        if (!canvas.contains(rect)) {
            add(issues, MinimapValidationCode.BOUNDS_OUTSIDE_CANVAS, path,
                    "Rectangle is outside canvas bounds");
        }
    }

    private static void add(
            List<MinimapValidationIssue> issues,
            MinimapValidationCode code,
            String path,
            String message
    ) {
        issues.add(new MinimapValidationIssue(code, path, message));
    }
}
