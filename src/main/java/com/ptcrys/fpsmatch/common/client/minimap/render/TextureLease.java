package com.ptcrys.fpsmatch.common.client.minimap.render;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class TextureLease {
    private final String textureId;
    private final AtomicInteger refs = new AtomicInteger(1);
    private final Runnable onRelease;
    private volatile boolean active = true;

    TextureLease(String textureId, Runnable onRelease) {
        this.textureId = Objects.requireNonNull(textureId, "textureId");
        this.onRelease = Objects.requireNonNull(onRelease, "onRelease");
    }

    public String textureId() {
        return textureId;
    }

    public boolean isActive() {
        return active && refs.get() > 0;
    }

    public void retain() {
        if (!active) {
            throw new IllegalStateException("Texture lease is inactive");
        }
        refs.incrementAndGet();
    }

    public void release() {
        if (refs.decrementAndGet() == 0) {
            active = false;
            onRelease.run();
        }
    }

    void forceRelease() {
        if (refs.getAndSet(0) > 0) {
            active = false;
            onRelease.run();
        }
    }
}