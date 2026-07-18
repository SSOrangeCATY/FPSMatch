package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface MinimapPermissionPolicy {
    Optional<Boolean> mayPerform(UUID actorId, MapKey mapKey, MinimapAction action);
}
