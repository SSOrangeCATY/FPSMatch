package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** One physical lock domain per canonical map path. */
final class RepositorySessionManager implements RepositorySessionProvider {
    private final NativeSessionFacade nativeFacade;
    private final SessionObserver observer;
    private final Map<Path, Domain> domains = new ConcurrentHashMap<>();
    private final AtomicLong nextEpochId = new AtomicLong();
    private final AtomicLong nextLeaseId = new AtomicLong();
    private final AtomicLong nextOperationId = new AtomicLong();

    RepositorySessionManager(
            NativeSessionFacade nativeFacade,
            SessionObserver observer
    ) {
        this.nativeFacade = Objects.requireNonNull(nativeFacade, "nativeFacade");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public SessionLease open(Path mapDirectory) throws IOException {
        Path normalized = normalize(mapDirectory);
        Domain domain = domains.computeIfAbsent(normalized, Domain::new);
        return domain.acquire();
    }

    private final class Domain {
        private final Path mapDirectory;
        private Epoch epoch;
        private IOException poison;

        private Domain(Path mapDirectory) {
            this.mapDirectory = mapDirectory;
        }

        private SessionLease acquire() throws IOException {
            boolean waited = false;
            while (true) {
                Notification notification;
                Epoch acquired = null;
                synchronized (this) {
                    if (poison != null) {
                        throw copyFailure("Repository session domain is poisoned", poison);
                    }
                    if (epoch == null) {
                        Epoch candidate = new Epoch(
                                nextEpochId.incrementAndGet(), Thread.currentThread(),
                                mapDirectory
                        );
                        epoch = candidate;
                        try {
                            openNative(candidate);
                            candidate.state = SessionObservation.EpochState.ACTIVE;
                            notification = note(
                                    SessionObserver.Event.AFTER_SESSION_ACQUIRED,
                                    candidate, 0,
                                    SessionObservation.CompletionOutcome.ACQUIRED
                            );
                            acquired = candidate;
                        } catch (IOException | RuntimeException failure) {
                            closePartial(candidate, failure);
                            epoch = null;
                            notifyAll();
                            throw failure instanceof IOException io
                                    ? io
                                    : new IOException(
                                            "Unable to open repository session", failure
                                    );
                        }
                    } else if (epoch.owner == Thread.currentThread()
                            && epoch.state == SessionObservation.EpochState.ACTIVE) {
                        epoch.referenceCount++;
                        acquired = epoch;
                        notification = note(
                                SessionObserver.Event.AFTER_SESSION_REENTERED,
                                epoch, nextLeaseId.incrementAndGet(),
                                SessionObservation.CompletionOutcome.ACQUIRED
                        );
                    } else {
                        notification = note(
                                SessionObserver.Event.AFTER_SESSION_WAITER_ENQUEUED,
                                epoch, nextLeaseId.incrementAndGet(),
                                SessionObservation.CompletionOutcome.REJECTED
                        );
                    }
                }
                emit(notification);
                if (acquired != null) {
                    if (waited) {
                        emit(note(
                                SessionObserver.Event.AFTER_SESSION_WAITER_COMPLETED,
                                acquired, 0,
                                SessionObservation.CompletionOutcome.ACQUIRED
                        ));
                    }
                    return new Lease(this, acquired);
                }
                waited = true;
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException(
                                "Interrupted while waiting for repository session", interrupted
                        );
                    }
                }
            }
        }

        private void openNative(Epoch candidate) throws IOException {
            candidate.parentHandle = nativeFacade.openMapDirectory(mapDirectory);
            try {
                candidate.lockHandle = nativeFacade.openOrCreateLock(
                        mapDirectory.resolve(".repository.lock")
                );
                try {
                    nativeFacade.lock(candidate.lockHandle);
                    candidate.locked = true;
                } catch (IOException | RuntimeException failure) {
                    closeLockAfterOpenFailure(candidate, failure);
                    throw failure instanceof IOException io
                            ? io
                            : new IOException("Unable to lock repository", failure);
                }
            } catch (IOException | RuntimeException failure) {
                closeParentAfterOpenFailure(candidate, failure);
                throw failure instanceof IOException io
                        ? io
                        : new IOException("Unable to open repository lock", failure);
            }
        }

        private void closePartial(Epoch candidate, Throwable failure) {
            closeLockAfterOpenFailure(candidate, failure);
            closeParentAfterOpenFailure(candidate, failure);
        }

        private void closeLockAfterOpenFailure(Epoch candidate, Throwable failure) {
            if (candidate.lockHandle == null) {
                return;
            }
            try {
                nativeFacade.closeLockHandle(candidate.lockHandle);
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            candidate.lockHandle = null;
        }

        private void closeParentAfterOpenFailure(Epoch candidate, Throwable failure) {
            if (candidate.parentHandle == null) {
                return;
            }
            try {
                nativeFacade.closeParentHandle(candidate.parentHandle);
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            candidate.parentHandle = null;
        }

        private OperationLease openOperation(Epoch expected) throws IOException {
            Notification notification;
            synchronized (this) {
                if (epoch != expected
                        || expected.state != SessionObservation.EpochState.ACTIVE
                        || expected.referenceCount <= 0) {
                    throw new IOException("Repository session is closed");
                }
                expected.admittedOperationCount++;
                notification = note(
                        SessionObserver.Event.AFTER_OPERATION_ADMITTED,
                        expected, nextOperationId.incrementAndGet(),
                        SessionObservation.CompletionOutcome.ACQUIRED
                );
            }
            emit(notification);
            return new Operation(this, expected);
        }

        private void releaseSession(Epoch expected) throws IOException {
            boolean cleanup = false;
            synchronized (this) {
                if (epoch != expected) {
                    return;
                }
                expected.referenceCount--;
                if (expected.referenceCount != 0) {
                    notifyAll();
                    return;
                }
                expected.closeRequested = true;
                while (expected.admittedOperationCount > 0 || expected.cleaning) {
                    waitForCleanup();
                }
                if (epoch != expected) {
                    return;
                }
                expected.cleaning = true;
                expected.state = SessionObservation.EpochState.CLOSING;
                cleanup = true;
            }
            if (cleanup) {
                finishCleanup(expected);
            }
        }

        private void releaseOperation(Epoch expected) throws IOException {
            boolean cleanup = false;
            synchronized (this) {
                if (epoch != expected || expected.admittedOperationCount <= 0) {
                    return;
                }
                expected.admittedOperationCount--;
                if (expected.closeRequested && expected.referenceCount == 0
                        && expected.admittedOperationCount == 0 && !expected.cleaning) {
                    expected.cleaning = true;
                    expected.state = SessionObservation.EpochState.CLOSING;
                    cleanup = true;
                }
                notifyAll();
            }
            if (cleanup) {
                finishCleanup(expected);
            }
        }

        private void waitForCleanup() throws IOException {
            try {
                wait();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "Interrupted while closing repository session", interrupted
                );
            }
        }

        private void finishCleanup(Epoch expected) throws IOException {
            emit(note(
                    SessionObserver.Event.AFTER_HANDLE_CLOSING,
                    expected, 0, SessionObservation.CompletionOutcome.NONE
            ));
            IOException failure = null;
            IOException unlockFailure = null;
            boolean lockCloseFailed = false;
            boolean parentCloseFailed = false;
            if (expected.locked) {
                try {
                    emit(note(
                            SessionObserver.Event.BEFORE_UNLOCK,
                            expected, 0, SessionObservation.CompletionOutcome.NONE
                    ));
                    nativeFacade.unlock(expected.lockHandle);
                    emit(note(
                            SessionObserver.Event.AFTER_UNLOCK,
                            expected, 0, SessionObservation.CompletionOutcome.NONE
                    ));
                } catch (IOException exception) {
                    unlockFailure = exception;
                }
            }
            try {
                emit(note(
                        SessionObserver.Event.BEFORE_LOCK_HANDLE_CLOSE,
                        expected, 0, SessionObservation.CompletionOutcome.NONE
                ));
                nativeFacade.closeLockHandle(expected.lockHandle);
                emit(note(
                        SessionObserver.Event.AFTER_LOCK_HANDLE_CLOSE,
                        expected, 0, SessionObservation.CompletionOutcome.NONE
                ));
            } catch (IOException exception) {
                lockCloseFailed = true;
                failure = combine(unlockFailure, exception);
            }
            try {
                emit(note(
                        SessionObserver.Event.BEFORE_PARENT_HANDLE_CLOSE,
                        expected, 0, SessionObservation.CompletionOutcome.NONE
                ));
                nativeFacade.closeParentHandle(expected.parentHandle);
                emit(note(
                        SessionObserver.Event.AFTER_PARENT_HANDLE_CLOSE,
                        expected, 0, SessionObservation.CompletionOutcome.NONE
                ));
            } catch (IOException exception) {
                parentCloseFailed = true;
                failure = combine(
                        failure == null ? unlockFailure : failure,
                        exception
                );
            }
            synchronized (this) {
                if (lockCloseFailed || parentCloseFailed) {
                    poison = copyFailure(
                            "Repository session cleanup is poisoned", failure
                    );
                    expected.state = SessionObservation.EpochState.POISONED;
                } else {
                    expected.state = SessionObservation.EpochState.CLOSED;
                }
                expected.cleanupFailure = failure;
                expected.cleaning = false;
                epoch = null;
                notifyAll();
            }
            emit(note(
                    expected.state == SessionObservation.EpochState.POISONED
                            ? SessionObserver.Event.AFTER_DOMAIN_POISONED
                            : SessionObserver.Event.AFTER_DOMAIN_CLOSED,
                    expected, 0,
                    failure == null
                            ? SessionObservation.CompletionOutcome.CLOSED
                            : SessionObservation.CompletionOutcome.POISONED
            ));
            if (failure != null) {
                throw failure;
            }
        }

        private Notification note(
                SessionObserver.Event event,
                Epoch current,
                long id,
                SessionObservation.CompletionOutcome outcome
        ) {
            return new Notification(event, new SessionObservation(
                    mapDirectory.toString(), current.epochId, current.owner.getName(), id,
                    current.state,
                    current.state == SessionObservation.EpochState.ACTIVE
                            ? SessionObservation.HandleState.OPEN
                            : current.state == SessionObservation.EpochState.CLOSING
                            ? SessionObservation.HandleState.CLOSING
                            : current.state == SessionObservation.EpochState.POISONED
                            ? SessionObservation.HandleState.POISONED
                            : SessionObservation.HandleState.CLOSED,
                    current.referenceCount, current.admittedOperationCount, outcome
            ));
        }
    }

    private final class Lease implements SessionLease {
        private final Domain domain;
        private final Epoch epoch;
        private boolean closed;
        private IOException closeFailure;

        private Lease(Domain domain, Epoch epoch) {
            this.domain = domain;
            this.epoch = epoch;
        }

        @Override
        public OperationLease openOperation() throws IOException {
            synchronized (domain) {
                if (closed) {
                    throw new IOException("Repository session lease is closed");
                }
            }
            return domain.openOperation(epoch);
        }

        @Override
        public void close() throws IOException {
            synchronized (domain) {
                if (closed) {
                    if (closeFailure != null) {
                        throw closeFailure;
                    }
                    return;
                }
                closed = true;
            }
            try {
                domain.releaseSession(epoch);
                if (epoch.cleanupFailure != null) {
                    closeFailure = epoch.cleanupFailure;
                    throw closeFailure;
                }
            } catch (IOException failure) {
                closeFailure = failure;
                throw failure;
            }
        }
    }

    private final class Operation implements OperationLease {
        private final Domain domain;
        private final Epoch epoch;
        private boolean closed;

        private Operation(Domain domain, Epoch epoch) {
            this.domain = domain;
            this.epoch = epoch;
        }

        @Override
        public void close() throws IOException {
            synchronized (domain) {
                if (closed) {
                    return;
                }
                closed = true;
            }
            domain.releaseOperation(epoch);
        }
    }

    private final class Epoch {
        private final long epochId;
        private final Path mapDirectory;
        private final Thread owner;
        private NativeSessionFacade.Handle parentHandle;
        private NativeSessionFacade.Handle lockHandle;
        private SessionObservation.EpochState state =
                SessionObservation.EpochState.OPENING;
        private int referenceCount = 1;
        private int admittedOperationCount;
        private boolean locked;
        private boolean closeRequested;
        private boolean cleaning;
        private IOException cleanupFailure;

        private Epoch(long epochId, Thread owner, Path mapDirectory) {
            this.epochId = epochId;
            this.mapDirectory = mapDirectory;
            this.owner = owner;
        }
    }

    private record Notification(
            SessionObserver.Event event,
            SessionObservation observation
    ) {
    }

    private void emit(Notification notification) {
        if (notification != null) {
            observer.observe(notification.event(), notification.observation());
        }
    }

    private static IOException combine(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static void closeAfterFailure(
            NativeSessionFacade facade,
            NativeSessionFacade.Handle handle,
            boolean parent,
            Throwable failure
    ) {
        if (handle == null) {
            return;
        }
        try {
            if (parent) {
                facade.closeParentHandle(handle);
            } else {
                facade.closeLockHandle(handle);
            }
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static IOException copyFailure(String message, IOException cause) {
        IOException copy = new IOException(message + ": " + cause.getMessage());
        copy.addSuppressed(cause);
        return copy;
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
