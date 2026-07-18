package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.format.BuiltinRuntimeBindingCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.format.ContainerValidationException;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapStorageFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinRuntimeCatalogTest {
    @TempDir
    Path temp;

    @Test
    void atomicallyReplacesSuccessfulSnapshotsAndKeepsTheLastGoodOnFailure()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        BuiltinRuntimeBinding binding;
        try (var runtime = RuntimeMapReader.read(pair.runtime())) {
            binding = new BuiltinRuntimeBinding(
                    runtime.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtime.manifest().documentId(),
                    runtime.runtimeHash()
            );
        }
        NamespacedId resourceId = NamespacedId.parse("fpsmatch:test");
        BuiltinRuntimeResourceLoader.ResourcePair valid =
                new BuiltinRuntimeResourceLoader.ResourcePair(
                        resourceId,
                        BuiltinRuntimeBindingCodec.write(binding),
                        () -> new ByteArrayInputStream(pair.runtime())
                );
        BuiltinRuntimeCatalog catalog = new BuiltinRuntimeCatalog(
                new BuiltinRuntimeResourceLoader()
        );
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                binding.binding(), binding.dimension()
        );

        assertTrue(catalog.snapshot().find(target).isEmpty());
        catalog.reload(temp.resolve("cache"), List.of(valid));
        BuiltinRuntimeMapRegistry lastGood = catalog.snapshot();
        assertTrue(lastGood.find(target).isPresent());

        BuiltinRuntimeResourceLoader.ResourcePair invalid =
                new BuiltinRuntimeResourceLoader.ResourcePair(
                        resourceId,
                        BuiltinRuntimeBindingCodec.write(binding),
                        () -> new ByteArrayInputStream(new byte[]{1})
                );
        assertThrows(ContainerValidationException.class, () ->
                catalog.reload(temp.resolve("cache"), List.of(invalid)));
        assertTrue(catalog.snapshot() == lastGood);

        catalog.reload(temp.resolve("cache"), List.of());
        assertTrue(catalog.snapshot().find(target).isEmpty());
        catalog.clear();
        assertTrue(catalog.snapshot().find(target).isEmpty());
    }
}
