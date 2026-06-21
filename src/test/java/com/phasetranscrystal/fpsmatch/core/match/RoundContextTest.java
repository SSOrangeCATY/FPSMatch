package com.phasetranscrystal.fpsmatch.core.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundContextTest {
    static class CustomContext implements RoundContext {
        final String value;

        CustomContext(String value) {
            this.value = value;
        }
    }

    @Test
    void ruleReceivesProvidedContext() {
        List<String> captured = new ArrayList<>();
        RoundRuleWithContext<String, String> rule = (lifecycle, ctx) -> {
            captured.add(((CustomContext) ctx).value);
            return Optional.empty();
        };
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(0)
                .roundTicks(2)
                .roundEndTicks(1)
                .addRule(rule)
                .build();

        lifecycle.tick(new CustomContext("first"));
        lifecycle.tick(new CustomContext("second"));

        assertEquals(List.of("first", "second"), captured);
    }

    @Test
    void legacyRoundRuleIgnoresContext() {
        int[] calls = {0};
        RoundRule<String, String> legacy = lifecycle -> {
            calls[0]++;
            return Optional.empty();
        };
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(0)
                .roundTicks(0)
                .roundEndTicks(1)
                .addRule(legacy)
                .build();

        lifecycle.tick();

        assertEquals(1, calls[0]);
    }

    @Test
    void contextCanBeSetAndReadBack() {
        CustomContext ctx = new CustomContext("shared");
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .build();
        lifecycle.setContext(ctx);
        assertEquals(ctx, lifecycle.context());
    }
}
