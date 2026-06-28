package com.phasetranscrystal.fpsmatch.core.damage;

@FunctionalInterface
public interface DamageRule {
    DamageDecision apply(DamageContext context, DamageDecision currentDecision);
}
