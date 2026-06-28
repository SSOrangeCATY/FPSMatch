package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class VisibilityPolicy {
    private final boolean global;
    private final boolean visibleToSpectators;
    private final Set<UUID> visibleTeams;
    private final Set<UUID> visiblePlayers;

    private VisibilityPolicy(boolean global, boolean visibleToSpectators, Set<UUID> visibleTeams, Set<UUID> visiblePlayers) {
        this.global = global;
        this.visibleToSpectators = visibleToSpectators;
        this.visibleTeams = Collections.unmodifiableSet(new HashSet<>(visibleTeams));
        this.visiblePlayers = Collections.unmodifiableSet(new HashSet<>(visiblePlayers));
    }

    public static VisibilityPolicy global() {
        return new VisibilityPolicy(true, true, Set.of(), Set.of());
    }

    public static VisibilityPolicy teams(Set<UUID> teams) {
        Objects.requireNonNull(teams, "teams");
        return new VisibilityPolicy(false, false, teams, Set.of());
    }

    public static VisibilityPolicy teamsAndSpectators(Set<UUID> teams) {
        Objects.requireNonNull(teams, "teams");
        return new VisibilityPolicy(false, true, teams, Set.of());
    }

    public static VisibilityPolicy players(Set<UUID> players) {
        Objects.requireNonNull(players, "players");
        return new VisibilityPolicy(false, false, Set.of(), players);
    }

    public static VisibilityPolicy playersAndSpectators(Set<UUID> players) {
        Objects.requireNonNull(players, "players");
        return new VisibilityPolicy(false, true, Set.of(), players);
    }

    public boolean canSee(ObjectiveViewer viewer) {
        if (global) {
            return true;
        }
        if (viewer.isSpectator()) {
            return visibleToSpectators;
        }
        if (viewer.playerId().filter(visiblePlayers::contains).isPresent()) {
            return true;
        }
        return viewer.teamId().filter(visibleTeams::contains).isPresent();
    }

    public boolean isGlobal() {
        return global;
    }

    public boolean isVisibleToSpectators() {
        return visibleToSpectators;
    }

    public Set<UUID> visibleTeams() {
        return visibleTeams;
    }

    public Set<UUID> visiblePlayers() {
        return visiblePlayers;
    }
}
