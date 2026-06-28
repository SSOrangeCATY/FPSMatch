package com.phasetranscrystal.fpsmatch.core.map;

public final class MatchLifecycleState {
    private boolean started;

    public boolean acceptStart(boolean eventAccepted) {
        return acceptStart(eventAccepted, true);
    }

    public boolean acceptStart(boolean eventAccepted, boolean prepared) {
        if (started) {
            return true;
        }
        if (!eventAccepted || !prepared) {
            return false;
        }
        started = true;
        return true;
    }

    public boolean isStarted() {
        return started;
    }

    public void reset() {
        started = false;
    }
}
