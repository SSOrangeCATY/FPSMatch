package com.phasetranscrystal.fpsmatch.core.objective;

public final class ControlPolicy {
    private final boolean enabled;
    private final boolean pauseWhenContested;
    private final boolean resetWhenEmpty;

    private ControlPolicy(boolean enabled, boolean pauseWhenContested, boolean resetWhenEmpty) {
        this.enabled = enabled;
        this.pauseWhenContested = pauseWhenContested;
        this.resetWhenEmpty = resetWhenEmpty;
    }

    public static ControlPolicy none() {
        return new ControlPolicy(false, false, false);
    }

    public static ControlPolicy teamExclusive() {
        return new ControlPolicy(true, true, false);
    }

    public ControlPolicy resetWhenEmpty() {
        return new ControlPolicy(enabled, pauseWhenContested, true);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean pauseWhenContested() {
        return pauseWhenContested;
    }

    public boolean resetsWhenEmpty() {
        return resetWhenEmpty;
    }
}
