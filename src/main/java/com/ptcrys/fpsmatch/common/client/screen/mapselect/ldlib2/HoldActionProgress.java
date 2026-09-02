package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

/** Time-based progress for a hold-to-confirm action, including cancel and completion animations. */
final class HoldActionProgress {

    private enum Phase {
        IDLE,
        HOLDING,
        RETURNING,
        FADING
    }

    private final long holdMillis;
    private final long returnMillis;
    private final long fadeMillis;
    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;
    private float phaseStartedProgress;

    HoldActionProgress(long holdMillis, long returnMillis, long fadeMillis) {
        if (holdMillis <= 0 || returnMillis <= 0 || fadeMillis <= 0) {
            throw new IllegalArgumentException("animation durations must be positive");
        }
        this.holdMillis = holdMillis;
        this.returnMillis = returnMillis;
        this.fadeMillis = fadeMillis;
    }

    void press(long now) {
        update(now);
        if (phase == Phase.HOLDING || phase == Phase.FADING) {
            return;
        }
        phaseStartedProgress = progress(now);
        phaseStartedAt = now;
        phase = Phase.HOLDING;
    }

    void release(long now) {
        update(now);
        if (phase != Phase.HOLDING) {
            return;
        }
        phaseStartedProgress = progress(now);
        phaseStartedAt = now;
        phase = phaseStartedProgress <= 0f ? Phase.IDLE : Phase.RETURNING;
    }

    /** Returns true exactly once when the hold reaches its completion threshold. */
    boolean update(long now) {
        if (phase == Phase.HOLDING && progress(now) >= 1f) {
            phase = Phase.FADING;
            phaseStartedAt = now;
            phaseStartedProgress = 1f;
            return true;
        }
        if (phase == Phase.RETURNING && elapsed(now) >= returnMillis) {
            reset();
        } else if (phase == Phase.FADING && elapsed(now) >= fadeMillis) {
            reset();
        }
        return false;
    }

    float progress(long now) {
        return switch (phase) {
            case IDLE -> 0f;
            case HOLDING -> clamp(phaseStartedProgress + elapsed(now) / (float) holdMillis);
            case RETURNING -> clamp(phaseStartedProgress * (1f - elapsed(now) / (float) returnMillis));
            case FADING -> 1f;
        };
    }

    float opacity(long now) {
        return phase == Phase.FADING ? clamp(1f - elapsed(now) / (float) fadeMillis) : progress(now) > 0f ? 1f : 0f;
    }

    boolean isHolding() {
        return phase == Phase.HOLDING;
    }

    private long elapsed(long now) {
        return Math.max(0L, now - phaseStartedAt);
    }

    private void reset() {
        phase = Phase.IDLE;
        phaseStartedProgress = 0f;
        phaseStartedAt = 0L;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
