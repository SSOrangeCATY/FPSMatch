package com.phasetranscrystal.fpsmatch.core.damage;

public record DamageDecision(boolean cancelled, float amount) {
    public static DamageDecision allow(float amount) {
        return new DamageDecision(false, amount);
    }

    public DamageDecision cancel() {
        return new DamageDecision(true, 0.0f);
    }

    public DamageDecision scale(float multiplier) {
        if (cancelled) {
            return this;
        }
        return new DamageDecision(false, amount * multiplier);
    }
}
