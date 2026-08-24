package com.ptcrys.fpsmatch.common.minimap.server;

/**
 * Synchronous, non-wire publication receipt sink.
 *
 * <p>A recovery-capable implementation must durably persist the supplied receipt before
 * returning {@code true}. In particular, acknowledging {@code COMMIT_ATTEMPTED} is the
 * authorization boundary for invoking repository commit.</p>
 */
@FunctionalInterface
public interface PublishAttemptObserver {
    boolean record(PublishAttemptReceipt receipt);

    static PublishAttemptObserver noOp() {
        return NoOpHolder.INSTANCE;
    }

    final class NoOpHolder {
        private static final PublishAttemptObserver INSTANCE = ignored -> true;

        private NoOpHolder() {
        }
    }
}
