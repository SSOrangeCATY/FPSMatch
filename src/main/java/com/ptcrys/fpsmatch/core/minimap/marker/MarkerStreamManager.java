package com.ptcrys.fpsmatch.core.minimap.marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side marker stream. Visibility is always applied before serialization.
 * Identity/team/map role changes force RESET(newEpoch, sequence=0) before any delta.
 */
public final class MarkerStreamManager {
    private final MinimapMarkerProvider provider;
    private final MinimapVisibilityPolicy policy;

    private UUID streamEpoch = UUID.randomUUID();
    private long sequence;
    private MinimapViewerContext lastContext;
    private MarkerSnapshot lastVisible = MarkerSnapshot.of(List.of());

    public MarkerStreamManager(MinimapMarkerProvider provider, MinimapVisibilityPolicy policy) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public MarkerStreamUpdate subscribe(MinimapViewerContext context, long nowTick) {
        Objects.requireNonNull(context, "context");
        return subscribe(context, visible(context));
    }

    public MarkerStreamUpdate subscribe(
            MinimapViewerContext context,
            List<MarkerSnapshot.Marker> visible
    ) {
        Objects.requireNonNull(context, "context");
        return subscribe(context, MarkerSnapshot.of(visible));
    }

    public MarkerStreamUpdate subscribe(
            MinimapViewerContext context,
            MarkerSnapshot visible
    ) {
        Objects.requireNonNull(context, "context");
        return forceReset(context, visible);
    }

    public MarkerStreamUpdate tick(MinimapViewerContext context, long nowTick) {
        Objects.requireNonNull(context, "context");
        if (!context.equals(lastContext)) {
            return forceReset(context, MarkerSnapshot.of(visible(context)));
        }
        return tick(context, visible(context));
    }

    public MarkerStreamUpdate tick(
            MinimapViewerContext context,
            List<MarkerSnapshot.Marker> visible
    ) {
        Objects.requireNonNull(context, "context");
        return tick(context, MarkerSnapshot.of(visible));
    }

    public MarkerStreamUpdate tick(
            MinimapViewerContext context,
            MarkerSnapshot visible
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(visible, "visible");
        if (!context.equals(lastContext)) {
            return forceReset(context, visible);
        }
        List<MarkerSnapshot.Marker> next = visible.markers();
        List<MarkerDelta> operations = new ArrayList<>();
        List<MarkerSnapshot.Marker> previous = lastVisible.markers();
        int previousIndex = 0;
        int nextIndex = 0;
        while (previousIndex < previous.size() && nextIndex < next.size()) {
            MarkerSnapshot.Marker previousMarker = previous.get(previousIndex);
            MarkerSnapshot.Marker current = next.get(nextIndex);
            int order = MarkerSnapshot.compareIds(
                    previousMarker.markerId(), current.markerId()
            );
            if (order < 0) {
                operations.add(new MarkerDelta.Remove(previousMarker.markerId()));
                previousIndex++;
            } else if (order > 0) {
                operations.add(new MarkerDelta.Add(current));
                nextIndex++;
            } else {
                if (!samePose(previousMarker, current)) {
                    operations.add(new MarkerDelta.Update(current));
                }
                previousIndex++;
                nextIndex++;
            }
        }
        while (previousIndex < previous.size()) {
            operations.add(new MarkerDelta.Remove(
                    previous.get(previousIndex++).markerId()
            ));
        }
        while (nextIndex < next.size()) {
            operations.add(new MarkerDelta.Add(next.get(nextIndex++)));
        }
        lastVisible = visible;
        if (operations.isEmpty()) {
            return MarkerStreamUpdate.delta(streamEpoch, sequence, List.of());
        }
        sequence += 1L;
        return MarkerStreamUpdate.delta(streamEpoch, sequence, operations);
    }

    private MarkerStreamUpdate forceReset(
            MinimapViewerContext context,
            MarkerSnapshot visible
    ) {
        streamEpoch = UUID.randomUUID();
        sequence = 0L;
        lastContext = context;
        lastVisible = Objects.requireNonNull(visible, "visible");
        return MarkerStreamUpdate.reset(streamEpoch, lastVisible.markers());
    }

    List<MarkerSnapshot.Marker> currentVisibleMarkers() {
        return lastVisible.markers();
    }

    private List<MarkerSnapshot.Marker> visible(MinimapViewerContext context) {
        return policy.filter(context, provider.collect(context));
    }

    private static boolean samePose(MarkerSnapshot.Marker left, MarkerSnapshot.Marker right) {
        return Double.compare(left.x(), right.x()) == 0
                && Double.compare(left.y(), right.y()) == 0
                && Double.compare(left.z(), right.z()) == 0
                && Float.compare(left.yaw(), right.yaw()) == 0
                && left.updatedTick() == right.updatedTick()
                && left.expiresTick().equals(right.expiresTick())
                && left.floorSlug().equals(right.floorSlug())
                && left.typeId().equals(right.typeId())
                && left.styleId().equals(right.styleId())
                && left.stateFields().equals(right.stateFields());
    }
}
