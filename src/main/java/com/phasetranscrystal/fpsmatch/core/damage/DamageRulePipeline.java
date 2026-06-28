package com.phasetranscrystal.fpsmatch.core.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DamageRulePipeline {
    private final List<DamageRule> rules = new ArrayList<>();

    public static DamageRulePipeline create() {
        return new DamageRulePipeline();
    }

    public DamageRulePipeline add(DamageRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    public DamageDecision evaluate(DamageContext context) {
        Objects.requireNonNull(context, "context");
        DamageDecision decision = DamageDecision.allow(context.amount());
        for (DamageRule rule : rules) {
            decision = rule.apply(context, decision);
            if (decision.cancelled()) {
                return decision;
            }
        }
        return decision;
    }
}
