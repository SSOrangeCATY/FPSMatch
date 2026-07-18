package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.CurrentPointer;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishRecord;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishState;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.RepositoryFileSystem;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class RepositoryRuntimeMapResolver
        implements ServerMinimapRuntimeRouter.RuntimeResolver {
    private final MinimapRepository repository;
    private final BiFunction<UUID, WireIdentity.MapTarget, Optional<RuntimeAuthority>>
            authorityResolver;

    public RepositoryRuntimeMapResolver(
            MinimapRepository repository,
            BiFunction<UUID, WireIdentity.MapTarget, Optional<RuntimeAuthority>>
                    authorityResolver
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorityResolver = Objects.requireNonNull(
                authorityResolver, "authorityResolver"
        );
    }

    @Override
    public Optional<RuntimeMapSource> resolve(
            UUID actorId,
            WireIdentity.MapTarget target
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(target, "target");
        Optional<RuntimeAuthority> authority = authorityResolver.apply(actorId, target);
        if (authority.isEmpty() || !authority.orElseThrow().target().equals(target)) {
            return Optional.empty();
        }
        RuntimeAuthority expected = authority.orElseThrow();
        Optional<CurrentPointer> current = repository.current(target.mapKey());
        if (current.isEmpty()) {
            throw unavailable("Current runtime revision is unavailable", null);
        }
        if (current.orElseThrow().revision() != expected.revision()) {
            return Optional.empty();
        }
        Path revision = repository.mapDirectory(target.mapKey())
                .resolve("revisions")
                .resolve(Long.toString(expected.revision()));
        Path recordPath = revision.resolve("publish-record.json");
        Path runtimePath = revision.resolve("runtime.fpsmapc");
        SeekableByteChannel channel = null;
        RuntimeMap runtime = null;
        try {
            PublishRecord record = PublishRecord.read(
                    readBounded(recordPath, MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES)
            );
            if (!record.descriptorChecksum().equals(
                    current.orElseThrow().descriptorChecksum()
            ) || record.state() != PublishState.COMMITTED
                    && record.state() != PublishState.PREPARED
                    || !record.target().mapKey().equals(target.mapKey())
                    || !record.target().dimension().equals(target.dimension())
                    || !record.target().documentId().equals(expected.documentId())
                    || record.descriptor().publishRevision() != expected.revision()
                    || !record.descriptor().sourceHash().equals(expected.sourceHash())
                    || !record.descriptor().runtimeHash().equals(expected.runtimeHash())) {
                return Optional.empty();
            }
            channel = Files.newByteChannel(
                    runtimePath,
                    java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
            );
            long runtimeSize = channel.size();
            if (runtimeSize > MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES) {
                throw new IOException("Runtime container exceeds its hard limit");
            }
            runtime = RuntimeMapReader.open(channel, runtimeSize);
            channel = null;
            if (!runtime.manifest().binding().equals(target.mapKey())
                    || !runtime.manifest().documentId().equals(expected.documentId())
                    || runtime.manifest().publishRevision() != expected.revision()
                    || !runtime.manifest().sourceHash().equals(expected.sourceHash())
                    || !runtime.runtimeHash().equals(expected.runtimeHash())
                    || !runtime.runtimeContainerHash().equals(
                    record.descriptor().runtimeContainerHash()
            )) {
                runtime.close();
                runtime = null;
                throw unavailable("Published runtime content is unavailable", null);
            }
            return Optional.of(new OpenRuntimeMapSource(target, runtime));
        } catch (RuntimeMapUnavailableException unavailable) {
            close(runtime, channel, unavailable);
            throw unavailable;
        } catch (IOException | RuntimeException unavailable) {
            close(runtime, channel, unavailable);
            throw unavailable("Unable to open published runtime content", unavailable);
        }
    }

    private static RuntimeMapUnavailableException unavailable(
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new RuntimeMapUnavailableException(message)
                : new RuntimeMapUnavailableException(message, cause);
    }

    private static byte[] readBounded(Path path, int maximumBytes) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(
                path,
                java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        )) {
            long size = channel.size();
            if (size < 0 || size > maximumBytes) {
                throw new IOException("Metadata file exceeds its hard limit");
            }
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(
                    Math.toIntExact(size)
            );
            while (buffer.hasRemaining()) {
                int count = channel.read(buffer);
                if (count < 0) {
                    throw new IOException("Metadata file ended before its declared size");
                }
                if (count == 0) {
                    throw new IOException("Metadata file read made no progress");
                }
            }
            if (channel.size() != size) {
                throw new IOException("Metadata file changed while it was read");
            }
            return buffer.array();
        }
    }

    private static void close(
            RuntimeMap runtime,
            SeekableByteChannel channel,
            Throwable failure
    ) {
        try {
            if (runtime != null) {
                runtime.close();
            } else if (channel != null) {
                channel.close();
            }
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static final class OpenRuntimeMapSource implements RuntimeMapSource {
        private final RuntimeMap runtime;
        private final WireIdentity.RuntimeIdentity identity;
        private final Map<ContainerPath, RuntimeEntryDescriptor> descriptors;

        private OpenRuntimeMapSource(
                WireIdentity.MapTarget target,
                RuntimeMap runtime
        ) {
            this.runtime = runtime;
            this.identity = new WireIdentity.RuntimeIdentity(
                    new WireIdentity.DocumentBinding(
                            target, runtime.manifest().documentId()
                    ),
                    runtime.manifest().publishRevision(),
                    runtime.runtimeHash(),
                    Optional.of(runtime.runtimeContainerHash())
            );
            this.descriptors = runtime.manifest().entries().stream().collect(
                    Collectors.toUnmodifiableMap(
                            RuntimeEntryDescriptor::path, descriptor -> descriptor
                    )
            );
        }

        @Override
        public WireIdentity.RuntimeIdentity identity() {
            return identity;
        }

        @Override
        public byte[] manifestBytes() {
            return runtime.manifestBytes();
        }

        @Override
        public Optional<RuntimeEntryDescriptor> descriptor(ContainerPath path) {
            return Optional.ofNullable(descriptors.get(path));
        }

        @Override
        public InputStream openEntry(ContainerPath path) {
            if (!descriptors.containsKey(path)) {
                throw new IllegalArgumentException("Runtime entry is not declared");
            }
            return runtime.openEntry(path);
        }

        @Override
        public void close() throws IOException {
            runtime.close();
        }
    }
}
