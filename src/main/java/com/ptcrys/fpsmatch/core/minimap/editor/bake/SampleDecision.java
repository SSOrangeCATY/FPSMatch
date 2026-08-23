package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import java.util.Objects;

public final class SampleDecision {
    public enum Kind {
        COLOR,
        TRANSPARENT,
        IGNORE
    }

    private final Kind kind;
    private final int argb;

    private SampleDecision(Kind kind, int argb) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.argb = argb;
    }

    public static SampleDecision color(int argb) {
        return new SampleDecision(Kind.COLOR, argb);
    }

    public static SampleDecision transparent() {
        return new SampleDecision(Kind.TRANSPARENT, 0);
    }

    public static SampleDecision ignore() {
        return new SampleDecision(Kind.IGNORE, 0);
    }

    public Kind kind() {
        return kind;
    }

    public int argb() {
        return argb;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SampleDecision that)) {
            return false;
        }
        return kind == that.kind && argb == that.argb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, argb);
    }
}
