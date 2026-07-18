package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapCacheKey;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.ContainerValidationException;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeEntryValidation;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side static runtime entry sync facade over fragment assembly + disk cache + active store.
 */
public final class MinimapClientSyncManager {
    private final FragmentAccumulator accumulator;
    private final MinimapDiskCache diskCache;
    private final RuntimeEntryStore entryStore;
    private final RuntimeEntryValidator entryValidator;
    private final Map<RuntimeGeneration, ManifestState> manifests = new HashMap<>();

    public MinimapClientSyncManager(
            FragmentAccumulator accumulator,
            MinimapDiskCache diskCache,
            RuntimeEntryStore entryStore,
            RuntimeEntryValidator entryValidator
    ) {
        this.accumulator = Objects.requireNonNull(accumulator, "accumulator");
        this.diskCache = Objects.requireNonNull(diskCache, "diskCache");
        this.entryStore = Objects.requireNonNull(entryStore, "entryStore");
        this.entryValidator = Objects.requireNonNull(entryValidator, "entryValidator");
    }

    public Optional<byte[]> acceptFragment(
            MinimapCacheKey cacheKey,
            TransferKey transferKey,
            int fragmentIndex,
            byte[] fragmentBytes,
            long nowMillis
    ) {
        Objects.requireNonNull(cacheKey, "cacheKey");
        if (!cacheKey.objectHash().equals(transferKey.objectHash())
                || !cacheKey.stablePath().equals(transferKey.stablePath())) {
            throw new FragmentAssemblyException("Stable path/hash mismatch for cache key");
        }
        Optional<byte[]> assembled = accumulator.accept(transferKey, fragmentIndex, fragmentBytes, nowMillis);
        if (assembled.isEmpty()) {
            return Optional.empty();
        }
        byte[] payload = assembled.get();
        if (!entryValidator.validate(cacheKey, payload)) {
            return Optional.empty();
        }
        if (!diskCache.put(cacheKey, payload)) {
            return Optional.empty();
        }
        entryStore.stage(cacheKey, payload);
        return Optional.of(payload);
    }

    public synchronized Optional<RuntimeManifest> acceptManifest(
            RuntimeGeneration generation,
            TransferKey transferKey,
            int fragmentIndex,
            byte[] fragmentBytes,
            long nowMillis
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(transferKey, "transferKey");
        Objects.requireNonNull(fragmentBytes, "fragmentBytes");
        if (!MinimapContainerLayout.RUNTIME_MANIFEST.value().equals(transferKey.stablePath())
                || !generation.runtimeHash().equals(transferKey.objectHash())
                || transferKey.totalLength() > MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES
                || !canonicalTransferGeometry(transferKey)) {
            return Optional.empty();
        }
        Optional<byte[]> assembled;
        try {
            assembled = accumulator.accept(
                    transferKey, fragmentIndex, fragmentBytes, nowMillis
            );
        } catch (FragmentAssemblyException exception) {
            return Optional.empty();
        }
        if (assembled.isEmpty()) {
            return Optional.empty();
        }
        byte[] payload = assembled.get();
        RuntimeManifest manifest;
        try {
            manifest = RuntimeEntryValidation.readManifest(payload).value();
        } catch (ContainerValidationException | IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (!manifest.binding().equals(generation.mapKey())
                || !manifest.documentId().equals(generation.documentId())
                || manifest.publishRevision() != generation.revision()) {
            return Optional.empty();
        }
        MinimapCacheKey cacheKey = cacheKey(
                generation, generation.runtimeHash(), MinimapContainerLayout.RUNTIME_MANIFEST
        );
        if (!entryValidator.validate(cacheKey, payload) || !diskCache.put(cacheKey, payload)) {
            return Optional.empty();
        }
        entryStore.stage(cacheKey, payload);
        manifests.put(generation, new ManifestState(manifest));
        return Optional.of(manifest);
    }

    public synchronized Optional<byte[]> acceptEntry(
            RuntimeGeneration generation,
            ContainerPath path,
            TransferKey transferKey,
            int fragmentIndex,
            byte[] fragmentBytes,
            long nowMillis
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(transferKey, "transferKey");
        Objects.requireNonNull(fragmentBytes, "fragmentBytes");
        ManifestState state = manifests.get(generation);
        if (state == null || !MinimapContainerLayout.isRuntimePath(path)
                || MinimapContainerLayout.RUNTIME_MANIFEST.equals(path)) {
            return Optional.empty();
        }
        RuntimeEntryDescriptor descriptor = state.entries().get(path);
        if (descriptor == null
                || !path.value().equals(transferKey.stablePath())
                || descriptor.byteLength() != transferKey.totalLength()
                || !descriptor.sha256().equals(transferKey.objectHash())
                || !canonicalTransferGeometry(transferKey)) {
            return Optional.empty();
        }
        Optional<byte[]> assembled;
        try {
            assembled = accumulator.accept(
                    transferKey, fragmentIndex, fragmentBytes, nowMillis
            );
        } catch (FragmentAssemblyException exception) {
            return Optional.empty();
        }
        if (assembled.isEmpty()) {
            return Optional.empty();
        }
        byte[] payload = assembled.get();
        try {
            RuntimeEntryValidation.validateEntry(state.manifest(), path, payload);
        } catch (ContainerValidationException | IllegalArgumentException exception) {
            return Optional.empty();
        }
        MinimapCacheKey cacheKey = cacheKey(
                generation, descriptor.sha256(), path
        );
        if (!entryValidator.validate(cacheKey, payload) || !diskCache.put(cacheKey, payload)) {
            return Optional.empty();
        }
        entryStore.stage(cacheKey, payload);
        return Optional.of(payload);
    }

    public synchronized boolean activateGeneration(
            RuntimeGeneration generation,
            List<ContainerPath> requiredPaths
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(requiredPaths, "requiredPaths");
        ManifestState state = manifests.get(generation);
        if (state == null || requiredPaths.isEmpty()) {
            return false;
        }
        LinkedHashSet<ContainerPath> activationPaths = new LinkedHashSet<>();
        activationPaths.add(MinimapContainerLayout.RUNTIME_MANIFEST);
        activationPaths.add(MinimapContainerLayout.RUNTIME_REGIONS);
        activationPaths.add(MinimapContainerLayout.CONNECTIONS);
        activationPaths.add(MinimapContainerLayout.RUNTIME_STYLES);
        activationPaths.addAll(requiredPaths);
        for (ContainerPath path : activationPaths) {
            if (!MinimapContainerLayout.RUNTIME_MANIFEST.equals(path)
                    && !state.entries().containsKey(path)) {
                return false;
            }
        }
        MinimapCacheKey generationKey = cacheKey(
                generation,
                generation.runtimeHash(),
                MinimapContainerLayout.RUNTIME_MANIFEST
        );
        if (!entryStore.hasStaged(
                generationKey,
                activationPaths.stream().map(ContainerPath::value).toList()
        )) {
            return false;
        }
        try {
            RuntimeEntryValidation.readDefinition(
                    state.manifest(),
                    entryStore.stagedPayload(
                            generationKey, MinimapContainerLayout.RUNTIME_REGIONS.value()
                    ).orElseThrow(),
                    entryStore.stagedPayload(
                            generationKey, MinimapContainerLayout.CONNECTIONS.value()
                    ).orElseThrow(),
                    entryStore.stagedPayload(
                            generationKey, MinimapContainerLayout.RUNTIME_STYLES.value()
                    ).orElseThrow()
            );
        } catch (ContainerValidationException | IllegalArgumentException exception) {
            return false;
        }
        return entryStore.activateStaged(
                generationKey,
                activationPaths.stream().map(ContainerPath::value).toList()
        );
    }

    public synchronized Optional<List<RuntimeEntryDescriptor>> stageCachedRequiredEntries(
            RuntimeGeneration generation,
            List<ContainerPath> requiredPaths
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(requiredPaths, "requiredPaths");
        ManifestState state = manifests.get(generation);
        if (state == null || requiredPaths.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<ContainerPath> paths = new LinkedHashSet<>();
        paths.add(MinimapContainerLayout.RUNTIME_REGIONS);
        paths.add(MinimapContainerLayout.CONNECTIONS);
        paths.add(MinimapContainerLayout.RUNTIME_STYLES);
        paths.addAll(requiredPaths);
        java.util.ArrayList<RuntimeEntryDescriptor> missing = new java.util.ArrayList<>();
        for (ContainerPath path : paths) {
            RuntimeEntryDescriptor descriptor = state.entries().get(path);
            if (descriptor == null) {
                return Optional.empty();
            }
            MinimapCacheKey cacheKey = cacheKey(generation, descriptor.sha256(), path);
            Optional<byte[]> cached = diskCache.get(cacheKey);
            if (cached.isEmpty()) {
                missing.add(descriptor);
                continue;
            }
            byte[] payload = cached.orElseThrow();
            try {
                RuntimeEntryValidation.validateEntry(state.manifest(), path, payload);
            } catch (ContainerValidationException | IllegalArgumentException exception) {
                return Optional.empty();
            }
            if (!entryValidator.validate(cacheKey, payload)) {
                return Optional.empty();
            }
            entryStore.stage(cacheKey, payload);
        }
        return Optional.of(List.copyOf(missing));
    }

    public boolean activateIfValid(MinimapCacheKey cacheKey, byte[] payload, boolean validationPassed) {
        if (!validationPassed) {
            return entryStore.tryActivate(cacheKey, payload, false);
        }
        if (!diskCache.put(cacheKey, payload)) {
            return false;
        }
        entryStore.activate(cacheKey, payload);
        return true;
    }

    public Optional<RuntimeEntryStore.ActiveEntry> active(com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey mapKey) {
        return entryStore.active(mapKey);
    }

    public Optional<RuntimeEntryStore.ActiveRuntime> activeRuntime(
            com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey mapKey
    ) {
        return entryStore.activeRuntime(mapKey);
    }

    public synchronized void clearTransientState() {
        manifests.clear();
        entryStore.clear();
    }

    private static boolean canonicalTransferGeometry(TransferKey transferKey) {
        long expected = (transferKey.totalLength() - 1L)
                / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L;
        return expected == transferKey.fragmentCount();
    }

    private static MinimapCacheKey cacheKey(
            RuntimeGeneration generation,
            Sha256 objectHash,
            ContainerPath path
    ) {
        return new MinimapCacheKey(
                generation.serverIdentity(),
                generation.dimension(),
                generation.mapKey(),
                generation.documentId(),
                generation.revision(),
                generation.runtimeHash(),
                objectHash,
                path.value()
        );
    }

    private record ManifestState(
            RuntimeManifest manifest,
            Map<ContainerPath, RuntimeEntryDescriptor> entries
    ) {
        private ManifestState(RuntimeManifest manifest) {
            this(
                    manifest,
                    manifest.entries().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                            RuntimeEntryDescriptor::path,
                            entry -> entry
                    ))
            );
        }
    }
}
