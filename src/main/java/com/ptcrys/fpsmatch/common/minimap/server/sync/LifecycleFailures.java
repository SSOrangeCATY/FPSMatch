package com.ptcrys.fpsmatch.common.minimap.server.sync;

final class LifecycleFailures {
    private LifecycleFailures() {
    }

    static Throwable merge(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    static void runAll(Runnable... steps) {
        Throwable failure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException | Error next) {
                failure = merge(failure, next);
            }
        }
        rethrow(failure);
    }
}
