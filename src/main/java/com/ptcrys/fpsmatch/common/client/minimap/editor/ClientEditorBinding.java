package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapServices;

import java.util.Objects;

/** Persists one editor session across connection-bound dispatcher replacements. */
public final class ClientEditorBinding
        implements ClientMinimapServices.EditorTransportBinding, AutoCloseable {
    private final LocalEditorSessionGateway gateway;
    private final MinimapEditorController controller;
    private ClientMinimapServices.EditorListenerHandle handle =
            ClientMinimapServices.EditorListenerHandle.NONE;
    private Runnable presentationRestorer = () -> { };
    private boolean presentationMissing;
    private boolean closed;

    private ClientEditorBinding(
            ClientMinimapServices services,
            LocalEditorSessionGateway gateway,
            MinimapEditorController controller
    ) {
        Objects.requireNonNull(services, "services");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public static ClientEditorBinding attach(
            ClientMinimapServices services,
            LocalEditorSessionGateway gateway,
            MinimapEditorController controller
    ) {
        ClientEditorBinding binding = new ClientEditorBinding(services, gateway, controller);
        binding.handle = services.attachEditorBinding(binding);
        return binding;
    }

    public synchronized void setPresentationRestorer(Runnable restorer) {
        presentationRestorer = Objects.requireNonNull(restorer, "restorer");
    }

    public synchronized void presentationAttached() {
        presentationMissing = false;
    }

    public synchronized void presentationRemovedForTransport() {
        if (!closed) presentationMissing = true;
    }

    @Override
    public synchronized ClientMinimapServices.EditorListeners listeners(long epoch) {
        return new ClientMinimapServices.EditorListeners(
                message -> at(epoch, () -> gateway.acceptServerSession(message)),
                message -> at(epoch, () -> gateway.acceptEditorAck(message)),
                message -> at(epoch, () -> gateway.acceptSourceManifest(message)),
                message -> at(epoch, () -> gateway.acceptSourceFragment(message)),
                message -> at(epoch, () -> gateway.acceptRebaseResult(message)),
                message -> at(epoch, () -> gateway.acceptError(message)),
                message -> at(epoch, () -> gateway.acceptPublishResult(message)),
                message -> at(epoch, () -> gateway.acceptPublishStatus(message))
        );
    }

    @Override
    public synchronized void ready(long epoch) {
        gateway.transportReady(epoch, controller.isDirty());
        if (presentationMissing && !closed) {
            presentationMissing = false;
            presentationRestorer.run();
        }
    }

    @Override
    public synchronized void detached(long epoch) {
        gateway.transportDown(epoch);
    }

    @Override
    public synchronized void reset() {
        gateway.transportReset();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        handle.close();
        handle = ClientMinimapServices.EditorListenerHandle.NONE;
    }

    private void at(long epoch, Runnable dispatch) {
        gateway.acceptAtTransportEpoch(epoch, dispatch);
    }
}
