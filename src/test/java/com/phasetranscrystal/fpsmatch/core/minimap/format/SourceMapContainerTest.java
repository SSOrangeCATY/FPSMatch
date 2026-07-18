package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BlendMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerCommon;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MediaType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RasterPaintLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceFloor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceMapContainerTest {
    @Test
    void writesAndReadsCanonicalSourceAuthorityWithCompleteContainerHash() throws Exception {
        MinimapDefinition definition = MinimapContainerFixtures.sourceDefinition();
        byte[] thumbnail = CanonicalPngCodecV1.encode(1, 1, new byte[]{1, 2, 3, (byte) 255});

        byte[] container = SourceMapWriter.write(definition, Map.of(
                path("generators.json"), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                path("thumbnail.png"), thumbnail
        ));

        try (SourceMap source = SourceMapReader.read(container)) {
            assertEquals(definition.document(), source.definition().document());
            assertEquals(definition.regions(), source.definition().regions());
            assertEquals(definition.connections(), source.definition().connections());
            assertEquals(definition.styles(), source.definition().styles());
            assertEquals(hash(container), source.sourceHash());
            assertArrayEquals(thumbnail, source.entryBytes(path("thumbnail.png")));

            List<String> descriptorPaths = source.definition().manifest().entries().stream()
                    .map(entry -> entry.path().value())
                    .toList();
            assertEquals(List.of(
                    "connections.json",
                    "document.json",
                    "generators.json",
                    "regions.json",
                    "styles.json",
                    "thumbnail.png"
            ), descriptorPaths);
            assertFalse(descriptorPaths.contains("manifest.json"));
            assertEquals(MediaType.APPLICATION_JSON,
                    source.definition().manifest().entries().get(0).mediaType());
            assertEquals(MediaType.IMAGE_PNG,
                    source.definition().manifest().entries().get(5).mediaType());
        }
    }

    @Test
    void rejectsSourceTilesThatDoNotReferenceADeclaredLayer() {
        MinimapDefinition definition = MinimapContainerFixtures.sourceDefinition();
        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                definition,
                Map.of(
                        path("floors/ground/layers/unknown/tiles/0_0.png"),
                        MinimapContainerFixtures.fullRuntimeTile()
                )
        ));
    }

    @Test
    void rejectsNonCanonicalOpaqueSourceJsonBeforeWriting() {
        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                MinimapContainerFixtures.sourceDefinition(),
                Map.of(path("generators.json"), " { }".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        ));
    }

    @Test
    void rejectsNonCanonicalSourceThumbnailBeforeWriting() {
        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                MinimapContainerFixtures.sourceDefinition(),
                Map.of(path("thumbnail.png"), new byte[]{1, 2, 3})
        ));
    }

    @Test
    void requiresCompleteCoverageForEachImportedImageAsset() {
        MinimapDefinition definition = MinimapContainerFixtures.sourceDefinitionWithImportedAssets();
        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                definition,
                Map.of(path("assets/images/a/tiles/0_0.png"),
                        MinimapContainerFixtures.fullRuntimeTile())
        ));
    }

    @Test
    void rejectsEntrySourceMetadataThatChangesAfterContentScanning() {
        CanonicalZipWriter.EntrySource unstable = new CanonicalZipWriter.EntrySource() {
            private final byte[] bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            private int streamsOpened;
            private boolean liedAboutDescriptorSize;

            @Override
            public ContainerPath path() {
                return ContainerPath.parse("generators.json");
            }

            @Override
            public long size() {
                if (streamsOpened >= 2 && !liedAboutDescriptorSize) {
                    liedAboutDescriptorSize = true;
                    return 1;
                }
                return bytes.length;
            }

            @Override
            public InputStream openStream() {
                streamsOpened++;
                return new ByteArrayInputStream(bytes);
            }
        };

        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                new SourceMapDraft(
                        MinimapContainerFixtures.sourceDefinition(),
                        List.of(unstable)
                )
        ));
    }

    @Test
    void rejectsEntryContentThatChangesBetweenValidationAndWriting() {
        CanonicalZipWriter.EntrySource unstable = new CanonicalZipWriter.EntrySource() {
            private int streamsOpened;

            @Override
            public ContainerPath path() {
                return ContainerPath.parse("generators.json");
            }

            @Override
            public long size() {
                return 2;
            }

            @Override
            public InputStream openStream() {
                byte[] bytes = streamsOpened++ == 0
                        ? "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        : " {".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new ByteArrayInputStream(bytes);
            }
        };

        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                new SourceMapDraft(
                        MinimapContainerFixtures.sourceDefinition(),
                        List.of(unstable)
                )
        ));
    }

    @Test
    void rejectsColoredMaskPixels() {
        byte[] colored = new byte[64 * 64 * 4];
        for (int index = 0; index < colored.length; index += 4) {
            colored[index] = 1;
            colored[index + 1] = 2;
            colored[index + 2] = 3;
            colored[index + 3] = (byte) 255;
        }

        assertThrows(ContainerValidationException.class, () -> SourceMapWriter.write(
                maskDefinition(),
                Map.of(path("floors/ground/layers/paint/mask/0_0.png"),
                        CanonicalPngCodecV1.encode(64, 64, colored))
        ));
    }

    private static MinimapDefinition maskDefinition() {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        SourceFloor original = base.document().floors().get(0);
        RasterPaintLayer paint = new RasterPaintLayer(new LayerCommon(
                "paint", com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel.literal("Paint"),
                true, false, 1, BlendMode.NORMAL, java.util.Optional.empty(), true
        ));
        SourceFloor floor = new SourceFloor(
                original.selection(), original.label(), original.contentBounds(),
                original.background(), original.calibration(), List.of(paint)
        );
        SourceDocument document = new SourceDocument(
                base.document().worldBounds(), base.document().canvas(),
                base.document().defaultViewMode(), List.of(floor),
                Map.of("ground", List.of("paint"))
        );
        return new MinimapDefinition(
                base.manifest(), document, base.regions(), base.connections(), base.styles()
        );
    }

    private static ContainerPath path(String value) {
        return ContainerPath.parse(value);
    }

    private static Sha256 hash(byte[] bytes) {
        try {
            return Sha256.parse(java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
