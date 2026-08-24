package com.ptcrys.fpsmatch.common.client.minimap.tactical;

import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapSubscriptionCoordinator;
import com.ptcrys.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge placeholder for platform Screen open/close.
 * Pure controller ownership stays here; concrete Screen construction is client/platform only.
 */
public final class MinimapClientScreens {
    private static final Logger LOGGER = LoggerFactory.getLogger("FPSMatch");
    private final TacticalMapController controller;
    private final ClientMinimapSubscriptionCoordinator subscriptions;
    private final ScreenOpener screenOpener;
    private TacticalOpenRequest pendingOpen;
    private long pendingAttemptSequence;
    private Consumer<TacticalDiagnosticSnapshot> pendingDiagnostics;
    private boolean closingFromScreen;

    public MinimapClientScreens(
            TacticalMapController controller,
            ClientMinimapSubscriptionCoordinator subscriptions
    ) {
        this(controller, subscriptions, (ignored, close) -> {
        });
    }

    public MinimapClientScreens(
            TacticalMapController controller,
            ClientMinimapSubscriptionCoordinator subscriptions,
            ScreenOpener screenOpener
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.subscriptions = subscriptions;
        this.screenOpener = Objects.requireNonNull(screenOpener, "screenOpener");
        if (subscriptions != null) {
            subscriptions.setTacticalListener(new TacticalEvents());
        }
    }

    public TacticalMapController controller() {
        return controller;
    }

    public boolean ownsScreen(Object candidate) {
        return screenOpener.owns(candidate);
    }

    public boolean isOpenPending() {
        return pendingOpen != null;
    }

    /**
     * Toggle from a key edge. The request is lazy so an active screen can close
     * even when the world/capability guards for opening are no longer valid.
     */
    public boolean toggle(Supplier<TacticalOpenRequest> requestSupplier) {
        Objects.requireNonNull(requestSupplier, "requestSupplier");
        if (controller.isOpen() || pendingOpen != null) {
            close();
            return true;
        }
        return openIfAllowed(Objects.requireNonNull(
                requestSupplier.get(), "open request"
        ));
    }

    public boolean openIfAllowed(TacticalOpenRequest request) {
        Objects.requireNonNull(request, "request");
        if (!canOpen(request)) {
            return false;
        }
        if (subscriptions.requestTactical().isEmpty()) {
            return false;
        }
        pendingOpen = request;
        return true;
    }

    public boolean openAcceptance(
            TacticalOpenRequest request,
            long attemptSequence,
            Consumer<TacticalDiagnosticSnapshot> diagnostics
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (!canOpen(request)) {
            diagnostics.accept(TacticalDiagnosticSnapshot.stage(
                    attemptSequence, TacticalDiagnosticStage.REQUEST_REJECTED
            ));
            return false;
        }

        pendingAttemptSequence = attemptSequence;
        pendingDiagnostics = diagnostics;
        try {
            if (subscriptions.requestTactical(true).isEmpty()) {
                publishStage(TacticalDiagnosticStage.REQUEST_REJECTED);
                clearPendingDiagnostic();
                return false;
            }
        } catch (RuntimeException failure) {
            failAndCleanup(failure, TacticalExceptionBoundary.REQUEST);
            throw failure;
        }
        pendingOpen = request;
        publishStage(TacticalDiagnosticStage.ACK_PENDING);
        return true;
    }

    public void close() {
        boolean hadSession = controller.isOpen() || pendingOpen != null;
        RuntimeException failure = null;
        try {
            closeFromScreen();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        if (hadSession) {
            try {
                screenOpener.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void closeFromScreen() {
        closingFromScreen = true;
        try {
            RuntimeException failure = null;
            try {
                subscriptions.closeTactical();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                controller.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
            pendingOpen = null;
            clearPendingDiagnostic();
            if (failure != null) {
                throw failure;
            }
        } finally {
            closingFromScreen = false;
        }
    }

    private boolean canOpen(TacticalOpenRequest request) {
        return !controller.isOpen()
                && !request.passiveOnly()
                && MinimapKeys.canConsumeOpen(
                request.inGame(), request.capabilityPresent(),
                request.textInputActive(), false
        );
    }

    private void publishStage(TacticalDiagnosticStage stage) {
        Consumer<TacticalDiagnosticSnapshot> diagnostics = pendingDiagnostics;
        if (diagnostics != null) {
            diagnostics.accept(TacticalDiagnosticSnapshot.stage(
                    pendingAttemptSequence, stage
            ));
        }
    }

    private void publishFailure(
            TacticalExceptionBoundary boundary,
            RuntimeException failure
    ) {
        // Keep the structured diagnostic compact while preserving the full client-side cause chain in logs.
        LOGGER.error("Tactical screen lifecycle failed at {}", boundary, failure);
        Consumer<TacticalDiagnosticSnapshot> diagnostics = pendingDiagnostics;
        if (diagnostics != null) {
            diagnostics.accept(TacticalDiagnosticSnapshot.failure(
                    pendingAttemptSequence,
                    boundary,
                    failure.getClass().getName()
            ));
        }
    }

    private void failAndCleanup(
            RuntimeException failure,
            TacticalExceptionBoundary boundary
    ) {
        publishFailure(boundary, failure);
        try {
            close();
        } catch (RuntimeException cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void clearPendingDiagnostic() {
        pendingAttemptSequence = 0L;
        pendingDiagnostics = null;
    }

    private final class TacticalEvents
            implements ClientMinimapSubscriptionCoordinator.TacticalListener {
        @Override
        public void activated(
                WireIdentity.ScopeLease lease,
                WireIdentity.RuntimeIdentity runtime
        ) {
            TacticalOpenRequest request = pendingOpen;
            if (request == null) {
                return;
            }
            try {
                publishStage(TacticalDiagnosticStage.ACK_ACTIVATED);
                final boolean controllerOpened;
                try {
                    controllerOpened = controller.openAcknowledged(
                            request,
                            new MinimapScopeLease(
                                    lease.scope(), lease.scopeEpoch(),
                                    lease.runtimeGeneration()
                            )
                    );
                } catch (RuntimeException failure) {
                    failAndCleanup(failure, TacticalExceptionBoundary.CONTROLLER);
                    throw failure;
                }
                if (!controllerOpened) {
                    publishStage(TacticalDiagnosticStage.CONTROLLER_REJECTED);
                    // Acceptance diagnostics fail closed; ordinary M-key rejection retains its subscription.
                    if (pendingDiagnostics != null) {
                        MinimapClientScreens.this.close();
                    }
                    return;
                }
                try {
                    if (pendingDiagnostics == null) {
                        screenOpener.open(
                                controller,
                                MinimapClientScreens.this::closeFromScreen
                        );
                    } else {
                        screenOpener.openAcceptance(
                                controller,
                                MinimapClientScreens.this::closeFromScreen,
                                pendingAttemptSequence,
                                pendingDiagnostics
                        );
                    }
                } catch (RuntimeException failure) {
                    failAndCleanup(failure, TacticalExceptionBoundary.OPENER);
                    throw failure;
                }
            } finally {
                pendingOpen = null;
                clearPendingDiagnostic();
            }
        }

        @Override
        public void rejected(WireStatus.ErrorInfo error) {
            publishStage(TacticalDiagnosticStage.SUBSCRIPTION_REJECTED);
            pendingOpen = null;
            clearPendingDiagnostic();
        }

        @Override
        public void closed() {
            controller.close();
            pendingOpen = null;
            clearPendingDiagnostic();
            if (!closingFromScreen) {
                screenOpener.close();
            }
        }
    }

    public interface ScreenOpener extends BiConsumer<
            TacticalMapController, Runnable> {
        void open(TacticalMapController controller, Runnable onClose);

        @Override
        default void accept(TacticalMapController controller, Runnable onClose) {
            open(controller, onClose);
        }

        default void close() {
        }

        default void openAcceptance(
                TacticalMapController controller,
                Runnable onClose,
                long attemptSequence,
                Consumer<TacticalDiagnosticSnapshot> diagnostics
        ) {
            open(controller, onClose);
        }

        default boolean owns(Object candidate) {
            return false;
        }
    }

    public enum TacticalDiagnosticStage {
        ACK_PENDING(false),
        ACK_ACTIVATED(false),
        REQUEST_REJECTED(true),
        SUBSCRIPTION_REJECTED(true),
        CONTROLLER_REJECTED(true),
        PRESENTATION_MISSING(true),
        SET_SCREEN_NOT_RETAINED(true),
        SCREEN_OWNED(true),
        RUNTIME_EXCEPTION(true);

        private final boolean terminal;

        TacticalDiagnosticStage(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean terminal() {
            return terminal;
        }
    }

    public enum TacticalExceptionBoundary {
        REQUEST,
        CONTROLLER,
        OPENER,
        PROJECTOR
    }

    public record TacticalDiagnosticSnapshot(
            long attemptSequence,
            TacticalDiagnosticStage stage,
            TacticalExceptionBoundary exceptionBoundary,
            String exceptionClass
    ) {
        public TacticalDiagnosticSnapshot {
            Objects.requireNonNull(stage, "stage");
            if (stage == TacticalDiagnosticStage.RUNTIME_EXCEPTION) {
                Objects.requireNonNull(exceptionBoundary, "exceptionBoundary");
                Objects.requireNonNull(exceptionClass, "exceptionClass");
                if (exceptionClass.isEmpty()) {
                    throw new IllegalArgumentException("exceptionClass is empty");
                }
            } else if (exceptionBoundary != null || exceptionClass != null) {
                throw new IllegalArgumentException(
                        "non-exception stage cannot carry exception metadata"
                );
            }
        }

        public static TacticalDiagnosticSnapshot stage(
                long attemptSequence,
                TacticalDiagnosticStage stage
        ) {
            Objects.requireNonNull(stage, "stage");
            if (stage == TacticalDiagnosticStage.RUNTIME_EXCEPTION) {
                throw new IllegalArgumentException(
                        "runtime exception requires boundary and class"
                );
            }
            return new TacticalDiagnosticSnapshot(
                    attemptSequence, stage, null, null
            );
        }

        public static TacticalDiagnosticSnapshot failure(
                long attemptSequence,
                TacticalExceptionBoundary exceptionBoundary,
                String exceptionClass
        ) {
            return new TacticalDiagnosticSnapshot(
                    attemptSequence,
                    TacticalDiagnosticStage.RUNTIME_EXCEPTION,
                    exceptionBoundary,
                    exceptionClass
            );
        }

        public String exceptionClassName() {
            return exceptionClass;
        }
    }
}
