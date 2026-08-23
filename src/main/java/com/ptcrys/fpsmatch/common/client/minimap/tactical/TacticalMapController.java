package com.ptcrys.fpsmatch.common.client.minimap.tactical;

import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapRuntime;
import com.ptcrys.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.ptcrys.fpsmatch.core.minimap.view.FloorViewMode;
import com.ptcrys.fpsmatch.core.minimap.view.FloorViewState;
import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Pure read-only tactical map controller. No annotations, no LDLib2/Minecraft types.
 * Passive apply paths never open the UI.
 */
public final class TacticalMapController {
    private final ClientMinimapRuntime runtime;
    private MinimapScopeLease lease;
    private boolean open;
    private TacticalMapState state = TacticalMapState.initial();
    private Set<String> preferredHiddenMarkerTypes = Set.of();
    private int manualTimeoutTicks = 100;
    private TacticalViewport viewport;

    public TacticalMapController(ClientMinimapRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public boolean openAcknowledged(
            TacticalOpenRequest request,
            MinimapScopeLease acknowledgedLease
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(acknowledgedLease, "acknowledgedLease");
        if (open || request.passiveOnly()) {
            return false;
        }
        if (!MinimapKeys.canConsumeOpen(
                request.inGame(),
                request.capabilityPresent(),
                request.textInputActive(),
                false
        )) {
            return false;
        }
        if (acknowledgedLease.scope() != WireIdentity.Scope.TACTICAL_SCREEN
                || runtime.currentGeneration().filter(generation ->
                runtime.canCommit(generation, acknowledgedLease)
        ).isEmpty()) {
            return false;
        }
        lease = acknowledgedLease;
        open = true;
        state = TacticalMapState.initial()
                .withHiddenMarkerTypes(preferredHiddenMarkerTypes);
        return true;
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        if (lease != null) {
            runtime.release(WireIdentity.Scope.TACTICAL_SCREEN);
            lease = null;
        }
        state = TacticalMapState.initial()
                .withHiddenMarkerTypes(preferredHiddenMarkerTypes);
        viewport = null;
    }

    public boolean isOpen() {
        return open;
    }

    public Optional<MinimapScopeLease> lease() {
        return Optional.ofNullable(lease);
    }

    public TacticalMapState state() {
        return state;
    }

    public void applyStateIfOpen(UnaryOperator<TacticalMapState> updater) {
        Objects.requireNonNull(updater, "updater");
        if (!open) {
            return;
        }
        state = Objects.requireNonNull(updater.apply(state), "updated state");
    }

    public void applyViewport(
            TacticalViewport viewport,
            ViewportCamera camera,
            FloorViewState floor
    ) {
        if (!open) {
            return;
        }
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(camera, "camera");
        state = state.withFloor(Objects.requireNonNull(floor, "floor"))
                .resolveCamera(
                        camera.panX(), camera.panY(), camera.zoom()
                );
    }

    public void panByPixels(double deltaX, double deltaY) {
        if (!open) {
            return;
        }
        ViewportCamera camera = requireViewport().panByPixels(
                currentCamera(), deltaX, deltaY
        );
        state = state.resolveCamera(
                camera.panX(), camera.panY(), camera.zoom()
        );
    }

    public void zoomByWheel(
            double wheelTicks,
            double cursorX,
            double cursorY
    ) {
        if (!open) {
            return;
        }
        ViewportCamera camera = requireViewport().zoomByWheel(
                currentCamera(), wheelTicks, cursorX, cursorY
        );
        state = state.resolveCamera(
                camera.panX(), camera.panY(), camera.zoom()
        );
    }

    public void fitFloor() {
        if (!open) {
            return;
        }
        applyCamera(requireViewport().fitFloor());
    }

    public void fitAll() {
        if (!open) {
            return;
        }
        applyCamera(requireViewport().fitAll());
    }

    public void selectFloor(String floorId) {
        if (!open) {
            return;
        }
        state = state.withFloor(
                state.floor().withManualFloor(floorId, manualTimeoutTicks)
        );
    }

    public void setAutomaticFloor(String floorId) {
        if (!open) {
            return;
        }
        state = state.withFloor(FloorViewState.automatic(floorId));
    }

    public void resumeAutomaticFloor() {
        if (!open) {
            return;
        }
        state = state.withFloor(FloorViewState.automatic(
                state.floor().tickTimeout(Integer.MAX_VALUE)
                        .effectiveFloorId().orElse("ground")
        ));
    }

    public void tick(int ticks) {
        if (!open) {
            return;
        }
        FloorViewState next = state.floor().tickTimeout(ticks);
        state = state.updateFloor(next);
        if (next.mode() == FloorViewMode.AUTOMATIC || next.manualTimeoutRemaining() == 0) {
            // keep automatic id
            state = state.updateFloor(FloorViewState.automatic(next.effectiveFloorId().orElse("ground")));
        }
    }

    public void setHiddenMarkerTypes(Set<String> hiddenMarkerTypes) {
        preferredHiddenMarkerTypes = Set.copyOf(Objects.requireNonNull(hiddenMarkerTypes, "hiddenMarkerTypes"));
        if (open) {
            state = state.withHiddenMarkerTypes(preferredHiddenMarkerTypes);
        }
    }

    public void resize(int width, int height) {
        if (!open) {
            return;
        }
        state = state.withViewport(width, height);
    }

    public void setManualTimeoutTicks(int manualTimeoutTicks) {
        this.manualTimeoutTicks = Math.max(
                20, Math.min(1200, manualTimeoutTicks)
        );
    }

    private void applyCamera(ViewportCamera camera) {
        state = state.resolveCamera(
                camera.panX(), camera.panY(), camera.zoom()
        );
    }

    private ViewportCamera currentCamera() {
        return ViewportCamera.fixedNorth(
                state.panX(), state.panY(), state.zoom(),
                state.viewportWidth(), state.viewportHeight()
        );
    }

    private TacticalViewport requireViewport() {
        return Objects.requireNonNull(
                viewport, "tactical viewport has not been prepared"
        );
    }
}
