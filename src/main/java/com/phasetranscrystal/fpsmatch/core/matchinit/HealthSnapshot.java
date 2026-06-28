package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.List;
import java.util.Objects;

public record HealthSnapshot(int maxHealth, List<Integer> chunks) {
    public HealthSnapshot {
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
    }

    public int initialHealth() {
        return Math.min(maxHealth, chunks.stream().mapToInt(Integer::intValue).sum());
    }
}
