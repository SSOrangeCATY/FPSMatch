package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.capability.map.MinimapCapability;
import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.RuntimeAuthority;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeMinimapServerRuntimeAccessTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");
    private static final NamespacedId OVERWORLD = NamespacedId.parse(
            "minecraft:overworld"
    );
    private static final NamespacedId NETHER = NamespacedId.parse(
            "minecraft:the_nether"
    );
    private static final WireIdentity.MapTarget TARGET = new WireIdentity.MapTarget(
            MAP, OVERWORLD
    );
    private static final MinimapCapability.Binding BINDING =
            new MinimapCapability.Binding(
                    OVERWORLD,
                    NamespacedId.parse("fpsmatch:dust2"),
                    7L,
                    Sha256.parse("1".repeat(64)),
                    Sha256.parse("2".repeat(64))
            );

    @Test
    void acceptsOnlyTheCurrentMapDimensionAndPublishedBinding() {
        RuntimeAuthority authority = ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD, Optional.of(BINDING)
        ).orElseThrow();

        assertEquals(TARGET, authority.target());
        assertEquals(BINDING.documentId(), authority.documentId());
        assertEquals(BINDING.revision(), authority.revision());
        assertEquals(BINDING.sourceHash(), authority.sourceHash());
        assertEquals(BINDING.runtimeHash(), authority.runtimeHash());

        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD, Optional.empty()
        ).isEmpty());
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, new MapKey("cs", "inferno"),
                OVERWORLD, OVERWORLD, Optional.of(BINDING)
        ).isEmpty());
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, NETHER, OVERWORLD, Optional.of(BINDING)
        ).isEmpty());
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, NETHER, Optional.of(BINDING)
        ).isEmpty());
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                new WireIdentity.MapTarget(MAP, NETHER),
                MAP, OVERWORLD, OVERWORLD, Optional.of(BINDING)
        ).isEmpty());
        MinimapCapability.Binding wrongBinding = new MinimapCapability.Binding(
                NETHER, BINDING.documentId(), BINDING.revision(),
                BINDING.sourceHash(), BINDING.runtimeHash()
        );
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD, Optional.of(wrongBinding)
        ).isEmpty());
    }

    @Test
    void permitsMountedUnboundBuiltinButRejectsMissingCapabilityAndStaleContext() {
        RuntimeAuthority builtin = new RuntimeAuthority(
                TARGET,
                BINDING.documentId(),
                0L,
                BINDING.sourceHash(),
                BINDING.runtimeHash()
        );

        assertEquals(Optional.of(builtin), ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD,
                true, true, true, Optional.empty(), Optional.of(builtin)
        ));
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD,
                false, true, true, Optional.empty(), Optional.of(builtin)
        ).isEmpty());
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD,
                true, false, true, Optional.empty(), Optional.of(builtin)
        ).isEmpty());
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD,
                true, true, false, Optional.empty(), Optional.of(builtin)
        ).isEmpty());
        MinimapCapability.Binding wrongDimension = new MinimapCapability.Binding(
                NETHER,
                BINDING.documentId(),
                1L,
                BINDING.sourceHash(),
                BINDING.runtimeHash()
        );
        assertTrue(ForgeMinimapServerRuntimeAccess.authorityFor(
                TARGET, MAP, OVERWORLD, OVERWORLD,
                true, true, true,
                Optional.of(wrongDimension), Optional.of(builtin)
        ).isEmpty());
    }
}
