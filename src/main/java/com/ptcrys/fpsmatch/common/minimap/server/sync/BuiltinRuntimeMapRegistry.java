package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerValidationException;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMap;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.ptcrys.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BuiltinRuntimeMapRegistry {
    private final Map<WireIdentity.MapTarget, Entry> entries;

    private BuiltinRuntimeMapRegistry(Map<WireIdentity.MapTarget, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Entry> find(WireIdentity.MapTarget target) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(target, "target")));
    }

    public record Entry(
            NamespacedId resourceId,
            BuiltinRuntimeBinding declaration,
            Path runtimePath,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        public Entry {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(declaration, "declaration");
            runtimePath = Objects.requireNonNull(runtimePath, "runtimePath")
                    .toAbsolutePath().normalize();
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
        }

        public WireIdentity.MapTarget target() {
            return new WireIdentity.MapTarget(
                    declaration.binding(), declaration.dimension()
            );
        }
    }

    public static final class Builder {
        private final Map<WireIdentity.MapTarget, Entry> entries = new LinkedHashMap<>();
        private final Set<NamespacedId> resourceIds = new java.util.LinkedHashSet<>();

        public Builder register(
                NamespacedId resourceId,
                BuiltinRuntimeBinding declaration,
                Path runtimePath
        ) {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(declaration, "declaration");
            Path normalized = Objects.requireNonNull(runtimePath, "runtimePath")
                    .toAbsolutePath().normalize();
            WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                    declaration.binding(), declaration.dimension()
            );
            if (resourceIds.contains(resourceId)) {
                throw new ContainerValidationException(
                        "Duplicate builtin runtime resource ID"
                );
            }
            if (entries.containsKey(target)) {
                throw new ContainerValidationException(
                        "Duplicate builtin runtime map target"
                );
            }
            Entry entry = validate(resourceId, declaration, normalized);
            resourceIds.add(resourceId);
            entries.put(target, entry);
            return this;
        }

        public BuiltinRuntimeMapRegistry build() {
            return new BuiltinRuntimeMapRegistry(entries);
        }

    }

    static Entry validate(
            NamespacedId resourceId,
            BuiltinRuntimeBinding declaration,
            Path runtimePath
    ) {
        try (RuntimeMap runtime = open(runtimePath)) {
            if (runtime.manifest().publishRevision() != 0L) {
                throw new ContainerValidationException(
                        "Builtin runtime map must use revision zero"
                );
            }
            if (!runtime.manifest().binding().equals(declaration.binding())
                    || !runtime.manifest().documentId().equals(
                    declaration.documentId()
            ) || !runtime.runtimeHash().equals(declaration.runtimeHash())) {
                throw new ContainerValidationException(
                        "Builtin runtime declaration does not match its container"
                );
            }
            return new Entry(
                    resourceId,
                    declaration,
                    runtimePath,
                    runtime.manifest().sourceHash(),
                    runtime.runtimeHash(),
                    runtime.runtimeContainerHash()
            );
        } catch (ContainerValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ContainerValidationException(
                    "Unable to validate builtin runtime map", exception
            );
        }
    }

    static RuntimeMap open(Path path) throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(
                path,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        );
        try {
            long size = channel.size();
            if (size <= 0 || size > MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES) {
                throw new ContainerValidationException(
                        "Builtin runtime container exceeds its byte limit"
                );
            }
            RuntimeMap runtime = RuntimeMapReader.open(channel, size);
            channel = null;
            return runtime;
        } finally {
            if (channel != null) {
                channel.close();
            }
        }
    }
}
