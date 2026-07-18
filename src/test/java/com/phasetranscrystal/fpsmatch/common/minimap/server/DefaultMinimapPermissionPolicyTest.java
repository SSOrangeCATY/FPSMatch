package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMinimapPermissionPolicyTest {
    private static final UUID ACTOR = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final MapKey MAP = new MapKey("fpsmatch:test", "Test Map");

    @Test
    void requiresTheConfiguredPermissionLevelForEveryAction() {
        DefaultMinimapPermissionPolicy policy = new DefaultMinimapPermissionPolicy(
                () -> 3,
                actor -> OptionalInt.of(2)
        );

        assertEquals(false, policy.mayPerform(
                ACTOR, MAP, MinimapAction.OPEN_EDITOR
        ).orElseThrow());
        assertEquals(false, policy.mayPerform(
                ACTOR, MAP, MinimapAction.COMMIT_PUBLISH
        ).orElseThrow());

        policy = new DefaultMinimapPermissionPolicy(
                () -> 3,
                actor -> OptionalInt.of(3)
        );
        assertEquals(true, policy.mayPerform(
                ACTOR, MAP, MinimapAction.REQUEST_WORLD_SNAPSHOT
        ).orElseThrow());
    }

    @Test
    void offlinePlayersDenyAndPermissionLevelCannotBeConfiguredBelowTwo() {
        DefaultMinimapPermissionPolicy offline = new DefaultMinimapPermissionPolicy(
                () -> 2,
                actor -> OptionalInt.empty()
        );
        assertEquals(false, offline.mayPerform(
                ACTOR, MAP, MinimapAction.FETCH_SOURCE
        ).orElseThrow());

        DefaultMinimapPermissionPolicy invalid = new DefaultMinimapPermissionPolicy(
                () -> 1,
                actor -> OptionalInt.of(4)
        );
        assertThrows(IllegalStateException.class, () -> invalid.mayPerform(
                ACTOR, MAP, MinimapAction.SAVE_DRAFT
        ));
    }
}
