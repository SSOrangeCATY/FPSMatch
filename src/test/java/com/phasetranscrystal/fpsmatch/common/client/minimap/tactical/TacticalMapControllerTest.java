package com.phasetranscrystal.fpsmatch.common.client.minimap.tactical;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapRuntime;
import com.phasetranscrystal.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapControllerTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");
    private static final NamespacedId DOC = NamespacedId.parse("fpsmatch:dust2");

    @Test
    void openRequiresInGameCapabilityAndNoTextInput() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease hud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        MinimapScopeLease tactical = acknowledgedTactical(runtime);

        TacticalMapController controller = new TacticalMapController(runtime);
        assertFalse(controller.openAcknowledged(
                new TacticalOpenRequest(false, true, false, false), tactical
        ));
        assertFalse(controller.openAcknowledged(
                new TacticalOpenRequest(true, false, false, false), tactical
        ));
        assertFalse(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, true, false), tactical
        ));
        assertTrue(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false), tactical
        ));
        assertTrue(controller.isOpen());

        // held key / passive must not re-open repeatedly
        assertFalse(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false), tactical
        ));
        assertTrue(runtime.canCommit(runtime.currentGeneration().orElseThrow(), hud));
    }

    @Test
    void passiveUpdatesNeverOpenScreenAndClosingRetainsHudLease() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease hud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        MinimapScopeLease acknowledged = acknowledgedTactical(runtime);
        TacticalMapController controller = new TacticalMapController(runtime);

        controller.applyStateIfOpen(state -> state.withZoom(2.0));
        assertFalse(controller.isOpen());

        assertTrue(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false), acknowledged
        ));
        MinimapScopeLease tactical = controller.lease().orElseThrow();
        controller.applyStateIfOpen(state -> state.withZoom(3.0));
        assertEquals(3.0, controller.state().zoom(), 1e-9);

        controller.close();
        assertFalse(controller.isOpen());
        assertFalse(runtime.canCommit(runtime.currentGeneration().orElseThrow(), tactical));
        assertTrue(runtime.canCommit(runtime.currentGeneration().orElseThrow(), hud));
    }

    @Test
    void panZoomFitAndFloorSelectorAreIndependentFromHudAutomaticFloor() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        TacticalMapController controller = new TacticalMapController(runtime);
        assertTrue(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false),
                acknowledgedTactical(runtime)
        ));

        TacticalViewport viewport = new TacticalViewport(
                new CanvasBounds(64, 64),
                new CanvasRect(8, 16, 56, 48),
                256,
                128
        );
        controller.applyViewport(
                viewport,
                viewport.fitAll(),
                FloorViewState.automatic("ground")
        );
        controller.panByPixels(10_000, 0);
        assertEquals(-51.2, controller.state().panX(), 1e-9);
        controller.fitAll();
        controller.zoomByWheel(1, 64, 0);
        assertEquals(2.5, controller.state().zoom(), 1e-9);
        controller.fitFloor();
        assertEquals(4.0, controller.state().zoom(), 1e-9);
        controller.selectFloor("upper");
        assertEquals(FloorViewMode.MANUAL, controller.state().floor().mode());
        assertEquals("upper", controller.state().floor().effectiveFloorId().orElseThrow());

        controller.setAutomaticFloor("ground");
        assertEquals(FloorViewMode.AUTOMATIC, controller.state().floor().mode());
        assertEquals("ground", controller.state().floor().effectiveFloorId().orElseThrow());

        controller.selectFloor("upper");
        assertEquals(100, controller.state().floor().manualTimeoutRemaining());
        controller.applyViewport(
                viewport,
                viewport.fitFloor(),
                controller.state().floor().withAutomaticFloor("basement")
        );
        controller.resumeAutomaticFloor();
        assertEquals(FloorViewMode.AUTOMATIC, controller.state().floor().mode());
        assertEquals(
                "basement",
                controller.state().floor().effectiveFloorId().orElseThrow()
        );

        controller.setAutomaticFloor("ground");
        controller.selectFloor("upper");

        // timeout returns to automatic without mutating HUD state (HUD not owned here)
        for (int i = 0; i < 100; i++) {
            controller.tick(1);
        }
        assertEquals(FloorViewMode.AUTOMATIC, controller.state().floor().mode());
        assertEquals("ground", controller.state().floor().effectiveFloorId().orElseThrow());
    }

    @Test
    void localMarkerFiltersPersistAndDoNotRequireNetwork() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        TacticalMapController controller = new TacticalMapController(runtime);
        assertTrue(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false),
                acknowledgedTactical(runtime)
        ));
        controller.setHiddenMarkerTypes(Set.of("fpsmatch:type/death"));
        assertEquals(Set.of("fpsmatch:type/death"), controller.state().hiddenMarkerTypes());
        controller.close();
        assertTrue(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false),
                acknowledgedTactical(runtime)
        ));
        assertEquals(Set.of("fpsmatch:type/death"), controller.state().hiddenMarkerTypes());
    }

    @Test
    void tickingManualTimeoutDoesNotResetTheInteractiveCamera() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        TacticalMapController controller = new TacticalMapController(runtime);
        assertTrue(controller.openAcknowledged(
                new TacticalOpenRequest(true, true, false, false),
                acknowledgedTactical(runtime)
        ));
        TacticalViewport viewport = new TacticalViewport(
                new CanvasBounds(64, 64),
                new CanvasRect(8, 16, 56, 48),
                256,
                128
        );
        controller.applyViewport(
                viewport,
                viewport.fitAll(),
                FloorViewState.automatic("ground")
        );
        controller.selectFloor("upper");
        controller.applyViewport(
                viewport,
                com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera
                        .fixedNorth(20, 24, 3, 256, 128),
                controller.state().floor()
        );

        controller.tick(1);

        assertEquals(TacticalMapState.FitMode.NONE, controller.state().fitMode());
        assertEquals(20.0, controller.state().panX(), 1e-9);
        assertEquals(24.0, controller.state().panY(), 1e-9);
        assertEquals(3.0, controller.state().zoom(), 1e-9);
        assertEquals(99, controller.state().floor().manualTimeoutRemaining());
    }

    @Test
    void consumeClickGateRejectsChatAndEditorTextFields() {
        assertFalse(MinimapKeys.canConsumeOpen(false, false, true, false));
        assertFalse(MinimapKeys.canConsumeOpen(true, false, false, true));
        assertFalse(MinimapKeys.canConsumeOpen(true, true, true, false));
        assertTrue(MinimapKeys.canConsumeOpen(true, true, false, false));
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MinimapScopeLease acknowledgedTactical(
            ClientMinimapRuntime runtime
    ) {
        MinimapScopeLease lease = runtime.acquirePending(
                WireIdentity.Scope.TACTICAL_SCREEN
        );
        WireIdentity.RuntimeIdentity identity = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                MAP, NamespacedId.parse("minecraft:overworld")
                        ),
                        DOC
                ),
                1L,
                hash("r1"),
                Optional.empty()
        );
        assertTrue(runtime.acknowledge(lease, identity).isPresent());
        return lease;
    }
}
