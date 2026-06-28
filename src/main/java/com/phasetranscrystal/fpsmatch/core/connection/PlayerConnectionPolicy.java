package com.phasetranscrystal.fpsmatch.core.connection;

public record PlayerConnectionPolicy(int graceTicks) {
    public PlayerConnectionPolicy {
        if (graceTicks < 1) {
            throw new IllegalArgumentException("graceTicks must be positive");
        }
    }

    public static PlayerConnectionPolicy disconnectGrace(int graceTicks) {
        return new PlayerConnectionPolicy(graceTicks);
    }
}
