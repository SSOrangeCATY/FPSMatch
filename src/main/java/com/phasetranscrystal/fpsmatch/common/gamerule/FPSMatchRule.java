package com.phasetranscrystal.fpsmatch.common.gamerule;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class FPSMatchRule {
    private static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, FPSMatch.MODID);

    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> THROWABLE_CAN_CROSS_BARRIER = registerBoolean("throwable_can_cross_barrier", true);
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> AUTO_SORT_PLAYER_INV = registerBoolean("auto_sort_player_inv", true);

    public static GameRule<Boolean> RULE_THROWABLE_CAN_CROSS_BARRIER;
    public static GameRule<Boolean> RULE_AUTO_SORT_PLAYER_INV;

    public static void register(IEventBus modEventBus) {
        GAME_RULES.register(modEventBus);
        modEventBus.addListener(FPSMatchRule::onGameRulesRegistered);
    }

    private static DeferredHolder<GameRule<?>, GameRule<Boolean>> registerBoolean(String id, boolean defaultValue) {
        return GAME_RULES.register(id, () -> new GameRule<>(
                GameRuleCategory.MISC,
                GameRuleType.BOOL,
                BoolArgumentType.bool(),
                (visitor, rule) -> visitor.visitBoolean(rule),
                Codec.BOOL,
                b -> b ? 1 : 0,
                defaultValue,
                FeatureFlagSet.of()
        ));
    }

    private static void onGameRulesRegistered(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.GAME_RULE)) {
            return;
        }
        RULE_THROWABLE_CAN_CROSS_BARRIER = THROWABLE_CAN_CROSS_BARRIER.get();
        RULE_AUTO_SORT_PLAYER_INV = AUTO_SORT_PLAYER_INV.get();
    }
}
