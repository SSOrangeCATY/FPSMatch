package com.ptcrys.fpsmatch.common.client.minimap.tactical;

import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapSubscriptionCoordinator;
import com.ptcrys.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Bridge placeholder for platform Screen open/close.
 * Pure controller ownership stays here; concrete Screen construction is client/platform only.
 */
public final class MinimapClientScreens {
    private final TacticalMapController controller;
    private final ClientMinimapSubscriptionCoordinator subscriptions;
    private final ScreenOpener screenOpener;
    private TacticalOpenRequest pendingOpen;
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

    public boolean isOpenPending() {
        return pendingOpen != null;
    }

    public boolean openIfAllowed(TacticalOpenRequest request) {
        Objects.requireNonNull(request, "request");
        if (controller.isOpen()
                || request.passiveOnly()
                || !MinimapKeys.canConsumeOpen(
                request.inGame(), request.capabilityPresent(),
                request.textInputActive(), false
        )) {
            return false;
        }
        if (subscriptions.requestTactical().isEmpty()) {
            return false;
        }
        pendingOpen = request;
        return true;
    }

    public void close() {
        closeFromScreen();
        screenOpener.close();
    }

    public void closeFromScreen() {
        closingFromScreen = true;
        try {
            subscriptions.closeTactical();
            controller.close();
            pendingOpen = null;
        } finally {
            closingFromScreen = false;
        }
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
            controller.openAcknowledged(
                    request,
                    new MinimapScopeLease(
                            lease.scope(), lease.scopeEpoch(), lease.runtimeGeneration()
                    )
            );
            if (controller.isOpen()) {
                screenOpener.open(controller, MinimapClientScreens.this::closeFromScreen);
            }
            pendingOpen = null;
        }

        @Override
        public void rejected(WireStatus.ErrorInfo error) {
            pendingOpen = null;
        }

        @Override
        public void closed() {
            controller.close();
            pendingOpen = null;
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
    }
}
