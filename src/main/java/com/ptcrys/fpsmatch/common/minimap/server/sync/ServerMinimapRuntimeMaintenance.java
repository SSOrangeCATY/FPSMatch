package com.ptcrys.fpsmatch.common.minimap.server.sync;

import java.util.List;

final class ServerMinimapRuntimeMaintenance {
    private final long sweepIntervalTicks;
    private final List<Runnable> sweepSteps;
    private final List<Runnable> closeSteps;
    private long nextSweepTick;
    private boolean closed;

    ServerMinimapRuntimeMaintenance(
            long sweepIntervalTicks,
            List<Runnable> sweepSteps,
            List<Runnable> closeSteps
    ) {
        if (sweepIntervalTicks <= 0L) {
            throw new IllegalArgumentException("Sweep interval must be positive");
        }
        this.sweepIntervalTicks = sweepIntervalTicks;
        this.sweepSteps = List.copyOf(sweepSteps);
        this.closeSteps = List.copyOf(closeSteps);
    }

    synchronized void tick(long nowTick) {
        if (nowTick < 0L) {
            throw new IllegalArgumentException("Maintenance tick must be non-negative");
        }
        if (closed || nowTick < nextSweepTick) {
            return;
        }
        nextSweepTick = nowTick > Long.MAX_VALUE - sweepIntervalTicks
                ? Long.MAX_VALUE : nowTick + sweepIntervalTicks;
        runAll(sweepSteps);
    }

    synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        runAll(closeSteps);
    }

    private static void runAll(List<Runnable> steps) {
        Throwable first = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException | Error failure) {
                if (first == null) {
                    first = failure;
                } else if (first != failure) {
                    first.addSuppressed(failure);
                }
            }
        }
        if (first instanceof RuntimeException failure) {
            throw failure;
        }
        if (first instanceof Error failure) {
            throw failure;
        }
    }
}
