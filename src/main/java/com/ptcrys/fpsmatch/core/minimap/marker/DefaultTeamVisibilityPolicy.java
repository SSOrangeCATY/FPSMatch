package com.ptcrys.fpsmatch.core.minimap.marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic team-aware visibility boundary. Protected enemy live markers never pass serialization
 * for active teammates; only public objectives and death events / same-team markers are allowed.
 */
public final class DefaultTeamVisibilityPolicy implements MinimapVisibilityPolicy {
    @Override
    public List<MarkerSnapshot.Marker> filter(MinimapViewerContext context, List<MarkerCandidate> candidates) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(candidates, "candidates");
        List<MarkerSnapshot.Marker> visible = new ArrayList<>();
        for (MarkerCandidate candidate : candidates) {
            if (isVisible(context, candidate)) {
                visible.add(candidate.toMarker());
            }
        }
        return MarkerSnapshot.of(visible).markers();
    }

    private static boolean isVisible(MinimapViewerContext context, MarkerCandidate candidate) {
        if (candidate.publicObjective()) {
            return true;
        }
        boolean sameTeam = context.teamId().equals(candidate.teamId());
        boolean isSelf = context.selfMarkerId().isPresent()
                && context.selfMarkerId().get().equals(candidate.markerId());
        return switch (context.role()) {
            case ACTIVE_PLAYER -> {
                if (isSelf) {
                    yield !candidate.deathEvent();
                }
                if (sameTeam) {
                    yield candidate.deathEvent() || !candidate.deathEvent();
                }
                // enemies only when policy later injects shared intel; default deny live enemies
                yield false;
            }
            case DEAD_TEAM_MEMBER -> {
                if (isSelf) {
                    // dead self has no live marker
                    yield candidate.deathEvent();
                }
                if (sameTeam) {
                    yield true;
                }
                yield context.teamSharedIntelEnabled() && candidate.deathEvent();
            }
            case SPECTATOR_TEAM -> context.observerOmniscient() || candidate.publicObjective() || isSelf;
        };
    }
}