package com.phasetranscrystal.fpsmatch.core.minimap.extension;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapExtensionRegistryTest {
    @AfterEach
    void clear() {
        MinimapExtensionRegistry.clearForTests();
    }

    @Test
    void rejectsDuplicateIdsAndSupportsLookup() {
        MinimapGameplayExtension first = new MinimapGameplayExtension() {
            @Override public String id() { return "test:a"; }
            @Override public boolean supports(MapKey mapKey) { return "cs".equals(mapKey.gameType()); }
        };
        MinimapExtensionRegistry.register(first);
        assertThrows(IllegalStateException.class, () -> MinimapExtensionRegistry.register(first));
        assertEquals(1, MinimapExtensionRegistry.supporting(new MapKey("cs", "m")).size());
        assertTrue(MinimapExtensionRegistry.supporting(new MapKey("other", "m")).isEmpty());
        assertEquals(List.of(), MinimapExtensionRegistry.markerProviders(new MapKey("cs", "m")));
    }

    @Test
    void supportsCsAndCsdmStyleKeysIndependently() {
        MinimapExtensionRegistry.register(new MinimapGameplayExtension() {
            @Override public String id() { return "test:bo"; }
            @Override public boolean supports(MapKey mapKey) {
                return "cs".equals(mapKey.gameType()) || "csdm".equals(mapKey.gameType());
            }
        });
        assertEquals(1, MinimapExtensionRegistry.supporting(new MapKey("cs", "dust2")).size());
        assertEquals(1, MinimapExtensionRegistry.supporting(new MapKey("csdm", "aim")).size());
        assertTrue(MinimapExtensionRegistry.supporting(new MapKey("lobby", "hub")).isEmpty());
    }

    @Test
    void mergesMarkerPresentationsByMapAndRejectsConflictingDeclarations() {
        MapKey mapKey = new MapKey("cs", "dust2");
        MarkerPresentation ally = presentation(
                "fpsmatch:type/player",
                "blockoffensive:style/ally",
                "blockoffensive:textures/minimap/markers/ally.png",
                "blockoffensive.minimap.marker.ally",
                1.0
        );
        MarkerPresentation c4 = presentation(
                "blockoffensive:type/c4",
                "blockoffensive:style/c4_dropped",
                "blockoffensive:textures/minimap/markers/c4_dropped.png",
                "blockoffensive.minimap.marker.c4_dropped",
                1.25
        );
        MinimapExtensionRegistry.register(extension("test:a", mapKey, List.of(c4, ally)));
        MinimapExtensionRegistry.register(extension("test:b", mapKey, List.of(ally)));

        assertEquals(
                List.of(c4, ally),
                MinimapExtensionRegistry.markerPresentations(mapKey)
        );
        assertEquals(
                ally,
                MinimapExtensionRegistry.markerPresentation(
                        mapKey, ally.typeId(), ally.styleId()
                ).orElseThrow()
        );
        assertTrue(MinimapExtensionRegistry.markerPresentations(
                new MapKey("other", "dust2")
        ).isEmpty());

        MarkerPresentation conflicting = presentation(
                "fpsmatch:type/player",
                "blockoffensive:style/ally",
                "blockoffensive:textures/minimap/markers/enemy.png",
                "blockoffensive.minimap.marker.enemy",
                1.0
        );
        MinimapExtensionRegistry.register(extension(
                "test:conflict", mapKey, List.of(conflicting)
        ));
        assertThrows(
                IllegalStateException.class,
                () -> MinimapExtensionRegistry.markerPresentations(mapKey)
        );
    }

    private static MinimapGameplayExtension extension(
            String id,
            MapKey supported,
            List<MarkerPresentation> presentations
    ) {
        return new MinimapGameplayExtension() {
            @Override public String id() { return id; }
            @Override public boolean supports(MapKey mapKey) { return supported.equals(mapKey); }
            @Override public List<MarkerPresentation> markerPresentations(MapKey mapKey) {
                return presentations;
            }
        };
    }

    private static MarkerPresentation presentation(
            String typeId,
            String styleId,
            String textureId,
            String translationKey,
            double scale
    ) {
        return new MarkerPresentation(
                NamespacedId.parse(typeId),
                NamespacedId.parse(styleId),
                NamespacedId.parse(textureId),
                DisplayLabel.translation(translationKey),
                scale
        );
    }
}
