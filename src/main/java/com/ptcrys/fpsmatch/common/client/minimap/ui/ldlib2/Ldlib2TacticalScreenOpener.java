package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalDiagnosticSnapshot;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalDiagnosticStage;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalExceptionBoundary;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Ldlib2TacticalScreenOpener
    implements MinimapClientScreens.ScreenOpener {
    private static final Logger LOGGER = LoggerFactory.getLogger("FPSMatch");
    private final Supplier<Ldlib2MinimapHudPresentation> presentations;
    private final AtomicLong screenGeneration = new AtomicLong();
    private final Object stateLock = new Object();
    private volatile Ldlib2TacticalMapScreen ownedScreen;
    private Ldlib2TacticalMapScreen pendingClearTarget;
    // A queued acceptance must terminate exactly once even when close/reopen races it.
    private PendingAcceptance pendingAcceptance;

    public Ldlib2TacticalScreenOpener(
            Supplier<Ldlib2MinimapHudPresentation> presentations
    ) {
        this.presentations = Objects.requireNonNull(
                presentations, "presentations"
        );
    }

    @Override
    public void open(TacticalMapController controller, Runnable onClose) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(onClose, "onClose");
        Minecraft minecraft = Minecraft.getInstance();
        long generation;
        PendingAcceptance cancelled;
        synchronized (stateLock) {
            generation = screenGeneration.incrementAndGet();
            cancelled = pendingAcceptance;
            pendingAcceptance = null;
        }
        publishCancelledAcceptance(cancelled);
        minecraft.tell(() -> openQueued(
                minecraft, generation, controller, onClose
        ));
    }

    @Override
    public void openAcceptance(
            TacticalMapController controller,
            Runnable onClose,
            long attemptSequence,
            Consumer<TacticalDiagnosticSnapshot> diagnostics
    ) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(onClose, "onClose");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Minecraft minecraft = Minecraft.getInstance();
        long generation;
        PendingAcceptance pending;
        PendingAcceptance cancelled;
        synchronized (stateLock) {
            generation = screenGeneration.incrementAndGet();
            cancelled = pendingAcceptance;
            pending = new PendingAcceptance(
                    generation, attemptSequence, diagnostics
            );
            pendingAcceptance = pending;
        }
        publishCancelledAcceptance(cancelled);
        if (minecraft == null) {
            openAcceptanceQueued(
                    null, generation, controller, onClose, pending
            );
        } else {
            minecraft.tell(() -> openAcceptanceQueued(
                    minecraft, generation, controller, onClose, pending
            ));
        }
    }

    private void openQueued(
            Minecraft minecraft,
            long generation,
            TacticalMapController controller,
            Runnable onClose
    ) {
        boolean closeAfter = false;
        Ldlib2TacticalMapScreen target = null;
        try {
            synchronized (stateLock) {
                if (!isCurrentLocked(generation)) {
                    return;
                }
                Ldlib2MinimapHudPresentation presentation = presentations.get();
                if (presentation == null) {
                    closeAfter = true;
                } else {
                    if (!isCurrentLocked(generation)) {
                        return;
                    }
                    target = new Ldlib2TacticalMapScreen(
                            controller, onClose, presentation
                    );
                    if (!isCurrentLocked(generation)) {
                        return;
                    }
                    ownedScreen = target;
                    pendingClearTarget = null;
                    minecraft.setScreen(target);
                    if (!isCurrentLocked(generation)
                            || minecraft.screen != target) {
                        clearOwnedLocked(target);
                        closeAfter = true;
                    }
                }
            }
        } catch (RuntimeException failure) {
            logFailure(failure);
            synchronized (stateLock) {
                clearOwnedLocked(target);
                closeAfter = isCurrentLocked(generation);
            }
        }
        if (closeAfter && isCurrent(generation)) {
            closeTargetSafely(target, onClose);
            if (target != null) {
                queueClear(minecraft, target);
            }
        }
    }

    private void openAcceptanceQueued(
            Minecraft minecraft,
            long generation,
            TacticalMapController controller,
            Runnable onClose,
            PendingAcceptance pending
    ) {
        Ldlib2TacticalMapScreen target = null;
        TacticalDiagnosticStage stage = null;
        RuntimeException operationFailure = null;
        boolean closeAfter = false;
        try {
            synchronized (stateLock) {
                if (!isCurrentLocked(generation)
                        || pendingAcceptance != pending) {
                    return;
                }
                Ldlib2MinimapHudPresentation presentation = presentations.get();
                if (presentation == null) {
                    stage = TacticalDiagnosticStage.PRESENTATION_MISSING;
                    closeAfter = true;
                } else {
                    if (!isCurrentLocked(generation)) {
                        return;
                    }
                    target = new Ldlib2TacticalMapScreen(
                            controller, onClose, presentation
                    );
                    if (!isCurrentLocked(generation)) {
                        return;
                    }
                    ownedScreen = target;
                    pendingClearTarget = null;
                    minecraft.setScreen(target);
                    if (!isCurrentLocked(generation)) {
                        clearOwnedLocked(target);
                        stage = TacticalDiagnosticStage.SET_SCREEN_NOT_RETAINED;
                    } else {
                        stage = classifyPlatformStage(target, minecraft.screen);
                    }
                    if (stage != TacticalDiagnosticStage.SCREEN_OWNED) {
                        clearOwnedLocked(target);
                        closeAfter = true;
                    }
                }
            }
        } catch (RuntimeException failure) {
            operationFailure = failure;
            synchronized (stateLock) {
                clearOwnedLocked(target);
                closeAfter = isCurrentLocked(generation);
            }
            logFailure(failure);
        }

        if (!claimPendingAcceptance(pending, generation)) {
            return;
        }
        if (operationFailure != null) {
            publishAcceptance(pending, TacticalDiagnosticSnapshot.failure(
                    pending.attemptSequence,
                    TacticalExceptionBoundary.OPENER,
                    operationFailure.getClass().getName()
            ));
        } else if (stage != null) {
            TacticalDiagnosticStage resolved = target == null
                    ? stage
                    : classifyPlatformStage(target, minecraft.screen);
            publishAcceptance(
                    pending,
                    TacticalDiagnosticSnapshot.stage(
                            pending.attemptSequence, resolved
                    )
            );
        }
        if (closeAfter && isCurrent(generation)) {
            closeTargetSafely(target, onClose);
            if (target != null) {
                queueClear(minecraft, target);
            }
        }
    }

    private boolean claimPendingAcceptance(
            PendingAcceptance pending,
            long generation
    ) {
        synchronized (stateLock) {
            if (!isCurrentLocked(generation) || pendingAcceptance != pending) {
                return false;
            }
            pendingAcceptance = null;
            return true;
        }
    }

    private static void publishAcceptance(
            PendingAcceptance pending,
            TacticalDiagnosticSnapshot snapshot
    ) {
        if (pending == null || !pending.terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            pending.diagnostics.accept(snapshot);
        } catch (RuntimeException diagnosticFailure) {
            logFailure(diagnosticFailure);
        }
    }

    private static void publishCancelledAcceptance(PendingAcceptance pending) {
        if (pending == null) {
            return;
        }
        publishAcceptance(
                pending,
                TacticalDiagnosticSnapshot.stage(
                        pending.attemptSequence,
                        TacticalDiagnosticStage.SET_SCREEN_NOT_RETAINED
                )
        );
    }

    private boolean isCurrent(long generation) {
        synchronized (stateLock) {
            return isCurrentLocked(generation);
        }
    }

    private boolean isCurrentLocked(long generation) {
        return screenGeneration.get() == generation;
    }

    private void clearOwnedLocked(Ldlib2TacticalMapScreen target) {
        if (target != null && ownedScreen == target) {
            ownedScreen = null;
        }
    }

    private static void closeSafely(Runnable onClose) {
        try {
            onClose.run();
        } catch (RuntimeException cleanupFailure) {
            logFailure(cleanupFailure);
        }
    }

    private static void closeTargetSafely(
            Ldlib2TacticalMapScreen target,
            Runnable onClose
    ) {
        if (target == null) {
            closeSafely(onClose);
            return;
        }
        try {
            // Mark the screen's one-shot lease state before the queued clear invokes removed().
            target.onClose();
        } catch (RuntimeException cleanupFailure) {
            logFailure(cleanupFailure);
        }
    }

    private static void logFailure(RuntimeException failure) {
        LOGGER.error("Tactical screen opener failed", failure);
    }

    private void queueClear(
            Minecraft minecraft,
            Ldlib2TacticalMapScreen target
    ) {
        long generation;
        synchronized (stateLock) {
            pendingClearTarget = target;
            generation = screenGeneration.get();
        }
        queueClear(minecraft, generation, target);
    }

    private void queueClear(
            Minecraft minecraft,
            long generation,
            Ldlib2TacticalMapScreen target
    ) {
        minecraft.tell(() -> clearIfCurrent(minecraft, generation, target));
    }

    private void clearIfCurrent(
            Minecraft minecraft,
            long generation,
            Ldlib2TacticalMapScreen target
    ) {
        synchronized (stateLock) {
            if (isCurrentLocked(generation) && minecraft.screen == target) {
                minecraft.setScreen(null);
                if (pendingClearTarget == target) {
                    pendingClearTarget = null;
                }
            } else if (isCurrentLocked(generation)
                    && pendingClearTarget == target) {
                // A newer screen already replaced this target; do not clear it.
                pendingClearTarget = null;
            }
        }
    }

    static TacticalDiagnosticStage classifyPlatformStage(
            Object target,
            Object currentScreen
    ) {
        return target == currentScreen
                ? TacticalDiagnosticStage.SCREEN_OWNED
                : TacticalDiagnosticStage.SET_SCREEN_NOT_RETAINED;
    }

    static void publishPlatformResult(
            Object target,
            Object currentScreen,
            long attemptSequence,
            Consumer<TacticalDiagnosticSnapshot> diagnostics
    ) {
        diagnostics.accept(TacticalDiagnosticSnapshot.stage(
                attemptSequence, classifyPlatformStage(target, currentScreen)
        ));
    }

    @Override
    public void close() {
        Ldlib2TacticalMapScreen target;
        long generation;
        PendingAcceptance cancelled;
        synchronized (stateLock) {
            generation = screenGeneration.incrementAndGet();
            target = ownedScreen != null ? ownedScreen : pendingClearTarget;
            ownedScreen = null;
            pendingClearTarget = target;
            cancelled = pendingAcceptance;
            pendingAcceptance = null;
        }
        publishCancelledAcceptance(cancelled);
        if (target != null) {
            Minecraft minecraft = Minecraft.getInstance();
            queueClear(minecraft, generation, target);
        }
    }

    @Override
    public boolean owns(Object candidate) {
        return candidate != null && candidate == ownedScreen;
    }

    private static final class PendingAcceptance {
        private final long generation;
        private final long attemptSequence;
        private final Consumer<TacticalDiagnosticSnapshot> diagnostics;
        private final AtomicBoolean terminal = new AtomicBoolean();

        private PendingAcceptance(
                long generation,
                long attemptSequence,
                Consumer<TacticalDiagnosticSnapshot> diagnostics
        ) {
            this.generation = generation;
            this.attemptSequence = attemptSequence;
            this.diagnostics = diagnostics;
        }
    }
}
