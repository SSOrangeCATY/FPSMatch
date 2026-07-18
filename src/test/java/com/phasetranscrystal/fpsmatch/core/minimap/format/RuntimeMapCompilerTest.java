package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FillStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyleOverride;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StrokeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMapCompilerTest {
    @Test
    void compilesOnlyExplicitRuntimeEntriesAndMaintainsTheHashChain() throws Exception {
        MinimapDefinition definition = definitionWithRegions();
        byte[] sourceBytes = SourceMapWriter.write(definition);
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            RuntimeCompileRequest request = new RuntimeCompileRequest(
                    source.manifest().revision(),
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                            NamespacedId.parse("fpsmatch:test-compiler"),
                            MinimapFormatContract.CURRENT
                    ),
                    MinimapContainerFixtures.fullRuntimeTiles()
            );

            CompiledMapPair pair = RuntimeMapCompiler.compile(source, request);
            try (RuntimeMap runtime = RuntimeMapReader.read(pair.runtimeBytes())) {
                CompiledMapPair.verifyBinding(source, runtime);
            }
            assertEquals(source.sourceHash(), pair.sourceHash());
            assertEquals(source.manifest().documentId(), pair.runtimeManifest().documentId());
            assertEquals(source.manifest().binding(), pair.runtimeManifest().binding());
            assertEquals(source.manifest().revision(), pair.runtimeManifest().publishRevision());
            assertEquals(source.sourceHash(), pair.runtimeManifest().sourceHash());
            assertEquals(Sha256Digest.of(pair.runtimeManifestBytes()), pair.runtimeHash());
            assertEquals(Sha256Digest.of(pair.runtimeBytes()), pair.runtimeContainerHash());

            try (RuntimeMap runtime = RuntimeMapReader.read(pair.runtimeBytes())) {
                assertEquals(pair.runtimeHash(), runtime.runtimeHash());
                assertEquals(pair.runtimeContainerHash(), runtime.containerHash());
                assertTrue(runtime.paths().contains(
                        ContainerPath.parse("floors/ground/tiles/0/0_0.png")
                ));
                assertFalse(runtime.paths().contains(ContainerPath.parse("document.json")));
                assertFalse(runtime.paths().contains(ContainerPath.parse("generators.json")));
                assertFalse(runtime.paths().contains(ContainerPath.parse("floors/ground/layers/base/tiles/0_0.png")));
                assertFalse(runtime.paths().contains(MinimapContainerLayout.RUNTIME_MANIFEST)
                        && runtime.manifest().entries().stream().anyMatch(
                        entry -> entry.path().equals(MinimapContainerLayout.RUNTIME_MANIFEST)));
            }
        }
    }

    @Test
    void rejectsPublishRevisionThatDoesNotMatchTheSourceManifest() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            RuntimeCompileRequest request = new RuntimeCompileRequest(
                    source.manifest().revision() + 1,
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                            NamespacedId.parse("fpsmatch:test-compiler"),
                            MinimapFormatContract.CURRENT
                    ),
                    MinimapContainerFixtures.fullRuntimeTiles()
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    ContainerValidationException.class,
                    () -> RuntimeMapCompiler.compile(source, request)
            );
        }
    }

    @Test
    void rejectsMissingRuntimeTilesAndIncorrectEdgeDimensions() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile profile =
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                            NamespacedId.parse("fpsmatch:test-compiler"),
                            MinimapFormatContract.CURRENT
                    );
            RuntimeCompileRequest missing = new RuntimeCompileRequest(
                    source.manifest().revision(), profile, List.of()
            );
            RuntimeCompileRequest wrongDimensions = new RuntimeCompileRequest(
                    source.manifest().revision(), profile,
                    List.of(new CanonicalZipWriter.Entry(
                            ContainerPath.parse("floors/ground/tiles/0/0_0.png"),
                            CanonicalPngCodecV1.encode(1, 1, new byte[4])
                    ))
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    ContainerValidationException.class,
                    () -> RuntimeMapCompiler.compile(source, missing)
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    ContainerValidationException.class,
                    () -> RuntimeMapCompiler.compile(source, wrongDimensions)
            );
        }
    }

    @Test
    void rejectsRuntimeDocumentOrBindingThatDoesNotMatchTheSourceManifest() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile profile =
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                            NamespacedId.parse("fpsmatch:test-compiler"),
                            MinimapFormatContract.CURRENT
                    );
            RuntimeCompileRequest wrongDocument = new RuntimeCompileRequest(
                    NamespacedId.parse("fpsmatch:other"), source.manifest().binding(),
                    source.manifest().revision(), profile, source.manifest().tileEdge(), 1,
                    List.of(), Optional.empty()
            );
            RuntimeCompileRequest wrongBinding = new RuntimeCompileRequest(
                    source.manifest().documentId(),
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey(
                            "fpsmatch:other", "Other"
                    ),
                    source.manifest().revision(), profile, source.manifest().tileEdge(), 1,
                    List.of(), Optional.empty()
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    ContainerValidationException.class,
                    () -> RuntimeMapCompiler.compile(source, wrongDocument)
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    ContainerValidationException.class,
                    () -> RuntimeMapCompiler.compile(source, wrongBinding)
            );
        }
    }

    @Test
    void resolvesAndDeduplicatesEffectiveRegionLabelAppearances() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(definitionWithRegions());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            RuntimeCompileRequest request = new RuntimeCompileRequest(
                    source.manifest().revision(),
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                            NamespacedId.parse("fpsmatch:test-compiler"),
                            MinimapFormatContract.CURRENT
                    ),
                    MinimapContainerFixtures.fullRuntimeTiles()
            );
            RuntimeDefinition runtime = RuntimeMapCompiler.compile(source, request)
                    .runtimeDefinition();
            List<RuntimeRegion> regions = runtime.regions().regions();
            assertEquals(2, regions.size());
            assertEquals(regions.get(0).styleId(), regions.get(1).styleId());
            assertTrue(regions.get(0).styleId().toString().startsWith(
                    "fpsmatch:resolved-region/"
            ));
            assertEquals(
                    NamespacedId.parse(
                            "fpsmatch:resolved-region/a1566849a32c877ce23d84fab3facc342f5f1abb2537f87733b2acf69c9aa18d"
                    ),
                    regions.get(0).styleId()
            );
            List<RuntimeStyle> generated = runtime.styles().styles().stream()
                    .filter(style -> style.id().equals(regions.get(0).styleId()))
                    .toList();
            assertEquals(1, generated.size());
            assertEquals(Optional.of(new TextAppearance(new RgbaColor(1, 2, 3, 255), 1)),
                    generated.get(0).label());
        }
    }

    private static MinimapDefinition definitionWithRegions() {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        NamespacedId styleId = NamespacedId.parse("fpsmatch:region");
        RegionStyle style = new RegionStyle(
                styleId,
                new FillStyle(new RgbaColor(10, 20, 30, 255), 0.5),
                new StrokeStyle(new RgbaColor(40, 50, 60, 255), 1, 1),
                new TextAppearance(new RgbaColor(100, 100, 100, 255), 1)
        );
        RegionStyleOverride override = new RegionStyleOverride(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new TextAppearance(new RgbaColor(1, 2, 3, 255), 1))
        );
        MinimapRegion first = region("a", override, styleId);
        MinimapRegion second = region("b", override, styleId);
        return new MinimapDefinition(
                base.manifest(), base.document(),
                new com.phasetranscrystal.fpsmatch.core.minimap.model.RegionsFile(List.of(first, second)),
                base.connections(), new StylesFile(List.of(style))
        );
    }

    private static MinimapRegion region(
            String id,
            RegionStyleOverride override,
            NamespacedId styleId
    ) {
        return new MinimapRegion(
                id,
                "ground",
                DisplayLabel.literal(id),
                new RectangleGeometry(new CanvasRect(10, 10, 20, 20)),
                NamespacedId.parse("fpsmatch:area"),
                List.of(),
                Optional.empty(),
                styleId,
                override,
                new CanvasPoint(15, 15),
                0,
                0,
                8
        );
    }
}
