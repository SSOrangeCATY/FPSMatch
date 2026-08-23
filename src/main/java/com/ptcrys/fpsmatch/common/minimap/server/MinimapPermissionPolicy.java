package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface MinimapPermissionPolicy {
    Optional<Boolean> mayPerform(UUID actorId, MapKey mapKey, MinimapAction action);
}
