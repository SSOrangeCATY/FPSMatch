package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;

public final class DefaultMinimapPermissionPolicy implements MinimapPermissionPolicy {
    private final IntSupplier requiredLevel;
    private final Function<UUID, OptionalInt> playerPermissionLevel;

    public DefaultMinimapPermissionPolicy(
            IntSupplier requiredLevel,
            Function<UUID, OptionalInt> playerPermissionLevel
    ) {
        this.requiredLevel = Objects.requireNonNull(requiredLevel, "requiredLevel");
        this.playerPermissionLevel = Objects.requireNonNull(
                playerPermissionLevel, "playerPermissionLevel"
        );
    }

    public static DefaultMinimapPermissionPolicy onlinePlayers(
            IntSupplier requiredLevel
    ) {
        return new DefaultMinimapPermissionPolicy(requiredLevel, actorId -> {
            if (!FPSMCore.initialized()) {
                return OptionalInt.empty();
            }
            return FPSMCore.getInstance().getPlayerByUUID(actorId)
                    .map(player -> {
                        for (int level = 4; level >= 0; level--) {
                            if (player.hasPermissions(level)) {
                                return OptionalInt.of(level);
                            }
                        }
                        return OptionalInt.of(0);
                    })
                    .orElseGet(OptionalInt::empty);
        });
    }

    @Override
    public Optional<Boolean> mayPerform(
            UUID actorId,
            MapKey mapKey,
            MinimapAction action
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(action, "action");
        int level = requiredLevel.getAsInt();
        if (level < 2 || level > 4) {
            throw new IllegalStateException(
                    "Minimap editor permission level must be in [2, 4]"
            );
        }
        return Optional.of(
                playerPermissionLevel.apply(actorId).orElse(-1) >= level
        );
    }
}
