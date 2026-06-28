package com.phasetranscrystal.fpsmatch.core.matchinit;

public interface MatchRosterBinder {
    void ensureTeam(MatchRosterPlan.MatchRosterTeam team);

    void reservePlayer(MatchRosterPlan.MatchRosterTeam team, MatchPlayerSeed player);
}
