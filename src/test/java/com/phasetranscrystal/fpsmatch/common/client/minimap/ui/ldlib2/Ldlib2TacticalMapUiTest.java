package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapRuntime;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalOpenRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ldlib2TacticalMapUiTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");
    private static final NamespacedId DOC = NamespacedId.parse("fpsmatch:dust2");

    @Test
    void requiresStableWidgetIdsAndRejectsMissingBindings() {
        Ldlib2TacticalMapUi ui = new Ldlib2TacticalMapUi();
        assertTrue(ui.catalog().ids().contains("minimap.tactical.canvas"));
        assertTrue(ui.catalog().ids().contains("minimap.tactical.floor_selector"));
        assertTrue(ui.catalog().ids().contains("minimap.tactical.region_detail"));
        assertTrue(ui.catalog().ids().contains("minimap.tactical.close"));
        assertThrows(IllegalStateException.class, () -> ui.bind(List.of("minimap.tactical.canvas")));
        ui.bind(ui.catalog().ids());
        assertTrue(ui.isBound());
    }

    @Test
    void passiveApplyNeverOpensUi() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        TacticalMapController controller = new TacticalMapController(runtime);
        Ldlib2TacticalMapUi ui = new Ldlib2TacticalMapUi();
        ui.bind(ui.catalog().ids());
        ui.applyIfOpen(controller);
        assertTrue(!ui.isVisible());
        com.phasetranscrystal.fpsmatch.common.client.minimap.MinimapScopeLease lease =
                runtime.acquirePending(
                        com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity.Scope
                                .TACTICAL_SCREEN
                );
        runtime.acknowledge(
                lease,
                new com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity
                        .RuntimeIdentity(
                        new com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity
                                .DocumentBinding(
                                new com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity
                                        .MapTarget(
                                        MAP,
                                        com.phasetranscrystal.fpsmatch.core.minimap.model
                                                .NamespacedId.parse("minecraft:overworld")
                                ),
                                DOC
                        ),
                        1L,
                        hash("r1"),
                        java.util.Optional.empty()
                )
        );
        controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false), lease
        );
        ui.applyIfOpen(controller);
        assertTrue(ui.isVisible());
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }
}
