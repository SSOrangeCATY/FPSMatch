package com.phasetranscrystal.fpsmatch.core.spawn;

@FunctionalInterface
public interface SpawnStrategy {
    SpawnPlan createPlan(SpawnRequest request);
}
