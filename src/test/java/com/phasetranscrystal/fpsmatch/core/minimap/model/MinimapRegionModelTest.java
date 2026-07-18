package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidationCode;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapRegionModelTest {
    @Test
    void rectangleAndPolygonUseDeterministicInclusiveHitTesting() {
        RegionGeometry rectangle = new RectangleGeometry(new CanvasRect(10, 20, 30, 40));
        assertTrue(rectangle.contains(new CanvasPoint(10, 20)));
        assertTrue(rectangle.contains(new CanvasPoint(30, 40)));
        assertFalse(rectangle.contains(new CanvasPoint(30.01, 40)));

        RegionGeometry polygon = new PolygonGeometry(List.of(
                new CanvasPoint(0, 0),
                new CanvasPoint(10, 0),
                new CanvasPoint(10, 10),
                new CanvasPoint(0, 10)
        ));
        assertTrue(polygon.contains(new CanvasPoint(5, 5)));
        assertTrue(polygon.contains(new CanvasPoint(10, 5)));
        assertFalse(polygon.contains(new CanvasPoint(11, 5)));
        assertThrows(IllegalArgumentException.class, () -> new PolygonGeometry(List.of(
                new CanvasPoint(0, 0), new CanvasPoint(1, 1), new CanvasPoint(2, 2)
        )));
    }

    @Test
    void regionConnectionAndStyleFilesRoundTripIndependently() {
        MinimapRegion region = region("site_a", "ground", NamespacedId.parse("fpsmatch:site"));
        RegionsFile regions = new RegionsFile(List.of(region));
        MinimapFloorConnection connection = new MinimapFloorConnection(
                "stairs_a",
                new ConnectionEndpoint("ground", new CanvasPoint(100, 100)),
                new ConnectionEndpoint("upper", new CanvasPoint(120, 120)),
                ConnectionType.STAIRS,
                ConnectionDisplayDirection.BIDIRECTIONAL,
                Optional.of(DisplayLabel.literal("Stairs"))
        );
        ConnectionsFile connections = new ConnectionsFile(List.of(connection));
        StylesFile styles = new StylesFile(List.of(
                style(),
                new LineStyle(NamespacedId.parse("fpsmatch:route"),
                        new StrokeStyle(new RgbaColor(10, 20, 30, 255), 1, 1)),
                new TextStyle(NamespacedId.parse("fpsmatch:label"),
                        new TextAppearance(new RgbaColor(255, 255, 255, 255), 1)),
                new IconStyle(NamespacedId.parse("fpsmatch:icon"),
                        new IconAppearance(NamespacedId.parse("fpsmatch:icons/site_a"), 1))
        ));

        assertRoundTrip(MinimapModelCodecs.REGIONS, regions);
        assertRoundTrip(MinimapModelCodecs.CONNECTIONS, connections);
        assertRoundTrip(MinimapModelCodecs.STYLES, styles);

        JsonElement regionJson = MinimapModelCodecs.REGIONS.encodeStart(JsonOps.INSTANCE, regions)
                .result().orElseThrow();
        assertEquals("rectangle", regionJson.getAsJsonObject().getAsJsonArray("regions").get(0)
                .getAsJsonObject().getAsJsonObject("geometry").get("type").getAsString());
        JsonElement styleJson = MinimapModelCodecs.STYLES.encodeStart(JsonOps.INSTANCE, styles)
                .result().orElseThrow();
        assertEquals("region", styleJson.getAsJsonObject().getAsJsonArray("styles").get(0)
                .getAsJsonObject().get("type").getAsString());
        assertEquals(List.of("REGION", "LINE", "TEXT", "ICON"),
                Arrays.stream(StyleType.values()).map(Enum::name).toList());
    }

    @Test
    void polygonRegionRoundTripsThroughGeometryDispatch() {
        MinimapRegion polygon = new MinimapRegion(
                "site_a",
                "ground",
                DisplayLabel.literal("Bombsite A"),
                new PolygonGeometry(List.of(
                        new CanvasPoint(100, 100),
                        new CanvasPoint(180, 100),
                        new CanvasPoint(140, 180)
                )),
                NamespacedId.parse("fpsmatch:bomb_site"),
                List.of(),
                Optional.empty(),
                NamespacedId.parse("fpsmatch:site"),
                RegionStyleOverride.empty(),
                new CanvasPoint(140, 140),
                100,
                0.25,
                8
        );

        assertRoundTrip(MinimapModelCodecs.REGIONS, new RegionsFile(List.of(polygon)));
    }

    @Test
    void aggregateDefinitionHasNoThirdAuthorityCodec() {
        MinimapDefinition definition = new MinimapDefinition(
                sourceManifest(),
                MinimapDefinitionCodecTest.validDocument(),
                new RegionsFile(List.of()),
                new ConnectionsFile(List.of()),
                new StylesFile(List.of())
        );
        assertEquals("fpsmatch:dust2", definition.manifest().documentId().toString());
        assertThrows(NoSuchFieldException.class, () -> MinimapDefinition.class.getField("CODEC"));
    }

    @Test
    void aggregateValidationRejectsMissingFloorAndStyleReferences() {
        MinimapRegion invalidRegion = region("site_a", "missing", NamespacedId.parse("fpsmatch:missing"));
        MinimapFloorConnection invalidConnection = new MinimapFloorConnection(
                "stairs_a",
                new ConnectionEndpoint("ground", new CanvasPoint(100, 100)),
                new ConnectionEndpoint("missing", new CanvasPoint(120, 120)),
                ConnectionType.STAIRS,
                ConnectionDisplayDirection.BIDIRECTIONAL,
                Optional.empty()
        );
        MinimapDefinition definition = new MinimapDefinition(
                sourceManifest(),
                MinimapDefinitionCodecTest.validDocument(),
                new RegionsFile(List.of(invalidRegion)),
                new ConnectionsFile(List.of(invalidConnection)),
                new StylesFile(List.of(style()))
        );

        Set<MinimapValidationCode> codes = MinimapValidator.validate(definition).stream()
                .map(issue -> issue.code()).collect(Collectors.toSet());
        assertTrue(codes.contains(MinimapValidationCode.MISSING_FLOOR_REFERENCE));
        assertTrue(codes.contains(MinimapValidationCode.MISSING_STYLE_REFERENCE));
    }

    @Test
    void aggregateValidationRejectsNonRegionStyleReferences() {
        NamespacedId sharedId = NamespacedId.parse("fpsmatch:site");
        MinimapDefinition definition = new MinimapDefinition(
                sourceManifest(),
                MinimapDefinitionCodecTest.validDocument(),
                new RegionsFile(List.of(region("site_a", "ground", sharedId))),
                new ConnectionsFile(List.of()),
                new StylesFile(List.of(new TextStyle(
                        sharedId,
                        new TextAppearance(new RgbaColor(255, 255, 255, 255), 1)
                )))
        );

        assertEquals(Set.of(MinimapValidationCode.STYLE_TYPE_MISMATCH),
                MinimapValidator.validate(definition).stream()
                        .map(issue -> issue.code()).collect(Collectors.toSet()));
    }

    @Test
    void aggregateValidationRejectsRegionVisualReferencesAcrossFloors() {
        SourceDocument valid = MinimapDefinitionCodecTest.validDocument();
        SourceFloor originalGround = valid.floors().get(0);
        RegionVisualLayer regionLayer = new RegionVisualLayer(
                LayerCompositionContractTest.common("regions", BlendMode.NORMAL, false),
                List.of("site_a")
        );
        SourceFloor ground = new SourceFloor(
                originalGround.selection(),
                originalGround.label(),
                originalGround.contentBounds(),
                originalGround.background(),
                originalGround.calibration(),
                List.of(regionLayer)
        );
        SourceFloor upper = new SourceFloor(
                new MinimapFloor("upper", 20, 40, 0, 0.5, 1),
                DisplayLabel.literal("Upper"),
                originalGround.contentBounds(),
                originalGround.background(),
                originalGround.calibration(),
                List.of()
        );
        SourceDocument document = new SourceDocument(
                valid.worldBounds(),
                valid.canvas(),
                valid.defaultViewMode(),
                List.of(ground, upper),
                Map.of("ground", List.of("regions"), "upper", List.of())
        );
        MinimapDefinition definition = new MinimapDefinition(
                sourceManifest(),
                document,
                new RegionsFile(List.of(region("site_a", "upper", NamespacedId.parse("fpsmatch:site")))),
                new ConnectionsFile(List.of()),
                new StylesFile(List.of(style()))
        );

        var issues = MinimapValidator.validate(definition);
        assertEquals(Set.of(MinimapValidationCode.REGION_LAYER_FLOOR_MISMATCH),
                issues.stream().map(issue -> issue.code()).collect(Collectors.toSet()));
        assertEquals("/floors/0/layers/0/regionIds/0", issues.get(0).path());
    }

    private static MinimapRegion region(String id, String floorId, NamespacedId styleId) {
        return new MinimapRegion(
                id,
                floorId,
                DisplayLabel.literal("Bombsite A"),
                new RectangleGeometry(new CanvasRect(100, 100, 180, 180)),
                NamespacedId.parse("fpsmatch:bomb_site"),
                List.of(NamespacedId.parse("fpsmatch:objective")),
                Optional.of(NamespacedId.parse("blockoffensive:site_a")),
                styleId,
                RegionStyleOverride.empty(),
                new CanvasPoint(140, 140),
                100,
                0.25,
                8
        );
    }

    private static RegionStyle style() {
        return new RegionStyle(
                NamespacedId.parse("fpsmatch:site"),
                new FillStyle(new RgbaColor(200, 180, 40, 255), 0.4),
                new StrokeStyle(new RgbaColor(255, 255, 255, 255), 2, 0.9),
                new TextAppearance(new RgbaColor(255, 255, 255, 255), 1)
        );
    }

    private static SourceManifest sourceManifest() {
        return new SourceManifest(
                com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                NamespacedId.parse("minecraft:overworld"),
                Optional.empty(),
                256,
                List.of()
        );
    }

    private static <T> void assertRoundTrip(com.mojang.serialization.Codec<T> codec, T value) {
        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow();
        assertEquals(value, codec.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }
}
