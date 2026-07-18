package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;

import java.util.Objects;

public final class RecoveryService {
    private final MinimapRepository repository;

    public RecoveryService(MinimapRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public PublishOutcome recover(MapKey key) {
        return repository.recover(key);
    }
}
