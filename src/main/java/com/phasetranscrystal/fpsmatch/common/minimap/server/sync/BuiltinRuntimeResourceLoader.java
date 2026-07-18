package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.BuiltinRuntimeBindingCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.format.ContainerValidationException;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BuiltinRuntimeResourceLoader {
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final long maximumContainerBytes;
    private final long maximumCatalogBytes;

    public BuiltinRuntimeResourceLoader() {
        this(
                MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES,
                MinimapHardLimits.MAX_BUILTIN_RUNTIME_CATALOG_BYTES
        );
    }

    BuiltinRuntimeResourceLoader(long maximumContainerBytes) {
        this(
                maximumContainerBytes,
                MinimapHardLimits.MAX_BUILTIN_RUNTIME_CATALOG_BYTES
        );
    }

    BuiltinRuntimeResourceLoader(
            long maximumContainerBytes,
            long maximumCatalogBytes
    ) {
        if (maximumContainerBytes <= 0
                || maximumContainerBytes
                > MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Builtin runtime container limit is invalid"
            );
        }
        if (maximumCatalogBytes < maximumContainerBytes
                || maximumCatalogBytes
                > MinimapHardLimits.MAX_BUILTIN_RUNTIME_CATALOG_BYTES) {
            throw new IllegalArgumentException(
                    "Builtin runtime catalog limit is invalid"
            );
        }
        this.maximumContainerBytes = maximumContainerBytes;
        this.maximumCatalogBytes = maximumCatalogBytes;
    }

    public BuiltinRuntimeMapRegistry load(
            Path cacheRoot,
            List<ResourcePair> resources
    ) {
        Path root = Objects.requireNonNull(cacheRoot, "cacheRoot")
                .toAbsolutePath().normalize();
        List<ResourcePair> ordered = new ArrayList<>(
                Objects.requireNonNull(resources, "resources")
        );
        if (ordered.size() > MinimapHardLimits.MAX_ZIP_ENTRIES) {
            throw new ContainerValidationException(
                    "Too many builtin runtime declarations"
            );
        }
        ordered.sort(Comparator.comparing(pair -> pair.resourceId().toString()));
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Builtin runtime cache root is not a directory");
            }
            BuiltinRuntimeMapRegistry.Builder registry =
                    BuiltinRuntimeMapRegistry.builder();
            long catalogBytes = 0L;
            for (ResourcePair resource : ordered) {
                BuiltinRuntimeBinding declaration =
                        BuiltinRuntimeBindingCodec.read(resource.bindingBytes());
                MaterializedRuntime materialized = materialize(
                        root,
                        resource,
                        declaration,
                        maximumCatalogBytes - catalogBytes
                );
                catalogBytes += materialized.byteLength();
                registry.register(
                        resource.resourceId(),
                        declaration,
                        materialized.path()
                );
            }
            return registry.build();
        } catch (ContainerValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ContainerValidationException(
                    "Unable to load builtin runtime resources", exception
            );
        }
    }

    private MaterializedRuntime materialize(
            Path root,
            ResourcePair resource,
            BuiltinRuntimeBinding declaration,
            long remainingCatalogBytes
    ) throws IOException {
        Path temporary = Files.createTempFile(root, ".builtin-runtime-", ".tmp");
        boolean promoted = false;
        try {
            long byteLength = copyBounded(
                    resource.runtimeSource(),
                    temporary,
                    remainingCatalogBytes
            );
            BuiltinRuntimeMapRegistry.Entry validated =
                    BuiltinRuntimeMapRegistry.validate(
                            resource.resourceId(), declaration, temporary
                    );
            Path target = root.resolve(
                    validated.runtimeContainerHash().value() + ".fpsmapc"
            ).normalize();
            if (!target.startsWith(root)) {
                throw new ContainerValidationException(
                        "Builtin runtime cache path escaped its root"
                );
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                BuiltinRuntimeMapRegistry.Entry existing =
                        BuiltinRuntimeMapRegistry.validate(
                                resource.resourceId(), declaration, target
                        );
                if (!existing.runtimeContainerHash().equals(
                        validated.runtimeContainerHash()
                )) {
                    throw new ContainerValidationException(
                            "Builtin runtime cache content does not match its name"
                    );
                }
                return new MaterializedRuntime(target, byteLength);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            promoted = true;
            return new MaterializedRuntime(target, byteLength);
        } finally {
            if (!promoted) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private long copyBounded(
            InputSource source,
            Path target,
            long remainingCatalogBytes
    ) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long total = 0L;
        try (InputStream input = Objects.requireNonNull(
                source.open(), "runtime input"
        ); OutputStream output = Files.newOutputStream(
                target,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            while (true) {
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    throw new IOException("Builtin runtime stream made no progress");
                }
                if (count > maximumContainerBytes - total) {
                    throw new ContainerValidationException(
                            "Builtin runtime container exceeds its byte limit"
                    );
                }
                if (count > remainingCatalogBytes - total) {
                    throw new ContainerValidationException(
                            "Builtin runtime catalog exceeds its byte limit"
                    );
                }
                output.write(buffer, 0, count);
                total += count;
            }
        }
        if (total == 0L) {
            throw new ContainerValidationException(
                    "Builtin runtime container is empty"
            );
        }
        return total;
    }

    private record MaterializedRuntime(Path path, long byteLength) {
    }

    public record ResourcePair(
            NamespacedId resourceId,
            byte[] bindingBytes,
            InputSource runtimeSource
    ) {
        public ResourcePair {
            Objects.requireNonNull(resourceId, "resourceId");
            bindingBytes = Objects.requireNonNull(
                    bindingBytes, "bindingBytes"
            ).clone();
            Objects.requireNonNull(runtimeSource, "runtimeSource");
        }

        @Override
        public byte[] bindingBytes() {
            return bindingBytes.clone();
        }
    }

    @FunctionalInterface
    public interface InputSource {
        InputStream open() throws IOException;
    }
}
