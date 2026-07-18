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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinRuntimeResourceLoaderTest {
    @TempDir
    Path temp;

    @Test
    void materializesAndRegistersAValidatedCompanionPair() throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        BuiltinRuntimeBinding binding = binding(pair.runtime());
        NamespacedId resourceId = NamespacedId.parse("blockoffensive:dust2");
        BuiltinRuntimeResourceLoader.ResourcePair resource =
                new BuiltinRuntimeResourceLoader.ResourcePair(
                        resourceId,
                        BuiltinRuntimeBindingCodec.write(binding),
                        () -> new ByteArrayInputStream(pair.runtime())
                );

        BuiltinRuntimeMapRegistry registry = new BuiltinRuntimeResourceLoader()
                .load(temp.resolve("builtin-cache"), List.of(resource));
        BuiltinRuntimeMapRegistry.Entry entry = registry.find(
                new WireIdentity.MapTarget(binding.binding(), binding.dimension())
        ).orElseThrow();

        assertEquals(resourceId, entry.resourceId());
        assertTrue(entry.runtimePath().startsWith(
                temp.resolve("builtin-cache").toAbsolutePath().normalize()
        ));
        assertTrue(entry.runtimePath().getFileName().toString()
                .equals(entry.runtimeContainerHash().value() + ".fpsmapc"));
        assertTrue(Files.isRegularFile(entry.runtimePath()));
        try (var runtime = BuiltinRuntimeMapRegistry.open(entry.runtimePath())) {
            assertEquals(binding.runtimeHash(), runtime.runtimeHash());
        }
        try (var files = Files.list(temp.resolve("builtin-cache"))) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                    .endsWith(".tmp")));
        }
    }

    @Test
    void rejectsInvalidBindingContainerAndOverLimitStreamWithoutPromotion()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        BuiltinRuntimeBinding binding = binding(pair.runtime());
        Path cache = temp.resolve("builtin-cache");

        assertThrows(ContainerValidationException.class, () ->
                new BuiltinRuntimeResourceLoader().load(cache, List.of(
                        new BuiltinRuntimeResourceLoader.ResourcePair(
                                NamespacedId.parse("blockoffensive:dust2"),
                                BuiltinRuntimeBindingCodec.write(binding),
                                () -> new ByteArrayInputStream(new byte[]{1, 2, 3})
                        )
                )));
        assertThrows(ContainerValidationException.class, () ->
                new BuiltinRuntimeResourceLoader().load(cache, List.of(
                        new BuiltinRuntimeResourceLoader.ResourcePair(
                                NamespacedId.parse("blockoffensive:dust2"),
                                new byte[]{'{', '}'},
                                () -> new ByteArrayInputStream(pair.runtime())
                        )
                )));
        assertThrows(ContainerValidationException.class, () ->
                new BuiltinRuntimeResourceLoader(pair.runtime().length).load(
                        cache, List.of(
                        new BuiltinRuntimeResourceLoader.ResourcePair(
                                NamespacedId.parse("blockoffensive:dust2"),
                                BuiltinRuntimeBindingCodec.write(binding),
                                EndlessInputStream::new
                        )
                )));

        if (Files.exists(cache)) {
            try (var files = Files.list(cache)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString()
                        .endsWith(".fpsmapc")));
            }
        }
    }

    @Test
    void rejectsAReloadWhoseValidContainersExceedTheAggregateBudget()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        BuiltinRuntimeBinding binding = binding(pair.runtime());
        byte[] bindingBytes = BuiltinRuntimeBindingCodec.write(binding);
        List<BuiltinRuntimeResourceLoader.ResourcePair> resources = List.of(
                new BuiltinRuntimeResourceLoader.ResourcePair(
                        NamespacedId.parse("blockoffensive:first"),
                        bindingBytes,
                        () -> new ByteArrayInputStream(pair.runtime())
                ),
                new BuiltinRuntimeResourceLoader.ResourcePair(
                        NamespacedId.parse("blockoffensive:second"),
                        bindingBytes,
                        () -> new ByteArrayInputStream(pair.runtime())
                )
        );
        long aggregateLimit = 2L * pair.runtime().length - 1L;

        assertThrows(ContainerValidationException.class, () ->
                new BuiltinRuntimeResourceLoader(
                        pair.runtime().length,
                        aggregateLimit
                ).load(temp.resolve("aggregate-cache"), resources));
    }

    private static BuiltinRuntimeBinding binding(byte[] runtimeBytes) throws Exception {
        try (var runtime = RuntimeMapReader.read(runtimeBytes)) {
            return new BuiltinRuntimeBinding(
                    runtime.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtime.manifest().documentId(),
                    runtime.runtimeHash()
            );
        }
    }

    private static final class EndlessInputStream extends InputStream {
        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            java.util.Arrays.fill(bytes, offset, offset + length, (byte) 0);
            return length;
        }
    }
}
