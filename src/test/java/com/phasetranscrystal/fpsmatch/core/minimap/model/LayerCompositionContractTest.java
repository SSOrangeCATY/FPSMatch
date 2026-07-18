package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayerCompositionContractTest {
    @Test
    void freezesBlendModesAndLayerTypes() {
        assertEquals(List.of("NORMAL", "MULTIPLY", "SCREEN", "ADD"),
                Arrays.stream(BlendMode.values()).map(Enum::name).toList());
        assertEquals(List.of(
                        "IMPORTED_IMAGE",
                        "WORLD_BAKE",
                        "RASTER_PAINT",
                        "VECTOR",
                        "REGION_VISUAL",
                        "CUTOUT"
                ),
                Arrays.stream(LayerType.values()).map(Enum::name).toList());
    }

    @Test
    void normalLayersUseSourceOverAndCutoutUsesDstOut() {
        LayerCommon common = common("layer", BlendMode.NORMAL, false);
        List<MinimapLayer> sourceOver = List.of(
                new ImportedImageLayer(common, "asset"),
                new WorldBakeLayer(common, "generator"),
                new RasterPaintLayer(common),
                new VectorLayer(common, List.of("line")),
                new RegionVisualLayer(common, List.of("site_a"))
        );

        sourceOver.forEach(layer -> assertEquals(CompositionOperator.SOURCE_OVER, layer.operator()));
        assertEquals(CompositionOperator.DST_OUT, new CutoutLayer(common).operator());
    }

    @Test
    void cutoutCannotUseAMaskOrColorBlendMode() {
        assertThrows(IllegalArgumentException.class,
                () -> new CutoutLayer(common("cutout", BlendMode.MULTIPLY, false)));
        assertThrows(IllegalArgumentException.class,
                () -> new CutoutLayer(common("cutout", BlendMode.NORMAL, true)));
    }

    @Test
    void layerMetadataAndReferencesAreValidatedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new LayerCommon("Bad/ID", DisplayLabel.literal("Bad"), true, false,
                        1, BlendMode.NORMAL, Optional.empty(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new LayerCommon("bad", DisplayLabel.literal("Bad"), true, false,
                        1.01, BlendMode.NORMAL, Optional.empty(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new ImportedImageLayer(common("image", BlendMode.NORMAL, false), "Bad/Asset"));
        assertThrows(IllegalArgumentException.class,
                () -> new VectorLayer(common("vector", BlendMode.NORMAL, false), List.of("same", "same")));
    }

    @Test
    void everyLayerVariantRoundTripsThroughTheDispatchedCodec() {
        List<MinimapLayer> layers = List.of(
                new ImportedImageLayer(common("image", BlendMode.NORMAL, false), "asset"),
                new WorldBakeLayer(common("world", BlendMode.MULTIPLY, false), "generator"),
                new RasterPaintLayer(common("paint", BlendMode.SCREEN, true)),
                new VectorLayer(common("vector", BlendMode.ADD, false), List.of("line")),
                new RegionVisualLayer(common("regions", BlendMode.NORMAL, false), List.of("site_a")),
                new CutoutLayer(common("cutout", BlendMode.NORMAL, false))
        );

        for (MinimapLayer layer : layers) {
            JsonElement encoded = MinimapModelCodecs.LAYER.encodeStart(JsonOps.INSTANCE, layer)
                    .result().orElseThrow();
            assertEquals(layer, MinimapModelCodecs.LAYER.parse(JsonOps.INSTANCE, encoded)
                    .result().orElseThrow());
        }
    }

    static LayerCommon common(String id, BlendMode blendMode, boolean maskEnabled) {
        return new LayerCommon(
                id,
                DisplayLabel.literal(id),
                true,
                false,
                1.0,
                blendMode,
                Optional.empty(),
                maskEnabled
        );
    }
}
