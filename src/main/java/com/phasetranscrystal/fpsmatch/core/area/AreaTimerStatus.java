package com.phasetranscrystal.fpsmatch.core.area;

public record AreaTimerStatus(AreaTimerState state, int progressTicks, int ticksRemaining) {
    public static AreaTimerStatus idle() {
        return new AreaTimerStatus(AreaTimerState.IDLE, 0, 0);
    }
}
