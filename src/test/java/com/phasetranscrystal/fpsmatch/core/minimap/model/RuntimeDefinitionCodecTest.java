package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeDefinitionCodecTest {
    @Test
    void runtimeRegionAndStyleRoundTripWithoutBakedVisualSource() {
        RuntimeRegion region = new RuntimeRegion(
                "site_a",
                "ground",
                DisplayLabel.literal("Bombsite A"),
                new RectangleGeometry(new CanvasRect(100, 100, 180, 180)),
                NamespacedId.parse("fpsmatch:bomb_site"),
                List.of(NamespacedId.parse("fpsmatch:objective")),
                Optional.of(NamespacedId.parse("blockoffensive:site_a")),
                NamespacedId.parse("fpsmatch:site"),
                new CanvasPoint(140, 140),
                100,
                0.25,
                8
        );
        RuntimeRegionsFile regions = new RuntimeRegionsFile(List.of(region));
        RuntimeStylesFile styles = new RuntimeStylesFile(List.of(new RuntimeStyle(
                NamespacedId.parse("fpsmatch:site"),
                Optional.of(new TextAppearance(new RgbaColor(255, 255, 255, 255), 1)),
                Optional.of(new IconAppearance(NamespacedId.parse("fpsmatch:icons/site_a"), 1))
        )));

        JsonElement encodedRegions = MinimapModelCodecs.RUNTIME_REGIONS
                .encodeStart(JsonOps.INSTANCE, regions).result().orElseThrow();
        JsonObject encodedRegion = encodedRegions.getAsJsonObject().getAsJsonArray("regions")
                .get(0).getAsJsonObject();
        assertEquals(Set.of(
                "id", "floorId", "label", "geometry", "semanticType", "tags",
                "gameplayReference", "styleId", "labelAnchor", "priority",
                "minVisibleScale", "maxVisibleScale"
        ), encodedRegion.keySet());
        assertFalse(encodedRegion.has("styleOverride"));
        assertFalse(encodedRegion.has("fill"));
        assertFalse(encodedRegion.has("stroke"));
        assertEquals(regions, MinimapModelCodecs.RUNTIME_REGIONS
                .parse(JsonOps.INSTANCE, encodedRegions).result().orElseThrow());

        JsonElement encodedStyles = MinimapModelCodecs.RUNTIME_STYLES
                .encodeStart(JsonOps.INSTANCE, styles).result().orElseThrow();
        assertEquals(Set.of("id", "label", "icon"), encodedStyles.getAsJsonObject()
                .getAsJsonArray("styles").get(0).getAsJsonObject().keySet());
        assertEquals(styles, MinimapModelCodecs.RUNTIME_STYLES
                .parse(JsonOps.INSTANCE, encodedStyles).result().orElseThrow());
    }

    @Test
    void runtimeAggregateIsAReadOnlyViewWithoutCodec() {
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:dust2"),
                new MapKey("cs", "de_dust2"),
                1,
                Sha256.parse("1".repeat(64)),
                new CompilerProfile(NamespacedId.parse("fpsmatch:canonical"), MinimapFormatContract.CURRENT),
                new CanvasBounds(512, 512),
                DefaultViewMode.FULL_MAP,
                List.of(),
                256,
                List.of()
        );
        RuntimeDefinition definition = new RuntimeDefinition(
                manifest,
                new RuntimeRegionsFile(List.of()),
                new ConnectionsFile(List.of()),
                new RuntimeStylesFile(List.of())
        );

        assertEquals(manifest, definition.manifest());
        assertThrows(NoSuchFieldException.class, () -> RuntimeDefinition.class.getField("CODEC"));
    }
}
