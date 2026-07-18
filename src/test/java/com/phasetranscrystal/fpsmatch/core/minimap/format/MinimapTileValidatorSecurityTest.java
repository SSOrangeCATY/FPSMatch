package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineTransform2D;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DefaultViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldBounds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MinimapTileValidatorSecurityTest {
    @Test
    void rejectsRuntimeCoverageBeforeEnumeratingAnUnrepresentableGrid() {
        RuntimeFloor floor = new RuntimeFloor(
                new MinimapFloor("ground", 0, 32, 0, 0, 0),
                DisplayLabel.literal("Ground"),
                Optional.empty(),
                new AffineTransform2D(1, 0, 0, 0, 1, 0),
                32
        );
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:test-map"),
                new com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey(
                        "fpsmatch:test", "Test Map"
                ),
                1,
                Sha256.parse("0".repeat(64)),
                new CompilerProfile(
                        NamespacedId.parse("fpsmatch:test-compiler"),
                        MinimapFormatContract.CURRENT
                ),
                new CanvasBounds(MinimapHardLimits.MAX_CANVAS_EDGE,
                        MinimapHardLimits.MAX_CANVAS_EDGE),
                DefaultViewMode.FULL_MAP,
                List.of(floor),
                1,
                List.of()
        );

        assertThrows(ContainerValidationException.class,
                () -> MinimapTileValidator.validateRuntimeCoverageBudget(manifest));
    }

    @Test
    void rejectsImportedAssetCoverageBeforeEnumeratingAnUnrepresentableGrid() {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinitionWithImportedAssets();
        SourceDocument document = new SourceDocument(
                new WorldBounds(0, 0, 100, 100),
                new CanvasBounds(MinimapHardLimits.MAX_CANVAS_EDGE,
                        MinimapHardLimits.MAX_CANVAS_EDGE),
                base.document().defaultViewMode(),
                base.document().floors(),
                base.document().layerOrder()
        );
        SourceManifest manifest = new SourceManifest(
                base.manifest().formatVersion(), base.manifest().documentId(),
                base.manifest().binding(), base.manifest().revision(),
                base.manifest().dimension(), base.manifest().provenance(), 1,
                List.of()
        );
        MinimapDefinition huge = new MinimapDefinition(
                manifest, document, base.regions(), base.connections(), base.styles()
        );

        assertThrows(ContainerValidationException.class,
                () -> MinimapTileValidator.validateSourceCoverageBudget(huge));
    }

    @Test
    void rejectsManifestWhoseDescriptorsOmitRequiredRuntimeTiles() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair pair = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:test-compiler"),
                                    MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            );
            RuntimeManifest manifest = pair.runtimeManifest();
            List<RuntimeEntryDescriptor> missingTile = manifest.entries().stream()
                    .filter(entry -> !entry.path().value().equals(
                            "floors/ground/tiles/0/0_0.png"
                    ))
                    .toList();
            RuntimeManifest incomplete = new RuntimeManifest(
                    manifest.formatVersion(), manifest.documentId(), manifest.binding(),
                    manifest.publishRevision(), manifest.sourceHash(), manifest.compilerProfile(),
                    manifest.canvas(), manifest.defaultViewMode(), manifest.floors(),
                    manifest.tileEdge(), missingTile
            );

            assertThrows(
                    ContainerValidationException.class,
                    () -> RuntimeEntryValidation.readManifest(CanonicalModelJson.write(
                            incomplete, MinimapModelCodecs.RUNTIME_MANIFEST
                    ))
            );
        }
    }
}
