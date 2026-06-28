package com.phasetranscrystal.fpsmatch.core.matchinit;

public final class RosterCapacityPolicy {
    private RosterCapacityPolicy() {
    }

    public static boolean canAccept(int playerLimit, int currentPlayerCount, boolean alreadyReserved) {
        if (alreadyReserved || playerLimit < 0) {
            return true;
        }
        return currentPlayerCount < playerLimit;
    }
}
