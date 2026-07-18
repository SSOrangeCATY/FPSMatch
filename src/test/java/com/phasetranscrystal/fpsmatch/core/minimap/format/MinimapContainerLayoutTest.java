package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapContainerLayoutTest {
    @Test
    void exposesTheFixedManifestJsonAndThumbnailEntrypoints() {
        assertEquals(ContainerPath.parse("manifest.json"), MinimapContainerLayout.SOURCE_MANIFEST);
        assertEquals(ContainerPath.parse("runtime-manifest.json"), MinimapContainerLayout.RUNTIME_MANIFEST);
        assertEquals(Set.of(
                ContainerPath.parse("manifest.json"),
                ContainerPath.parse("document.json"),
                ContainerPath.parse("regions.json"),
                ContainerPath.parse("connections.json"),
                ContainerPath.parse("styles.json"),
                ContainerPath.parse("generators.json"),
                ContainerPath.parse("vectors.json"),
                ContainerPath.parse("thumbnail.png")
        ), MinimapContainerLayout.SOURCE_FIXED_PATHS);
        assertEquals(Set.of(
                ContainerPath.parse("runtime-manifest.json"),
                ContainerPath.parse("regions-runtime.json"),
                ContainerPath.parse("connections.json"),
                ContainerPath.parse("styles-runtime.json"),
                ContainerPath.parse("thumbnail.png")
        ), MinimapContainerLayout.RUNTIME_FIXED_PATHS);
        assertEquals(Set.of(
                ContainerPath.parse("manifest.json"),
                ContainerPath.parse("document.json"),
                ContainerPath.parse("regions.json"),
                ContainerPath.parse("connections.json"),
                ContainerPath.parse("styles.json"),
                ContainerPath.parse("generators.json"),
                ContainerPath.parse("vectors.json")
        ), MinimapContainerLayout.SOURCE_JSON_PATHS);
        assertEquals(Set.of(
                ContainerPath.parse("runtime-manifest.json"),
                ContainerPath.parse("regions-runtime.json"),
                ContainerPath.parse("connections.json"),
                ContainerPath.parse("styles-runtime.json")
        ), MinimapContainerLayout.RUNTIME_JSON_PATHS);
        assertTrue(MinimapContainerLayout.isSourceJson(path("document.json")));
        assertTrue(MinimapContainerLayout.isRuntimeJson(path("styles-runtime.json")));
        assertFalse(MinimapContainerLayout.isSourceJson(path("thumbnail.png")));
        assertFalse(MinimapContainerLayout.isRuntimeJson(path("floors/ground/tiles/0/0_0.png")));
    }

    @Test
    void acceptsOnlyTheSourcePathGrammar() {
        assertTrue(MinimapContainerLayout.isSourcePath(path("manifest.json")));
        assertTrue(MinimapContainerLayout.isSourcePath(path("document.json")));
        assertTrue(MinimapContainerLayout.isSourcePath(path("floors/ground/layers/world_base/tiles/0_12.png")));
        assertTrue(MinimapContainerLayout.isSourcePath(path("floors/ground/layers/paint/mask/12_0.png")));
        assertTrue(MinimapContainerLayout.isSourcePath(path("assets/images/overview/tiles/3_7.png")));
        assertTrue(MinimapContainerLayout.isSourcePath(path("thumbnail.png")));

        assertFalse(MinimapContainerLayout.isSourcePath(path("floors/ground/tiles/0/0_0.png")));
        assertFalse(MinimapContainerLayout.isSourcePath(path("floors/ground/layers/world_base/tiles/00_0.png")));
        assertFalse(MinimapContainerLayout.isSourcePath(path("floors/ground/layers/world_base/tiles/-1_0.png")));
        assertThrows(IllegalArgumentException.class,
                () -> path("floors/ground/layers/world_base/tiles/0_0.PNG"));
        assertThrows(IllegalArgumentException.class,
                () -> path("floors/Upper/layers/world_base/tiles/0_0.png"));
        assertFalse(MinimapContainerLayout.isSourcePath(path("floors/ground/layers/world_base/tiles/0.png")));
        assertFalse(MinimapContainerLayout.isSourcePath(path("source-only.json")));
    }

    @Test
    void acceptsOnlyTheRuntimePathGrammar() {
        assertTrue(MinimapContainerLayout.isRuntimePath(path("runtime-manifest.json")));
        assertTrue(MinimapContainerLayout.isRuntimePath(path("floors/ground/tiles/0/0_12.png")));
        assertTrue(MinimapContainerLayout.isRuntimePath(path("floors/ground/tiles/12/12_0.png")));
        assertTrue(MinimapContainerLayout.isRuntimePath(path("regions-runtime.json")));
        assertTrue(MinimapContainerLayout.isRuntimePath(path("connections.json")));
        assertTrue(MinimapContainerLayout.isRuntimePath(path("styles-runtime.json")));
        assertTrue(MinimapContainerLayout.isRuntimePath(path("thumbnail.png")));

        assertFalse(MinimapContainerLayout.isRuntimePath(path("manifest.json")));
        assertFalse(MinimapContainerLayout.isRuntimePath(path("document.json")));
        assertFalse(MinimapContainerLayout.isRuntimePath(path("floors/ground/layers/world_base/tiles/0_0.png")));
        assertFalse(MinimapContainerLayout.isRuntimePath(path("assets/images/overview/tiles/0_0.png")));
        assertFalse(MinimapContainerLayout.isRuntimePath(path("floors/ground/tiles/00/0_0.png")));
        assertFalse(MinimapContainerLayout.isRuntimePath(path("floors/ground/tiles/-1/0_0.png")));
        assertThrows(IllegalArgumentException.class, () -> path("floors/ground/tiles/0/0_0.PNG"));
        assertFalse(MinimapContainerLayout.isRuntimePath(path("floors/ground/tiles/0/0_0.png/extra")));
    }

    @Test
    void identifiesSourceOnlyEntriesThatRuntimeCompilationMustExclude() {
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("manifest.json")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("document.json")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("regions.json")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("styles.json")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("generators.json")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("vectors.json")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("floors/ground/layers/world_base/tiles/0_0.png")));
        assertTrue(MinimapContainerLayout.isSourceOnlyPath(path("assets/images/overview/tiles/0_0.png")));

        assertFalse(MinimapContainerLayout.isSourceOnlyPath(path("connections.json")));
        assertFalse(MinimapContainerLayout.isSourceOnlyPath(path("thumbnail.png")));
        assertFalse(MinimapContainerLayout.isSourceOnlyPath(path("floors/ground/tiles/0/0_0.png")));
        assertFalse(MinimapContainerLayout.isSourceOnlyPath(path("unknown.bin")));
    }

    @Test
    void classifiesEntriesForReaderAndCompilerAllowLists() {
        assertEquals(MinimapContainerLayout.SourceEntryKind.MANIFEST,
                MinimapContainerLayout.classifySource(path("manifest.json")).orElseThrow());
        assertEquals(MinimapContainerLayout.SourceEntryKind.LAYER_TILE,
                MinimapContainerLayout.classifySource(path("floors/ground/layers/base/tiles/1_2.png")).orElseThrow());
        assertEquals(MinimapContainerLayout.SourceEntryKind.LAYER_MASK,
                MinimapContainerLayout.classifySource(path("floors/ground/layers/base/mask/1_2.png")).orElseThrow());
        assertEquals(MinimapContainerLayout.SourceEntryKind.ASSET_TILE,
                MinimapContainerLayout.classifySource(path("assets/images/base/tiles/1_2.png")).orElseThrow());
        assertEquals(MinimapContainerLayout.RuntimeEntryKind.FLOOR_TILE,
                MinimapContainerLayout.classifyRuntime(path("floors/ground/tiles/1/1_2.png")).orElseThrow());
        assertTrue(MinimapContainerLayout.classifyRuntime(path("document.json")).isEmpty());
        assertTrue(MinimapContainerLayout.classifySource(path("regions-runtime.json")).isEmpty());
    }

    @Test
    void parsesCoordinatesWithoutAcceptingIntegerOverflow() {
        MinimapContainerLayout.SourceTileAddress source =
                MinimapContainerLayout.parseSourceTile(
                        path("floors/ground/layers/base/tiles/12_3.png")
                ).orElseThrow();
        assertEquals(12, source.x());
        assertEquals(3, source.y());
        MinimapContainerLayout.RuntimeTileAddress runtime =
                MinimapContainerLayout.parseRuntimeTile(
                        path("floors/ground/tiles/4/12_3.png")
                ).orElseThrow();
        assertEquals(4, runtime.zoom());
        assertTrue(MinimapContainerLayout.parseRuntimeTile(
                path("floors/ground/tiles/999999999999999999999/0_0.png")
        ).isEmpty());
    }

    private static ContainerPath path(String value) {
        return ContainerPath.parse(value);
    }
}
