package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.Objects;
import java.util.Set;

public record SpawnCandidate(String id, SpawnVector position, Set<String> tags) {
    public SpawnCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}
