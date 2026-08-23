package com.ptcrys.fpsmatch.common.client.minimap.cache;

import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MinimapDiskCache {
    private final Path root;
    private final long maxBytes;
    private final Map<MinimapCacheKey, CachedFile> lru =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Set<MinimapCacheKey> pinned = ConcurrentHashMap.newKeySet();
    private long cachedBytes;

    public MinimapDiskCache(Path root, long maxBytes) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    public synchronized boolean put(MinimapCacheKey key, byte[] payload) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        if (!Sha256Digest.of(payload).equals(key.objectHash())) {
            return false;
        }
        try {
            Path target = pathFor(key);
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            Files.write(temp, payload);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            remember(key, target, payload.length);
            evictIfNeeded();
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            cleanupTemp(key);
            return false;
        }
    }

    public synchronized Optional<byte[]> get(MinimapCacheKey key) {
        Objects.requireNonNull(key, "key");
        Path path;
        try {
            path = pathFor(key);
        } catch (IllegalArgumentException | SecurityException exception) {
            lru.remove(key);
            return Optional.empty();
        }
        if (!Files.isRegularFile(path)) {
            forget(key);
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (!Sha256Digest.of(bytes).equals(key.objectHash())) {
                Files.deleteIfExists(path);
                forget(key);
                return Optional.empty();
            }
            remember(key, path, bytes.length);
            evictIfNeeded();
            return Optional.of(bytes);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public void pin(MinimapCacheKey key) {
        pinned.add(Objects.requireNonNull(key, "key"));
    }

    public void unpin(MinimapCacheKey key) {
        pinned.remove(Objects.requireNonNull(key, "key"));
    }

    private void evictIfNeeded() {
        Iterator<Map.Entry<MinimapCacheKey, CachedFile>> iterator =
                lru.entrySet().iterator();
        while (cachedBytes > maxBytes && iterator.hasNext()) {
            Map.Entry<MinimapCacheKey, CachedFile> entry = iterator.next();
            if (pinned.contains(entry.getKey())) {
                continue;
            }
            try {
                Files.deleteIfExists(entry.getValue().path());
            } catch (IOException ignored) {
                continue;
            }
            cachedBytes -= entry.getValue().byteLength();
            iterator.remove();
        }
    }

    private void remember(MinimapCacheKey key, Path path, long byteLength) {
        CachedFile previous = lru.put(key, new CachedFile(path, byteLength));
        if (previous != null) {
            cachedBytes -= previous.byteLength();
        }
        cachedBytes += byteLength;
    }

    private void forget(MinimapCacheKey key) {
        CachedFile removed = lru.remove(key);
        if (removed != null) {
            cachedBytes -= removed.byteLength();
        }
    }

    private void cleanupTemp(MinimapCacheKey key) {
        try {
            Path target = pathFor(key);
            Files.deleteIfExists(target.resolveSibling(target.getFileName().toString() + ".tmp"));
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            // best-effort
        }
    }

    private Path pathFor(MinimapCacheKey key) {
        Path target = root
                .resolve(sanitize(key.serverIdentity()))
                .resolve(sanitize(key.dimension().toString()))
                .resolve(sanitize(key.mapKey().gameType()))
                .resolve(sanitize(key.mapKey().mapName()))
                .resolve(sanitize(key.documentId().toString()))
                .resolve(Long.toString(key.revision()))
                .resolve(key.runtimeHash().value())
                .resolve(key.objectHash().value())
                .resolve(sanitize(key.stablePath()))
                .toAbsolutePath()
                .normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("Cache path escapes its root");
        }
        return target;
    }

    private static String sanitize(String value) {
        String sanitized = value.replace(':', '_').replace('/', '_').replace('\\', '_');
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new IllegalArgumentException("Cache path segment is invalid");
        }
        return sanitized;
    }

    private record CachedFile(Path path, long byteLength) {
    }
}
