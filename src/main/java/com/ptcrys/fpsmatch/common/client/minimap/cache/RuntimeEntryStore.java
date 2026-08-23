package com.ptcrys.fpsmatch.common.client.minimap.cache;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeEntryStore {
    private final Map<RuntimeIdentityKey, ActiveRuntime> active = new ConcurrentHashMap<>();
    private final Map<MapKey, RuntimeIdentityKey> activeByMap = new ConcurrentHashMap<>();
    private final Map<GenerationKey, Map<String, ActiveEntry>> staged = new ConcurrentHashMap<>();

    public void activate(MinimapCacheKey key, byte[] payload) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        ActiveEntry entry = new ActiveEntry(key, payload);
        stage(key, payload);
        activateGeneration(List.of(entry));
    }

    public boolean stage(MinimapCacheKey key, byte[] payload) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        GenerationKey generation = GenerationKey.of(key);
        staged.computeIfAbsent(generation, ignored -> new ConcurrentHashMap<>())
                .put(key.stablePath(), new ActiveEntry(key, payload));
        return true;
    }

    public synchronized void activateGeneration(List<ActiveEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Active runtime generation cannot be empty");
        }
        ActiveEntry first = Objects.requireNonNull(entries.get(0), "entry");
        GenerationKey generation = GenerationKey.of(first.key());
        LinkedHashMap<String, ActiveEntry> copy = new LinkedHashMap<>();
        for (ActiveEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!generation.equals(GenerationKey.of(entry.key()))) {
                throw new IllegalArgumentException("Runtime entries belong to different generations");
            }
            if (copy.put(entry.key().stablePath(), entry) != null) {
                throw new IllegalArgumentException("Runtime entry path is duplicated");
            }
        }
        active.put(generation.identity(), new ActiveRuntime(
                generation.serverIdentity(),
                generation.dimension(),
                generation.mapKey(),
                generation.documentId(),
                generation.revision(),
                generation.runtimeHash(),
                copy
        ));
        activeByMap.put(generation.mapKey(), generation.identity());
        staged.remove(generation);
    }

    public synchronized boolean activateStaged(
            MinimapCacheKey generationKey,
            List<String> requiredPaths
    ) {
        Objects.requireNonNull(generationKey, "generationKey");
        Objects.requireNonNull(requiredPaths, "requiredPaths");
        GenerationKey generation = GenerationKey.of(generationKey);
        LinkedHashMap<String, ActiveEntry> candidates = availableEntries(generation);
        if (!candidates.keySet().containsAll(requiredPaths)) {
            return false;
        }
        activateGeneration(List.copyOf(candidates.values()));
        return true;
    }

    public boolean hasStaged(MinimapCacheKey generationKey, List<String> requiredPaths) {
        Objects.requireNonNull(generationKey, "generationKey");
        Objects.requireNonNull(requiredPaths, "requiredPaths");
        return availableEntries(GenerationKey.of(generationKey))
                .keySet().containsAll(requiredPaths);
    }

    public Optional<byte[]> stagedPayload(MinimapCacheKey generationKey, String stablePath) {
        Objects.requireNonNull(generationKey, "generationKey");
        Objects.requireNonNull(stablePath, "stablePath");
        ActiveEntry entry = availableEntries(GenerationKey.of(generationKey)).get(stablePath);
        return entry == null ? Optional.empty() : Optional.of(entry.payload());
    }

    private LinkedHashMap<String, ActiveEntry> availableEntries(GenerationKey generation) {
        LinkedHashMap<String, ActiveEntry> entries = new LinkedHashMap<>();
        ActiveRuntime current = active.get(generation.identity());
        if (current != null) {
            entries.putAll(current.entries());
        }
        Map<String, ActiveEntry> pending = staged.get(generation);
        if (pending != null) {
            entries.putAll(pending);
        }
        return entries;
    }

    /**
     * @param validationPassed false keeps the previous active revision (failed new revision).
     */
    public boolean tryActivate(MinimapCacheKey key, byte[] payload, boolean validationPassed) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        if (!validationPassed) {
            return false;
        }
        activate(key, payload);
        return true;
    }

    public Optional<ActiveEntry> active(MapKey mapKey) {
        ActiveRuntime runtime = activeRuntime(mapKey).orElse(null);
        if (runtime == null || runtime.entries().isEmpty()) {
            return Optional.empty();
        }
        return runtime.entries().values().stream().findFirst();
    }

    public Optional<ActiveRuntime> activeRuntime(MapKey mapKey) {
        RuntimeIdentityKey identity = activeByMap.get(mapKey);
        return identity == null ? Optional.empty() : Optional.ofNullable(active.get(identity));
    }

    public Optional<ActiveRuntime> activeRuntime(MinimapCacheKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(active.get(RuntimeIdentityKey.of(key)));
    }

    public synchronized void clear() {
        active.clear();
        activeByMap.clear();
        staged.clear();
    }

    public record ActiveEntry(MinimapCacheKey key, byte[] payload) {
        public ActiveEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(payload, "payload");
            payload = payload.clone();
        }

        public long revision() {
            return key.revision();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    public record ActiveRuntime(
            String serverIdentity,
            NamespacedId dimension,
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash,
            Map<String, ActiveEntry> entries
    ) {
        public ActiveRuntime {
            Objects.requireNonNull(serverIdentity, "serverIdentity");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(mapKey, "mapKey");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            entries = Map.copyOf(new LinkedHashMap<>(entries));
        }

        public Optional<byte[]> entry(String stablePath) {
            ActiveEntry entry = entries.get(stablePath);
            return entry == null ? Optional.empty() : Optional.of(entry.payload());
        }
    }

    private record GenerationKey(
            String serverIdentity,
            NamespacedId dimension,
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash
    ) {
        private static GenerationKey of(MinimapCacheKey key) {
            return new GenerationKey(
                    key.serverIdentity(), key.dimension(), key.mapKey(), key.documentId(),
                    key.revision(), key.runtimeHash()
            );
        }

        private RuntimeIdentityKey identity() {
            return new RuntimeIdentityKey(
                    serverIdentity, dimension, mapKey, documentId, revision, runtimeHash
            );
        }
    }

    private record RuntimeIdentityKey(
            String serverIdentity,
            NamespacedId dimension,
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash
    ) {
        private static RuntimeIdentityKey of(MinimapCacheKey key) {
            return GenerationKey.of(key).identity();
        }
    }
}
