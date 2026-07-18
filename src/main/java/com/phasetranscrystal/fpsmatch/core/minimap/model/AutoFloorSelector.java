package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AutoFloorSelector {
    private static final Comparator<MinimapFloor> CANDIDATE_ORDER = Comparator
            .comparingInt(MinimapFloor::autoPriority).reversed()
            .thenComparing(MinimapFloor::id);

    private final List<MinimapFloor> floors;
    private final Map<String, MinimapFloor> floorsById;

    public AutoFloorSelector(List<MinimapFloor> floors) {
        Objects.requireNonNull(floors, "floors");
        this.floors = List.copyOf(floors);
        this.floorsById = validateAndIndex(this.floors);
    }

    public AutoFloorState resolve(AutoFloorState previous, double y, boolean revisionChanged) {
        Objects.requireNonNull(previous, "previous");
        if (!Double.isFinite(y)) {
            throw new IllegalArgumentException("Floor selection height must be finite");
        }

        if (revisionChanged || previous instanceof AutoFloorState.None) {
            return resolveBase(y);
        }

        AutoFloorState.Floor selected = (AutoFloorState.Floor) previous;
        MinimapFloor current = floorsById.get(selected.floorId());
        if (current == null) {
            return resolveBase(y);
        }

        MinimapFloor higherPriority = floors.stream()
                .filter(floor -> floor.autoPriority() > current.autoPriority())
                .filter(floor -> floor.containsEnter(y))
                .sorted(CANDIDATE_ORDER)
                .findFirst()
                .orElse(null);
        if (higherPriority != null) {
            return AutoFloorState.floor(higherPriority.id());
        }

        if (current.containsExit(y)) {
            return previous;
        }
        return resolveBase(y);
    }

    private AutoFloorState resolveBase(double y) {
        return floors.stream()
                .filter(floor -> floor.containsBase(y))
                .sorted(CANDIDATE_ORDER)
                .findFirst()
                .<AutoFloorState>map(floor -> AutoFloorState.floor(floor.id()))
                .orElseGet(AutoFloorState::none);
    }

    private static Map<String, MinimapFloor> validateAndIndex(List<MinimapFloor> floors) {
        Map<String, MinimapFloor> byId = new HashMap<>();
        for (MinimapFloor floor : floors) {
            Objects.requireNonNull(floor, "floor");
            if (byId.putIfAbsent(floor.id(), floor) != null) {
                throw new IllegalArgumentException("Duplicate floor ID: " + floor.id());
            }
        }
        for (int leftIndex = 0; leftIndex < floors.size(); leftIndex++) {
            MinimapFloor left = floors.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < floors.size(); rightIndex++) {
                MinimapFloor right = floors.get(rightIndex);
                if (left.autoPriority() == right.autoPriority()
                        && rangesOverlap(left.minY(), left.maxY(), right.minY(), right.maxY())) {
                    throw new IllegalArgumentException(
                            "Same-priority floor ranges overlap: " + left.id() + " and " + right.id()
                    );
                }
            }
        }
        return Map.copyOf(byId);
    }

    private static boolean rangesOverlap(double leftMin, double leftMax, double rightMin, double rightMax) {
        return leftMin < rightMax && rightMin < leftMax;
    }
}
