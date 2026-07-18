package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapTextureCache;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client lifecycle for minimap runtime resources.
 * Ordinary resource reload preserves disk cache; logout/reset releases GPU/runtime state.
 */
public final class MinimapClientLifecycle {
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicInteger reloadCount = new AtomicInteger();
    private final AtomicInteger resetCount = new AtomicInteger();
    private MinimapTextureCache textureCache;
    private MinimapDiskCache diskCache;
    private boolean preserveDiskCacheOnReload = true;

    public void activate(MinimapTextureCache textureCache, MinimapDiskCache diskCache) {
        this.textureCache = Objects.requireNonNull(textureCache, "textureCache");
        this.diskCache = Objects.requireNonNull(diskCache, "diskCache");
        active.set(true);
    }

    public void onResourceReload() {
        if (!active.get()) {
            return;
        }
        reloadCount.incrementAndGet();
        if (textureCache != null) {
            textureCache.reset();
        }
        // disk cache intentionally retained on ordinary resource reload
        if (!preserveDiskCacheOnReload && diskCache != null) {
            // no clear API; marker only — disk retention is the default contract
        }
    }

    public void onLogoutOrReset() {
        resetCount.incrementAndGet();
        if (textureCache != null) {
            textureCache.reset();
        }
        active.set(false);
    }

    public boolean isActive() {
        return active.get();
    }

    public int reloadCount() {
        return reloadCount.get();
    }

    public int resetCount() {
        return resetCount.get();
    }

    public void setPreserveDiskCacheOnReload(boolean preserve) {
        this.preserveDiskCacheOnReload = preserve;
    }

    public boolean preservesDiskCacheOnReload() {
        return preserveDiskCacheOnReload;
    }
}