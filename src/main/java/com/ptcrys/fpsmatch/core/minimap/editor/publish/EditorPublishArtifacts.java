package com.ptcrys.fpsmatch.core.minimap.editor.publish;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceDefaults;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalZipWriter;
import com.ptcrys.fpsmatch.core.minimap.format.CompiledMapPair;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapWriter;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CompilerProfile;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a publishable empty source/runtime pair for CREATE_EMPTY editor sessions.
 */
public final class EditorPublishArtifacts {
    private EditorPublishArtifacts() {
    }

    public record Pair(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash,
            long revision
    ) {
        public Pair {
            Objects.requireNonNull(sourceBytes, "sourceBytes");
            Objects.requireNonNull(runtimeBytes, "runtimeBytes");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
            if (revision < 0) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
            sourceBytes = sourceBytes.clone();
            runtimeBytes = runtimeBytes.clone();
        }

        public byte[] sourceBytes() {
            return sourceBytes.clone();
        }

        public byte[] runtimeBytes() {
            return runtimeBytes.clone();
        }
    }

    public static Pair buildEmpty(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            CanvasBounds canvas,
            int tileEdge,
            String floorId
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(floorId, "floorId");
        if (revision <= 0) {
            throw new IllegalArgumentException("Publish revision must be positive");
        }
        if (tileEdge <= 0) {
            throw new IllegalArgumentException("tileEdge must be positive");
        }

        MinimapDefinition definition = EditorSourceDefaults.createDefinition(
                mapKey, dimension, documentId, revision, canvas, tileEdge, floorId, List.of());
        byte[] sourceBytes = SourceMapWriter.write(definition);
        List<CanonicalZipWriter.EntrySource> tiles = transparentTiles(floorId, canvas, tileEdge);
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair compiled = RuntimeMapCompiler.compile(
                    source,
                    RuntimeCompileRequest.forSource(
                            source.manifest(),
                            revision,
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:editor-empty"),
                                    MinimapFormatContract.CURRENT
                            ),
                            tiles
                    )
            );
            return new Pair(
                    sourceBytes,
                    compiled.runtimeBytes(),
                    compiled.sourceHash(),
                    compiled.runtimeHash(),
                    compiled.runtimeContainerHash(),
                    revision
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build empty minimap publish pair", exception);
        }
    }

    private static List<CanonicalZipWriter.EntrySource> transparentTiles(
            String floorId,
            CanvasBounds canvas,
            int tileEdge
    ) {
        int tilesX = Math.max(1, (canvas.width() + tileEdge - 1) / tileEdge);
        int tilesY = Math.max(1, (canvas.height() + tileEdge - 1) / tileEdge);
        byte[] rgba = new byte[tileEdge * tileEdge * 4];
        byte[] png = CanonicalPngCodecV1.encode(tileEdge, tileEdge, rgba);
        List<CanonicalZipWriter.EntrySource> tiles = new ArrayList<>(tilesX * tilesY);
        for (int y = 0; y < tilesY; y++) {
            for (int x = 0; x < tilesX; x++) {
                ContainerPath path = ContainerPath.parse(
                        "floors/" + floorId + "/tiles/0/" + x + "_" + y + ".png"
                );
                tiles.add(new CanonicalZipWriter.Entry(path, png));
            }
        }
        return tiles;
    }
}
