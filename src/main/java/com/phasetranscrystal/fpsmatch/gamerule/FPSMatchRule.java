package com.phasetranscrystal.fpsmatch.gamerule;

import net.minecraft.world.level.GameRules;

import static net.minecraft.world.level.GameRules.register;

public class FPSMatchRule {
    public static final GameRules.Key<GameRules.BooleanValue> RULE_THROWABLE_CAN_CROSS_BARRIER = register("throwableCanCrossBarrier", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    public static void init() {
    }
}
