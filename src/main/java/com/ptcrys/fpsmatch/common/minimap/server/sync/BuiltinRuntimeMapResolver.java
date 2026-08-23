package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMap;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class BuiltinRuntimeMapResolver
        implements ServerMinimapRuntimeRouter.RuntimeResolver {
    private final BuiltinRuntimeMapRegistry registry;
    private final BiFunction<UUID, WireIdentity.MapTarget, Optional<RuntimeAuthority>>
            authorityResolver;

    public BuiltinRuntimeMapResolver(
            BuiltinRuntimeMapRegistry registry,
            BiFunction<UUID, WireIdentity.MapTarget, Optional<RuntimeAuthority>>
                    authorityResolver
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
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
        if (authority.isEmpty()
                || !authority.orElseThrow().target().equals(target)
                || authority.orElseThrow().revision() != 0L) {
            return Optional.empty();
        }
        RuntimeAuthority expected = authority.orElseThrow();
        Optional<BuiltinRuntimeMapRegistry.Entry> registered = registry.find(target);
        if (registered.isEmpty()) {
            throw new RuntimeMapUnavailableException(
                    "Builtin runtime declaration is unavailable"
            );
        }
        BuiltinRuntimeMapRegistry.Entry entry = registered.orElseThrow();
        if (!expected.documentId().equals(entry.declaration().documentId())
                || !expected.sourceHash().equals(entry.sourceHash())
                || !expected.runtimeHash().equals(entry.runtimeHash())) {
            return Optional.empty();
        }
        RuntimeMap runtime = null;
        try {
            runtime = BuiltinRuntimeMapRegistry.open(entry.runtimePath());
            if (runtime.manifest().publishRevision() != 0L
                    || !runtime.manifest().binding().equals(target.mapKey())
                    || !runtime.manifest().documentId().equals(expected.documentId())
                    || !runtime.manifest().sourceHash().equals(expected.sourceHash())
                    || !runtime.runtimeHash().equals(expected.runtimeHash())
                    || !runtime.runtimeContainerHash().equals(
                    entry.runtimeContainerHash()
            )) {
                runtime.close();
                runtime = null;
                throw new RuntimeMapUnavailableException(
                        "Builtin runtime content is unavailable"
                );
            }
            return Optional.of(new OpenSource(target, runtime));
        } catch (RuntimeMapUnavailableException unavailable) {
            close(runtime, unavailable);
            throw unavailable;
        } catch (IOException | RuntimeException unavailable) {
            close(runtime, unavailable);
            throw new RuntimeMapUnavailableException(
                    "Unable to open builtin runtime content",
                    unavailable
            );
        }
    }

    private static void close(RuntimeMap runtime, Throwable failure) {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static final class OpenSource implements RuntimeMapSource {
        private final RuntimeMap runtime;
        private final WireIdentity.RuntimeIdentity identity;
        private final Map<ContainerPath, RuntimeEntryDescriptor> descriptors;

        private OpenSource(WireIdentity.MapTarget target, RuntimeMap runtime) {
            this.runtime = runtime;
            this.identity = new WireIdentity.RuntimeIdentity(
                    new WireIdentity.DocumentBinding(
                            target, runtime.manifest().documentId()
                    ),
                    0L,
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
