package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.time.Duration;

final class PublishTargetFixture {
    static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    static final NamespacedId DOCUMENT = NamespacedId.parse("fpsmatch:test-map");

    private PublishTargetFixture() {
    }

    static PublishTransaction reserve(
            MinimapRepository repository,
            MapKey key,
            long baseRevision
    ) {
        return repository.reserve(key, DIMENSION, DOCUMENT, baseRevision);
    }

    static PublishTransaction reserve(
            MinimapRepository repository,
            MapKey key,
            long baseRevision,
            Duration ttl
    ) {
        return repository.reserve(key, DIMENSION, DOCUMENT, baseRevision, ttl);
    }
}
