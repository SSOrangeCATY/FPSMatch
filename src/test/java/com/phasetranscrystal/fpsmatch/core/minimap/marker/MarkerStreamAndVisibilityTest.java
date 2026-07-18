package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerStreamAndVisibilityTest {
    private static final NamespacedId SELF = NamespacedId.parse("fpsmatch:player/self");
    private static final NamespacedId TEAMMATE = NamespacedId.parse("fpsmatch:player/teammate");
    private static final NamespacedId ENEMY = NamespacedId.parse("fpsmatch:player/enemy");
    private static final NamespacedId DEATH = NamespacedId.parse("fpsmatch:event/death");
    private static final NamespacedId TYPE_PLAYER = NamespacedId.parse("fpsmatch:type/player");
    private static final NamespacedId TYPE_DEATH = NamespacedId.parse("fpsmatch:type/death");
    private static final NamespacedId STYLE = NamespacedId.parse("fpsmatch:style/default");

    @Test
    void snapshotsAreStableSortedAndCarryWorldPose() {
        MarkerSnapshot snapshot = MarkerSnapshot.of(List.of(
                marker(ENEMY, TYPE_PLAYER, 3, 0, 1, 90f, 10, Optional.empty()),
                marker(SELF, TYPE_PLAYER, 1, 0, 0, 0f, 10, Optional.empty())
        ));
        // Deterministic lexicographic sort by markerId string: enemy < self.
        assertEquals(ENEMY, snapshot.markers().get(0).markerId());
        assertEquals(SELF, snapshot.markers().get(1).markerId());
        assertEquals(3.0, snapshot.markers().get(0).x(), 1e-9);
        assertEquals(90f, snapshot.markers().get(0).yaw(), 1e-6);
        assertEquals(1.0, snapshot.markers().get(1).x(), 1e-9);
    }

    @Test
    void alreadyCanonicalSnapshotRetainsItsImmutableList() {
        List<MarkerSnapshot.Marker> canonical = List.of(
                marker(ENEMY, TYPE_PLAYER, 3, 0, 1, 90f, 10, Optional.empty()),
                marker(SELF, TYPE_PLAYER, 1, 0, 0, 0f, 10, Optional.empty())
        );

        MarkerSnapshot snapshot = MarkerSnapshot.of(canonical);

        assertSame(canonical, snapshot.markers());
    }

    @Test
    void visibilityPolicyFiltersBeforeSerializationAndHidesProtectedEnemy() {
        MinimapViewerContext active = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(SELF), true, false
        );
        DefaultTeamVisibilityPolicy policy = new DefaultTeamVisibilityPolicy();
        List<MarkerCandidate> candidates = List.of(
                candidate(SELF, TYPE_PLAYER, "ct", false, false),
                candidate(TEAMMATE, TYPE_PLAYER, "ct", false, false),
                candidate(ENEMY, TYPE_PLAYER, "t", false, false),
                candidate(DEATH, TYPE_DEATH, "ct", true, false)
        );
        List<MarkerSnapshot.Marker> visible = policy.filter(active, candidates);
        assertTrue(visible.stream().anyMatch(m -> m.markerId().equals(SELF)));
        assertTrue(visible.stream().anyMatch(m -> m.markerId().equals(TEAMMATE)));
        assertTrue(visible.stream().anyMatch(m -> m.markerId().equals(DEATH)));
        assertFalse(visible.stream().anyMatch(m -> m.markerId().equals(ENEMY)));
    }

    @Test
    void identityChangeEmitsResetBeforeAnyDelta() {
        RecordingProvider provider = new RecordingProvider();
        MarkerStreamManager manager = new MarkerStreamManager(provider, new DefaultTeamVisibilityPolicy());
        MinimapViewerContext viewer = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(SELF), true, false
        );
        MarkerStreamUpdate first = manager.subscribe(viewer, 0L);
        assertEquals(MarkerStreamUpdate.Kind.RESET, first.kind());
        assertEquals(0L, first.sequence());
        assertFalse(first.markers().isEmpty());

        provider.move(SELF, 5, 0, 5, 45f, 1L);
        MarkerStreamUpdate delta = manager.tick(viewer, 1L);
        assertEquals(MarkerStreamUpdate.Kind.DELTA, delta.kind());
        assertEquals(1L, delta.sequence());
        assertTrue(delta.operations().stream().anyMatch(op -> op instanceof MarkerDelta.Update));

        MinimapViewerContext dead = new MinimapViewerContext(
                ViewerRole.DEAD_TEAM_MEMBER, "ct", Optional.of(SELF), true, false
        );
        MarkerStreamUpdate reset = manager.tick(dead, 2L);
        assertEquals(MarkerStreamUpdate.Kind.RESET, reset.kind());
        assertEquals(0L, reset.sequence());
        assertTrue(reset.streamEpoch() != first.streamEpoch());
    }

    @Test
    void clientStoreRejectsStaleSequenceAndAcceptsResetEpoch() {
        ClientMarkerStore store = new ClientMarkerStore();
        UUID epoch = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        store.applyReset(epoch, 0L, List.of(marker(SELF, TYPE_PLAYER, 0, 0, 0, 0f, 0, Optional.empty())));
        assertEquals(1, store.markers().size());

        assertThrows(MarkerStreamException.class, () ->
                store.applyDelta(epoch, 2L, List.of(new MarkerDelta.Update(
                        marker(SELF, TYPE_PLAYER, 1, 0, 0, 10f, 1, Optional.empty())
                ))));
        store.applyDelta(epoch, 1L, List.of(new MarkerDelta.Update(
                marker(SELF, TYPE_PLAYER, 1, 0, 0, 10f, 1, Optional.empty())
        )));
        assertEquals(1.0, store.markers().get(0).x(), 1e-9);

        UUID next = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        store.applyReset(next, 0L, List.of());
        assertTrue(store.markers().isEmpty());
        assertThrows(MarkerStreamException.class, () ->
                store.applyDelta(epoch, 2L, List.of(new MarkerDelta.Remove(SELF))));
    }

    @Test
    void providerCandidatesNeverBypassVisibilityForHiddenMarkers() {
        MinimapMarkerProvider provider = ctx -> List.of(
                candidate(ENEMY, TYPE_PLAYER, "t", false, false)
        );
        MarkerStreamManager manager = new MarkerStreamManager(provider, new DefaultTeamVisibilityPolicy());
        MinimapViewerContext viewer = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(SELF), true, false
        );
        MarkerStreamUpdate reset = manager.subscribe(viewer, 0L);
        assertTrue(reset.markers().isEmpty());
        // no hidden enemy serialized
        assertFalse(reset.markers().stream().anyMatch(m -> m.markerId().equals(ENEMY)));
    }

    @Test
    void markerSnapshotsPreserveCanonicalTypedStateFields() {
        List<WireMarker.StateField> stateFields = List.of(
                new WireMarker.StateField(
                        NamespacedId.parse("fpsmatch:label"),
                        new WireMarker.StringValue("Bomb carrier")
                ),
                new WireMarker.StateField(
                        NamespacedId.parse("fpsmatch:flashed"),
                        new WireMarker.BoolValue(true)
                )
        );
        MarkerSnapshot.Marker marker = new MarkerSnapshot.Marker(
                SELF, TYPE_PLAYER, STYLE, 1, 2, 3, 45f, 10L,
                Optional.empty(), Optional.of("ground"), stateFields
        );

        MarkerSnapshot snapshot = MarkerSnapshot.of(List.of(marker));

        assertEquals(stateFields, snapshot.markers().get(0).stateFields());
    }

    @Test
    void stateFieldChangesEmitMarkerUpdatesWithoutPoseChanges() {
        MinimapViewerContext viewer = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(SELF), true, false
        );
        MarkerStreamManager manager = new MarkerStreamManager(
                context -> List.of(), (context, candidates) -> List.of()
        );
        MarkerSnapshot.Marker initial = markerWithState(false);
        MarkerSnapshot.Marker changed = markerWithState(true);

        manager.subscribe(viewer, List.of(initial));
        MarkerStreamUpdate update = manager.tick(viewer, List.of(changed));

        assertEquals(MarkerStreamUpdate.Kind.DELTA, update.kind());
        assertEquals(1L, update.sequence());
        MarkerDelta.Update operation = (MarkerDelta.Update) update.operations().get(0);
        assertEquals(changed.stateFields(), operation.marker().stateFields());
    }

    @Test
    void canonicalSnapshotsProduceDeterministicLinearDiffOperations() {
        NamespacedId addedId = NamespacedId.parse("fpsmatch:player/added");
        NamespacedId removedId = NamespacedId.parse("fpsmatch:player/removed");
        MarkerStreamManager manager = new MarkerStreamManager(
                context -> List.of(), (context, candidates) -> List.of()
        );
        MinimapViewerContext viewer = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(SELF), true, false
        );
        MarkerSnapshot.Marker self = marker(
                SELF, TYPE_PLAYER, 1, 0, 1, 0f, 10, Optional.empty()
        );
        MarkerSnapshot.Marker removed = marker(
                removedId, TYPE_PLAYER, 2, 0, 2, 0f, 10, Optional.empty()
        );
        manager.subscribe(viewer, MarkerSnapshot.of(
                List.of(removed, self)
        ).markers());
        MarkerSnapshot.Marker added = marker(
                addedId, TYPE_PLAYER, 3, 0, 3, 0f, 11, Optional.empty()
        );
        MarkerSnapshot.Marker movedSelf = marker(
                SELF, TYPE_PLAYER, 4, 0, 4, 0f, 11, Optional.empty()
        );

        MarkerStreamUpdate update = manager.tick(viewer, MarkerSnapshot.of(
                List.of(movedSelf, added)
        ).markers());

        assertEquals(3, update.operations().size());
        assertEquals(addedId, ((MarkerDelta.Add) update.operations().get(0))
                .marker().markerId());
        assertEquals(removedId, ((MarkerDelta.Remove) update.operations().get(1))
                .markerId());
        assertEquals(SELF, ((MarkerDelta.Update) update.operations().get(2))
                .marker().markerId());
    }

    @Test
    void preCanonicalizedSnapshotsAreReusedByResetAndDeltaPaths() {
        MinimapViewerContext viewer = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(SELF), true, false
        );
        MarkerStreamManager manager = new MarkerStreamManager(
                context -> List.of(), (context, candidates) -> List.of()
        );
        MarkerSnapshot first = MarkerSnapshot.of(List.of(
                marker(SELF, TYPE_PLAYER, 1, 0, 1, 0f, 10, Optional.empty())
        ));
        MarkerSnapshot second = MarkerSnapshot.of(List.of(
                marker(SELF, TYPE_PLAYER, 2, 0, 2, 0f, 11, Optional.empty())
        ));

        MarkerStreamUpdate reset = manager.subscribe(viewer, first);
        MarkerStreamUpdate delta = manager.tick(viewer, second);

        assertSame(first.markers(), reset.markers());
        assertSame(second.markers(), manager.currentVisibleMarkers());
        assertEquals(1, delta.operations().size());
    }

    @Test
    void anyViewerAuthorizationChangeForcesANewStreamEpoch() {
        MarkerStreamManager manager = new MarkerStreamManager(
                context -> List.of(), (context, candidates) -> List.of()
        );
        MinimapViewerContext initial = new MinimapViewerContext(
                ViewerRole.DEAD_TEAM_MEMBER, "ct", Optional.of(SELF), true, false
        );
        MinimapViewerContext restricted = new MinimapViewerContext(
                ViewerRole.DEAD_TEAM_MEMBER, "ct", Optional.of(SELF), false, false
        );
        MarkerStreamUpdate first = manager.subscribe(initial, List.of(markerWithState(false)));

        MarkerStreamUpdate reset = manager.tick(restricted, List.of());

        assertEquals(MarkerStreamUpdate.Kind.RESET, reset.kind());
        assertEquals(0L, reset.sequence());
        assertFalse(first.streamEpoch().equals(reset.streamEpoch()));
    }

    private static MarkerSnapshot.Marker marker(
            NamespacedId id,
            NamespacedId type,
            double x,
            double y,
            double z,
            float yaw,
            long updatedTick,
            Optional<Long> expires
    ) {
        return new MarkerSnapshot.Marker(id, type, STYLE, x, y, z, yaw, updatedTick, expires, Optional.empty());
    }

    private static MarkerSnapshot.Marker markerWithState(boolean flashed) {
        return new MarkerSnapshot.Marker(
                SELF, TYPE_PLAYER, STYLE, 1, 2, 3, 45f, 10L,
                Optional.empty(), Optional.of("ground"),
                List.of(new WireMarker.StateField(
                        NamespacedId.parse("fpsmatch:flashed"),
                        new WireMarker.BoolValue(flashed)
                ))
        );
    }

    private static MarkerCandidate candidate(
            NamespacedId id,
            NamespacedId type,
            String teamId,
            boolean deathEvent,
            boolean publicObjective
    ) {
        return new MarkerCandidate(
                id, type, STYLE, 0, 0, 0, 0f, 0L, Optional.empty(), Optional.empty(),
                teamId, deathEvent, publicObjective
        );
    }

    private static final class RecordingProvider implements MinimapMarkerProvider {
        private double x;
        private double z;
        private float yaw;
        private long tick;

        void move(NamespacedId ignored, double x, double y, double z, float yaw, long tick) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.tick = tick;
        }

        @Override
        public List<MarkerCandidate> collect(MinimapViewerContext context) {
            return List.of(new MarkerCandidate(
                    SELF, TYPE_PLAYER, STYLE, x, 0, z, yaw, tick, Optional.empty(), Optional.empty(),
                    "ct", false, false
            ), new MarkerCandidate(
                    TEAMMATE, TYPE_PLAYER, STYLE, 2, 0, 2, 0f, tick, Optional.empty(), Optional.empty(),
                    "ct", false, false
            ));
        }
    }
}
