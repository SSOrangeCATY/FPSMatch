package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;


import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SamplerProfile {
    private final String id;
    private final long seed;
    private final List<BlockSampleRule> rules;

    public SamplerProfile(String id, long seed, List<BlockSampleRule> rules) {
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Sampler profile id is invalid");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.seed = seed;
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Sampler profile requires at least one rule");
        }
        Set<Integer> priorities = new HashSet<>();
        for (BlockSampleRule rule : rules) {
            if (!priorities.add(rule.priority())) {
                throw new IllegalArgumentException("Duplicate sampler rule priority: " + rule.priority());
            }
        }
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(BlockSampleRule::priority))
                .toList();
    }

    public String id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    public List<BlockSampleRule> rules() {
        return rules;
    }

    public SampleDecision sample(String blockId, List<String> tags, String property) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(tags, "tags");
        for (BlockSampleRule rule : rules) {
            switch (rule.matchKind()) {
                case IGNORE -> {
                    if (tags.contains(rule.matchValue())) {
                        return SampleDecision.ignore();
                    }
                }
                case TRANSPARENT -> {
                    if (blockId.equals(rule.matchValue())) {
                        return SampleDecision.transparent();
                    }
                }
                case COLOR -> {
                    if (blockId.equals(rule.matchValue())) {
                        return SampleDecision.color(rule.argb());
                    }
                }
                case BLOCK_ID, TAG, PROPERTY -> {
                    // reserved for future property-aware rules
                }
            }
        }
        return SampleDecision.ignore();
    }
}
