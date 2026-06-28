package com.phasetranscrystal.fpsmatch.core.area;

public record AreaTimerPolicy(
        int completionTicks,
        boolean pauseWhenContested,
        boolean resetWhenEmpty
) {
    public AreaTimerPolicy {
        if (completionTicks < 1) {
            throw new IllegalArgumentException("completionTicks must be positive");
        }
    }

    public static AreaTimerPolicy teamCountdown(int completionTicks) {
        return new AreaTimerPolicy(completionTicks, true, true);
    }
}
