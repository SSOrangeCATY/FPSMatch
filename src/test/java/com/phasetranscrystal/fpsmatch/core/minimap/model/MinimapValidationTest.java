package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidationCode;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidationIssue;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidator;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.AbstractList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapValidationTest {
    @Test
    void acceptsAConsistentSourceDocument() {
        assertTrue(MinimapValidator.validate(MinimapDefinitionCodecTest.validDocument()).isEmpty());
    }

    @Test
    void rejectsDuplicateFloorsAndInvalidLayerOrder() {
        SourceDocument valid = MinimapDefinitionCodecTest.validDocument();
        SourceFloor floor = valid.floors().get(0);
        SourceDocument invalid = new SourceDocument(
                valid.worldBounds(),
                valid.canvas(),
                valid.defaultViewMode(),
                List.of(floor, floor),
                Map.of("ground", List.of("imported", "imported"))
        );

        Set<MinimapValidationCode> codes = codes(MinimapValidator.validate(invalid));
        assertTrue(codes.contains(MinimapValidationCode.DUPLICATE_FLOOR_ID));
        assertTrue(codes.contains(MinimapValidationCode.LAYER_ORDER_DUPLICATE));
        assertTrue(codes.contains(MinimapValidationCode.LAYER_ORDER_NOT_PERMUTATION));
        assertFalse(codes.contains(MinimapValidationCode.SAME_PRIORITY_FLOOR_OVERLAP));
    }

    @Test
    void rejectsCanvasOverflowAndInvalidCalibration() {
        SourceDocument valid = MinimapDefinitionCodecTest.validDocument();
        SourceFloor source = valid.floors().get(0);
        LayerCommon clippedCommon = new LayerCommon(
                "paint",
                DisplayLabel.literal("Paint"),
                true,
                false,
                1,
                BlendMode.NORMAL,
                Optional.of(new CanvasRect(0, 0, 600, 512)),
                false
        );
        SourceFloor invalidFloor = new SourceFloor(
                source.selection(),
                source.label(),
                Optional.of(new CanvasRect(0, 0, 600, 512)),
                source.background(),
                new FloorCalibration(List.of(
                        point(0, 0, 0, 0),
                        point(100, 0, -100, 0),
                        point(0, 100, 0, 100)
                ), false, 2),
                List.of(new RasterPaintLayer(clippedCommon))
        );
        SourceDocument invalid = new SourceDocument(
                valid.worldBounds(),
                valid.canvas(),
                valid.defaultViewMode(),
                List.of(invalidFloor),
                Map.of("ground", List.of("paint"))
        );

        List<MinimapValidationIssue> issues = MinimapValidator.validate(invalid);
        Set<MinimapValidationCode> codes = codes(issues);
        assertTrue(codes.contains(MinimapValidationCode.BOUNDS_OUTSIDE_CANVAS));
        assertTrue(codes.contains(MinimapValidationCode.CALIBRATION_INVALID));
        assertTrue(issues.stream().allMatch(issue -> issue.path().startsWith("/")));
    }

    @Test
    void rejectsSamePriorityFloorOverlap() {
        SourceDocument valid = MinimapDefinitionCodecTest.validDocument();
        SourceFloor ground = valid.floors().get(0);
        SourceFloor overlapping = new SourceFloor(
                new MinimapFloor("upper", 10, 30, 0, 0.5, 1),
                DisplayLabel.literal("Upper"),
                ground.contentBounds(),
                ground.background(),
                ground.calibration(),
                List.of()
        );
        SourceDocument invalid = new SourceDocument(
                valid.worldBounds(),
                valid.canvas(),
                valid.defaultViewMode(),
                List.of(ground, overlapping),
                Map.of(
                        "ground", List.of("imported", "paint", "cutout"),
                        "upper", List.of()
                )
        );

        assertEquals(Set.of(MinimapValidationCode.SAME_PRIORITY_FLOOR_OVERLAP),
                codes(MinimapValidator.validate(invalid)));
    }

    @Test
    void rejectsInvalidRuntimeAggregate() {
        RuntimeFloor floor = new RuntimeFloor(
                new MinimapFloor("ground", -10, 20, 0, 0.5, 1),
                DisplayLabel.literal("Ground"),
                Optional.empty(),
                MinimapDefinitionCodecTest.approvedCalibration().fit().transform(),
                2
        );
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                Sha256.parse("1".repeat(64)),
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), MinimapFormatContract.CURRENT),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(floor, floor),
                256,
                List.of()
        );
        RuntimeStyle style = new RuntimeStyle(
                NamespacedId.parse("fpsmatch:site"), Optional.empty(), Optional.empty()
        );
        RuntimeRegion region = new RuntimeRegion(
                "site_a",
                "missing",
                DisplayLabel.literal("Bombsite A"),
                new RectangleGeometry(new CanvasRect(500, 500, 600, 600)),
                NamespacedId.parse("fpsmatch:bomb_site"),
                List.of(),
                Optional.empty(),
                NamespacedId.parse("fpsmatch:missing"),
                new CanvasPoint(600, 600),
                100,
                0,
                8
        );
        MinimapFloorConnection connection = new MinimapFloorConnection(
                "stairs",
                new ConnectionEndpoint("missing", new CanvasPoint(600, 600)),
                new ConnectionEndpoint("ground", new CanvasPoint(100, 100)),
                ConnectionType.STAIRS,
                ConnectionDisplayDirection.BIDIRECTIONAL,
                Optional.empty()
        );
        RuntimeDefinition invalid = new RuntimeDefinition(
                manifest,
                new RuntimeRegionsFile(List.of(region, region)),
                new ConnectionsFile(List.of(connection, connection)),
                new RuntimeStylesFile(List.of(style, style))
        );

        Set<MinimapValidationCode> validationCodes = codes(MinimapValidator.validate(invalid));
        assertTrue(validationCodes.contains(MinimapValidationCode.DUPLICATE_FLOOR_ID));
        assertTrue(validationCodes.contains(MinimapValidationCode.DUPLICATE_REGION_ID));
        assertTrue(validationCodes.contains(MinimapValidationCode.DUPLICATE_CONNECTION_ID));
        assertTrue(validationCodes.contains(MinimapValidationCode.DUPLICATE_STYLE_ID));
        assertTrue(validationCodes.contains(MinimapValidationCode.MISSING_FLOOR_REFERENCE));
        assertTrue(validationCodes.contains(MinimapValidationCode.MISSING_STYLE_REFERENCE));
        assertTrue(validationCodes.contains(MinimapValidationCode.GEOMETRY_OUTSIDE_CANVAS));
        assertFalse(validationCodes.contains(MinimapValidationCode.SAME_PRIORITY_FLOOR_OVERLAP));
    }

    @Test
    void rejectsReferencedRuntimeStyleWithoutLabelAppearance() {
        RuntimeFloor floor = new RuntimeFloor(
                new MinimapFloor("ground", -10, 20, 0, 0.5, 1),
                DisplayLabel.literal("Ground"),
                Optional.empty(),
                MinimapDefinitionCodecTest.approvedCalibration().fit().transform(),
                2
        );
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                Sha256.parse("1".repeat(64)),
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), MinimapFormatContract.CURRENT),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(floor),
                256,
                List.of()
        );
        NamespacedId styleId = NamespacedId.parse("fpsmatch:site");
        RuntimeRegion region = new RuntimeRegion(
                "site_a",
                "ground",
                DisplayLabel.literal("Bombsite A"),
                new RectangleGeometry(new CanvasRect(100, 100, 180, 180)),
                NamespacedId.parse("fpsmatch:bomb_site"),
                List.of(),
                Optional.empty(),
                styleId,
                new CanvasPoint(140, 140),
                100,
                0,
                8
        );
        RuntimeDefinition definition = new RuntimeDefinition(
                manifest,
                new RuntimeRegionsFile(List.of(region)),
                new ConnectionsFile(List.of()),
                new RuntimeStylesFile(List.of(new RuntimeStyle(
                        styleId,
                        Optional.empty(),
                        Optional.of(new IconAppearance(NamespacedId.parse("fpsmatch:icons/site_a"), 1))
                )))
        );

        assertEquals(Set.of(MinimapValidationCode.MISSING_STYLE_APPEARANCE),
                codes(MinimapValidator.validate(definition)));
    }

    @Test
    void rejectsRuntimeRegionVerticesAboveTheAggregateHardLimit() {
        CanvasPoint[] triangle = {
                new CanvasPoint(100, 100),
                new CanvasPoint(180, 100),
                new CanvasPoint(140, 180)
        };
        List<CanvasPoint> vertices = new AbstractList<>() {
            @Override
            public CanvasPoint get(int index) {
                return triangle[index % triangle.length];
            }

            @Override
            public int size() {
                return MinimapHardLimits.MAX_VECTOR_VERTICES + 1;
            }
        };
        RuntimeFloor floor = new RuntimeFloor(
                new MinimapFloor("ground", -10, 20, 0, 0.5, 1),
                DisplayLabel.literal("Ground"),
                Optional.empty(),
                MinimapDefinitionCodecTest.approvedCalibration().fit().transform(),
                2
        );
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                Sha256.parse("1".repeat(64)),
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), MinimapFormatContract.CURRENT),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(floor),
                256,
                List.of()
        );
        NamespacedId styleId = NamespacedId.parse("fpsmatch:site");
        RuntimeDefinition definition = new RuntimeDefinition(
                manifest,
                new RuntimeRegionsFile(List.of(new RuntimeRegion(
                        "site_a",
                        "ground",
                        DisplayLabel.literal("Bombsite A"),
                        new PolygonGeometry(vertices),
                        NamespacedId.parse("fpsmatch:bomb_site"),
                        List.of(),
                        Optional.empty(),
                        styleId,
                        new CanvasPoint(140, 140),
                        100,
                        0,
                        8
                ))),
                new ConnectionsFile(List.of()),
                new RuntimeStylesFile(List.of(new RuntimeStyle(
                        styleId,
                        Optional.of(new TextAppearance(new RgbaColor(255, 255, 255, 255), 1)),
                        Optional.empty()
                )))
        );

        assertEquals(Set.of(MinimapValidationCode.HARD_LIMIT_EXCEEDED),
                codes(MinimapValidator.validate(definition)));
    }

    private static Set<MinimapValidationCode> codes(List<MinimapValidationIssue> issues) {
        return issues.stream().map(MinimapValidationIssue::code).collect(Collectors.toSet());
    }

    private static ControlPoint point(double x, double z, double u, double v) {
        return new ControlPoint(new WorldPoint2D(x, z), new CanvasPoint(u, v));
    }
}
