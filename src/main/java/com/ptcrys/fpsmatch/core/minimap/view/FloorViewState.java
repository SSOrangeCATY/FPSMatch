package com.ptcrys.fpsmatch.core.minimap.view;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;
import java.util.Optional;

public final class FloorViewState {
    private final FloorViewMode mode;
    private final String automaticFloorId;
    private final String manualFloorId;
    private final int manualTimeoutRemaining;

    private FloorViewState(
            FloorViewMode mode,
            String automaticFloorId,
            String manualFloorId,
            int manualTimeoutRemaining
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.automaticFloorId = automaticFloorId;
        this.manualFloorId = manualFloorId;
        this.manualTimeoutRemaining = manualTimeoutRemaining;
    }

    public static FloorViewState automatic(String floorId) {
        requireSlug(floorId);
        return new FloorViewState(FloorViewMode.AUTOMATIC, floorId, null, 0);
    }

    public static FloorViewState manual(String floorId, int timeoutTicks) {
        requireSlug(floorId);
        if (timeoutTicks < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        return new FloorViewState(FloorViewMode.MANUAL, null, floorId, timeoutTicks);
    }

    public FloorViewMode mode() {
        return mode;
    }

    public int manualTimeoutRemaining() {
        return manualTimeoutRemaining;
    }

    public Optional<String> effectiveFloorId() {
        if (mode == FloorViewMode.MANUAL && manualTimeoutRemaining > 0) {
            return Optional.ofNullable(manualFloorId);
        }
        return Optional.ofNullable(automaticFloorId != null ? automaticFloorId : manualFloorId);
    }

    public FloorViewState withAutomaticFloor(String floorId) {
        requireSlug(floorId);
        if (mode == FloorViewMode.MANUAL && manualTimeoutRemaining > 0) {
            return new FloorViewState(mode, floorId, manualFloorId, manualTimeoutRemaining);
        }
        return automatic(floorId);
    }

    public FloorViewState withManualFloor(String floorId, int timeoutTicks) {
        requireSlug(floorId);
        if (timeoutTicks < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        String automatic = automaticFloorId != null
                ? automaticFloorId
                : effectiveFloorId().orElse(floorId);
        return new FloorViewState(
                FloorViewMode.MANUAL,
                automatic,
                floorId,
                timeoutTicks
        );
    }

    public FloorViewState tickTimeout(int ticks) {
        if (mode != FloorViewMode.MANUAL || manualTimeoutRemaining <= 0) {
            return this;
        }
        int next = Math.max(0, manualTimeoutRemaining - Math.max(0, ticks));
        if (next == 0) {
            if (automaticFloorId != null) {
                return automatic(automaticFloorId);
            }
            return new FloorViewState(FloorViewMode.AUTOMATIC, manualFloorId, null, 0);
        }
        return new FloorViewState(mode, automaticFloorId, manualFloorId, next);
    }

    private static void requireSlug(String floorId) {
        if (!MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Floor id must be a valid internal slug");
        }
    }
}
