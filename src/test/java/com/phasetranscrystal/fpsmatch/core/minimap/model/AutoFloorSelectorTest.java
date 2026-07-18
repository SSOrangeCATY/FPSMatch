package com.phasetranscrystal.fpsmatch.core.minimap.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoFloorSelectorTest {
    @Test
    void firstResolutionUsesBaseRangeWithoutEnterHysteresis() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("ground", 0, 10, 0, 0.5, 1),
                floor("upper", 8, 20, 10, 0.5, 1)
        ));

        assertEquals(AutoFloorState.floor("upper"),
                selector.resolve(AutoFloorState.none(), 8.1, false));
        assertEquals(AutoFloorState.none(),
                selector.resolve(AutoFloorState.none(), 20, false));
    }

    @Test
    void higherPriorityFloorMustReachEnterRangeBeforeSwitching() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("ground", 0, 10, 0, 0.5, 1),
                floor("upper", 8, 20, 10, 0.5, 1)
        ));

        assertEquals(AutoFloorState.floor("ground"),
                selector.resolve(AutoFloorState.floor("ground"), 8.1, false));
        assertEquals(AutoFloorState.floor("upper"),
                selector.resolve(AutoFloorState.floor("ground"), 8.5, false));
    }

    @Test
    void currentFloorStaysInsideExitRangeThenFallsBackByBaseRange() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("ground", 0, 10, 0, 0.5, 1),
                floor("upper", 8, 20, 10, 0.5, 1)
        ));

        assertEquals(AutoFloorState.floor("upper"),
                selector.resolve(AutoFloorState.floor("upper"), 7.2, false));
        assertEquals(AutoFloorState.floor("ground"),
                selector.resolve(AutoFloorState.floor("upper"), 6.9, false));
    }

    @Test
    void holesRemainTemporarilyOnlyWithinCurrentExitRange() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("ground", 0, 10, 0, 0.5, 1),
                floor("upper", 20, 30, 0, 0.5, 1)
        ));

        assertEquals(AutoFloorState.floor("ground"),
                selector.resolve(AutoFloorState.floor("ground"), 10.5, false));
        assertEquals(AutoFloorState.none(),
                selector.resolve(AutoFloorState.floor("ground"), 11, false));
    }

    @Test
    void teleportDeletedFloorAndRevisionChangeResolveDirectly() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("ground", 0, 10, 0, 0.5, 1),
                floor("upper", 20, 30, 0, 0.5, 1)
        ));
        assertEquals(AutoFloorState.floor("upper"),
                selector.resolve(AutoFloorState.floor("ground"), 25, false));
        assertEquals(AutoFloorState.floor("ground"),
                selector.resolve(AutoFloorState.floor("deleted"), 5, false));

        AutoFloorSelector overlappingPriority = new AutoFloorSelector(List.of(
                floor("ground", 0, 10, 0, 0.5, 1),
                floor("upper", 8, 20, 10, 0.5, 1)
        ));
        assertEquals(AutoFloorState.floor("upper"),
                overlappingPriority.resolve(AutoFloorState.floor("ground"), 8.1, true));
    }

    @Test
    void validatesFloorDefinitionsBeforeRuntimeSelection() {
        assertThrows(IllegalArgumentException.class, () -> new AutoFloorSelector(List.of(
                floor("a", 0, 10, 5, 0.5, 1),
                floor("b", 9, 20, 5, 0.5, 1)
        )));
        assertThrows(IllegalArgumentException.class, () -> new AutoFloorSelector(List.of(
                floor("same", 0, 10, 0, 0.5, 1),
                floor("same", 20, 30, 0, 0.5, 1)
        )));
        assertThrows(IllegalArgumentException.class,
                () -> new MinimapFloor("bad", 0, 1, 0, 0.5, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MinimapFloor("bad", 0, 10, 0, -0.1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MinimapFloor("bad", 0, 10, 0, 0.5, 16.1));
        assertThrows(IllegalArgumentException.class,
                () -> new MinimapFloor("bad", 0, Double.NaN, 0, 0.5, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AutoFloorSelector(List.of(floor("ground", 0, 10, 0, 0.5, 1)))
                        .resolve(AutoFloorState.none(), Double.NaN, false));
    }

    @Test
    void enterAndExitRangesRemainHalfOpenAtBothEdges() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("ground", 0, 20, 0, 0.5, 1),
                floor("bridge", 8, 10, 10, 0.5, 1)
        ));

        assertEquals(AutoFloorState.floor("bridge"),
                selector.resolve(AutoFloorState.floor("ground"), 9.499, false));
        assertEquals(AutoFloorState.floor("ground"),
                selector.resolve(AutoFloorState.floor("ground"), 9.5, false));
        assertEquals(AutoFloorState.floor("bridge"),
                selector.resolve(AutoFloorState.floor("bridge"), 7, false));
        assertEquals(AutoFloorState.floor("ground"),
                selector.resolve(AutoFloorState.floor("bridge"), 6.999, false));
    }

    @Test
    void teleportCanSkipMultipleFloorsAndLowerPriorityDoesNotPreemptCurrent() {
        AutoFloorSelector selector = new AutoFloorSelector(List.of(
                floor("low", 0, 10, 1, 0.5, 1),
                floor("middle", 20, 30, 1, 0.5, 1),
                floor("high", 40, 50, 1, 0.5, 1),
                floor("underlay", 42, 48, 0, 0.5, 1)
        ));

        assertEquals(AutoFloorState.floor("high"),
                selector.resolve(AutoFloorState.floor("low"), 45, false));
        assertEquals(AutoFloorState.floor("high"),
                selector.resolve(AutoFloorState.floor("high"), 45, false));
    }

    private static MinimapFloor floor(
            String id,
            double minY,
            double maxY,
            int priority,
            double enterMargin,
            double exitMargin
    ) {
        return new MinimapFloor(id, minY, maxY, priority, enterMargin, exitMargin);
    }
}
