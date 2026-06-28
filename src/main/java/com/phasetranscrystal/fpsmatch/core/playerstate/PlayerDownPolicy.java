package com.phasetranscrystal.fpsmatch.core.playerstate;

public record PlayerDownPolicy(int maxDowns) {
    public static final int UNLIMITED_DOWNS = -1;

    public PlayerDownPolicy {
        if (maxDowns < UNLIMITED_DOWNS) {
            throw new IllegalArgumentException("maxDowns must be -1 or >= 0");
        }
    }

    public static PlayerDownPolicy unlimited() {
        return new PlayerDownPolicy(UNLIMITED_DOWNS);
    }

    public static PlayerDownPolicy maxDowns(int maxDowns) {
        return new PlayerDownPolicy(maxDowns);
    }

    public boolean shouldEliminateOnDown(int nextDownedCount) {
        return maxDowns != UNLIMITED_DOWNS && nextDownedCount > maxDowns;
    }
}
