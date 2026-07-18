package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import java.util.Objects;

public final class BlockSampleRule {
    public enum MatchKind {
        BLOCK_ID,
        TAG,
        PROPERTY,
        COLOR,
        TRANSPARENT,
        IGNORE
    }

    private final MatchKind matchKind;
    private final String matchValue;
    private final int argb;
    private final int priority;

    private BlockSampleRule(MatchKind matchKind, String matchValue, int argb, int priority) {
        this.matchKind = Objects.requireNonNull(matchKind, "matchKind");
        this.matchValue = Objects.requireNonNull(matchValue, "matchValue");
        this.argb = argb;
        this.priority = priority;
    }

    public static BlockSampleRule color(String blockId, int argb, int priority) {
        return new BlockSampleRule(MatchKind.COLOR, blockId, argb, priority);
    }

    public static BlockSampleRule transparent(String blockId, int priority) {
        return new BlockSampleRule(MatchKind.TRANSPARENT, blockId, 0, priority);
    }

    public static BlockSampleRule ignoreTag(String tag, int priority) {
        return new BlockSampleRule(MatchKind.IGNORE, tag, 0, priority);
    }

    public MatchKind matchKind() {
        return matchKind;
    }

    public String matchValue() {
        return matchValue;
    }

    public int argb() {
        return argb;
    }

    public int priority() {
        return priority;
    }
}
