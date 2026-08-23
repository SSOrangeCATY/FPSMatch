package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.concurrent.atomic.AtomicLong;

public final class BakeGeneration {
    private final AtomicLong value;

    public BakeGeneration(long initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("Bake generation must be non-negative");
        }
        this.value = new AtomicLong(initial);
    }

    public long value() {
        return value.get();
    }

    public long next() {
        return value.incrementAndGet();
    }

    public boolean isCurrent(long observed) {
        return observed == value.get();
    }
}
