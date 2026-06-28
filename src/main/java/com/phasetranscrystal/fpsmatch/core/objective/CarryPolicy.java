package com.phasetranscrystal.fpsmatch.core.objective;

public final class CarryPolicy {
    private final boolean enabled;
    private final boolean singleHolder;
    private final boolean dropOnDown;

    private CarryPolicy(boolean enabled, boolean singleHolder, boolean dropOnDown) {
        this.enabled = enabled;
        this.singleHolder = singleHolder;
        this.dropOnDown = dropOnDown;
    }

    public static CarryPolicy none() {
        return new CarryPolicy(false, false, false);
    }

    public static CarryPolicy singleHolderDropsOnDown() {
        return new CarryPolicy(true, true, true);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean singleHolder() {
        return singleHolder;
    }

    public boolean dropOnDown() {
        return dropOnDown;
    }
}
