package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.format.CompiledMapPair;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapWriter;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;

public final class MinimapStorageFixtures {
    private MinimapStorageFixtures() {
    }

    public static Pair validPair(long revision) {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        SourceManifest manifest = new SourceManifest(
                base.manifest().formatVersion(), base.manifest().documentId(),
                base.manifest().binding(), revision, base.manifest().dimension(),
                base.manifest().provenance(), base.manifest().tileEdge(), java.util.List.of()
        );
        MinimapDefinition definition = new MinimapDefinition(
                manifest, base.document(), base.regions(), base.connections(), base.styles()
        );
        byte[] sourceBytes = SourceMapWriter.write(definition);
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair pair = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:test-storage"),
                                    MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            );
            return new Pair(sourceBytes, pair.runtimeBytes());
        } catch (Exception exception) {
            throw new AssertionError("Unable to build storage fixture", exception);
        }
    }

    public record Pair(byte[] source, byte[] runtime) {
    }
}
