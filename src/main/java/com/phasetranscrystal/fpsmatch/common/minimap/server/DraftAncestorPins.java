package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;

import java.util.Objects;

public interface DraftAncestorPins {
    DraftAncestorPins NONE = new DraftAncestorPins() {
        @Override
        public boolean supportsPersistentPins() {
            return false;
        }

        @Override
        public void pin(
                MapKey mapKey,
                long revision,
                Sha256 expectedSourceHash,
                String pinId
        ) {
        }

        @Override
        public void unpin(MapKey mapKey, long revision, String pinId) {
        }
    };

    default boolean supportsPersistentPins() {
        return true;
    }

    void pin(
            MapKey mapKey,
            long revision,
            Sha256 expectedSourceHash,
            String pinId
    );

    void unpin(MapKey mapKey, long revision, String pinId);

    static DraftAncestorPins repository(MinimapRepository repository) {
        Objects.requireNonNull(repository, "repository");
        return new DraftAncestorPins() {
            @Override
            public void pin(
                    MapKey mapKey,
                    long revision,
                    Sha256 expectedSourceHash,
                    String pinId
            ) {
                repository.pinCommittedRevision(
                        mapKey, revision, expectedSourceHash, pinId
                );
            }

            @Override
            public void unpin(MapKey mapKey, long revision, String pinId) {
                repository.unpinRevision(mapKey, revision, pinId);
            }
        };
    }
}
