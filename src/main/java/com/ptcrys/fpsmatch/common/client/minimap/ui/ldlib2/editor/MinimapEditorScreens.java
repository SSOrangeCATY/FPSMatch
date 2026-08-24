package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.ptcrys.fpsmatch.common.client.minimap.editor.ClientEditorBinding;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorResumeCheckpoint;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorResumeCheckpointRegistry;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorStatus;
import com.ptcrys.fpsmatch.common.client.minimap.editor.LocalEditorSessionGateway;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.ptcrys.fpsmatch.common.client.net.FPSMClientPacketRegistrar;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomMinimapIdentity;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandLog;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorEdit;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceCodec;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceSnapshot;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditableLayer;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.RasterSurface;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client entry points for the OP minimap editor UI.
 */
public final class MinimapEditorScreens {
    private static final int DEFAULT_CANVAS = 512;
    private static final int DEFAULT_TILE_EDGE = 128;
    private static volatile LocalEditorSessionGateway activeGateway;
    private static final AtomicLong PRESENTATION_GENERATION = new AtomicLong();

    /** Acceptance-only diagnostics; no identity, hash, or exception message is exposed. */
    public record AcceptanceOpenFailure(Stage stage, String exceptionClass) {
        public enum Stage {
            SERVICES_UNAVAILABLE,
            CHECKPOINT_MISMATCH,
            CHECKPOINT_INVALID,
            OPEN_EMPTY,
            SCREEN_NOT_OWNED,
            OPEN_EXCEPTION
        }

        public AcceptanceOpenFailure {
            Objects.requireNonNull(stage, "stage");
            exceptionClass = boundedExceptionClass(exceptionClass);
        }

        public String token() {
            String value = stage.name().toLowerCase(java.util.Locale.ROOT);
            return exceptionClass == null ? value : value + ":" + exceptionClass;
        }
    }

    private MinimapEditorScreens() {
    }

    public static void open(MapRoomDetail detail) {
        openInternal(detail, Minecraft.getInstance().screen, failure -> {
        }, false);
    }

    public static void open(MapRoomDetail detail, Screen parent) {
        openInternal(detail, parent, failure -> {
        }, false);
    }

    public static Optional<AcceptanceHandle> openAcceptance(MapRoomDetail detail) {
        return openAcceptance(detail, failure -> {
        });
    }

    public static Optional<AcceptanceHandle> openAcceptance(
            MapRoomDetail detail,
            Consumer<AcceptanceOpenFailure> diagnostic
    ) {
        return openInternal(detail, Minecraft.getInstance().screen, diagnostic, true);
    }

    private static Optional<AcceptanceHandle> openInternal(
            MapRoomDetail detail,
            Screen parent,
            Consumer<AcceptanceOpenFailure> diagnostic,
            boolean acceptance
    ) {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(diagnostic, "diagnostic");
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientMinimapServices services = FPSMClientPacketRegistrar.minimapServices();
        if (player == null || services == null) {
            report(diagnostic, new AcceptanceOpenFailure(
                    AcceptanceOpenFailure.Stage.SERVICES_UNAVAILABLE, null
            ));
            return Optional.empty();
        }
        if (!detail.summary().currentPlayerOp()) {
            report(diagnostic, new AcceptanceOpenFailure(
                    AcceptanceOpenFailure.Stage.OPEN_EMPTY, null
            ));
            return Optional.empty();
        }
        MapKey mapKey = new MapKey(detail.summary().gameType(), detail.summary().mapName());
        WireIdentity.DocumentBinding expectedBinding = bindingFor(detail.summary(), mapKey);
        long expectedRevision = detail.summary().minimapIdentity()
                .map(MapRoomMinimapIdentity::revision).orElse(0L);
        Optional<Sha256> expectedSourceHash = detail.summary().minimapIdentity()
                .map(MapRoomMinimapIdentity::sourceHash);
        EditorResumeCheckpointRegistry registry = EditorResumeCheckpointRegistry.global();
        EditorResumeCheckpointRegistry.Lookup lookup = registry.lookup(
                player.getUUID(), expectedBinding, expectedRevision, expectedSourceHash
        );
        if (lookup.state() == EditorResumeCheckpointRegistry.LookupState.MISMATCH) {
            registry.discard(player.getUUID(), mapKey);
            report(diagnostic, new AcceptanceOpenFailure(
                    AcceptanceOpenFailure.Stage.CHECKPOINT_MISMATCH, null
            ));
            return Optional.empty();
        }
        Optional<EditorResumeCheckpoint> checkpoint = lookup.checkpoint();
        EditorSourceSnapshot initialSource;
        try {
            initialSource = checkpoint.map(value -> EditorSourceCodec.decode(value.sourceBytes()))
                    .orElseGet(() -> EditorSourceCodec.createEmpty(
                            mapKey,
                            expectedBinding.target().dimension(),
                            expectedBinding.documentId(),
                            expectedRevision,
                            new CanvasBounds(DEFAULT_CANVAS, DEFAULT_CANVAS),
                            DEFAULT_TILE_EDGE,
                            "ground"
                    ));
        } catch (RuntimeException invalidCheckpoint) {
            registry.discard(player.getUUID(), mapKey);
            report(diagnostic, new AcceptanceOpenFailure(
                    AcceptanceOpenFailure.Stage.CHECKPOINT_INVALID,
                    invalidCheckpoint.getClass().getName()
            ));
            return Optional.empty();
        }

        Consumer<MinimapWireMessage> sender = message -> FPSMatch.sendMinimapToServer(
                UUID.randomUUID(),
                message
        );
        AcceptancePublishHold publishHold = acceptance
                ? new AcceptancePublishHold(sender)
                : null;
        Consumer<MinimapWireMessage> gatewaySender = publishHold == null
                ? sender
                : publishHold;
        LocalEditorSessionGateway gateway = createGateway(
                detail.summary(), player.getUUID(), gatewaySender, UUID::randomUUID, checkpoint
        );
        EditorDocument document = initialSource.document();
        Sha256 initialRoot = checkpoint.map(EditorResumeCheckpoint::draftRootHash)
                .orElse(LocalEditorSessionGateway.emptyHash());
        EditorCommandLog log = EditorCommandLog.empty(initialRoot);
        checkpoint.ifPresent(value -> log.reanchor(initialRoot, value.ackCursor()));
        MinimapEditorController controller = MinimapEditorController.open(
                gateway.sessionId(),
                gateway.draftId(),
                player.getUUID(),
                document,
                log,
                gateway,
                true
        );
        controller.setInitialSource(initialSource, checkpoint.isPresent());

        ClientEditorBinding binding = null;
        Ldlib2MinimapEditorScreen target = null;
        AtomicReference<ClientEditorBinding> acceptanceBinding = new AtomicReference<>();
        AtomicReference<AcceptanceHandle> acceptanceHandle = new AtomicReference<>();
        AcceptanceTeardown acceptanceTeardown = acceptance
                ? new AcceptanceTeardown(
                controller::clearAcceptanceErrorObservation,
                publishHold::cancel,
                () -> registry.discard(player.getUUID(), mapKey),
                () -> gateway.requestClose(WireEditor.CloseMode.DISCARD_DRAFT),
                gateway::clearPublishCompletionListener,
                () -> {
                    ClientEditorBinding owned = acceptanceBinding.getAndSet(null);
                    if (owned != null) {
                        owned.close();
                    }
                },
                controller::close,
                () -> clearActiveGateway(gateway)
        )
                : null;
        try {
            binding = ClientEditorBinding.attach(services, gateway, controller);
            acceptanceBinding.set(binding);
            ClientEditorBinding persistentBinding = binding;
            target = new Ldlib2MinimapEditorScreen(
                    controller, gateway, binding, parent, mapKey,
                    acceptance, acceptanceTeardown,
                    acceptance ? () -> {
                        AcceptanceHandle current = acceptanceHandle.get();
                        return current != null && current.publish();
                    } : null
            );
            AcceptanceHandle handle = new AcceptanceHandle(
                    target, gateway, controller, player.getUUID(), publishHold,
                    acceptanceTeardown
            );
            acceptanceHandle.set(handle);
            binding.setPresentationRestorer(() -> {
                long generation = PRESENTATION_GENERATION.incrementAndGet();
                minecraft.tell(() -> restorePresentation(
                        minecraft, generation, parent, mapKey, gateway, controller,
                        persistentBinding, handle, diagnostic
                ));
            });
            minecraft.setScreen(target);
            if (minecraft.screen != target) {
                report(diagnostic, new AcceptanceOpenFailure(
                        AcceptanceOpenFailure.Stage.SCREEN_NOT_OWNED, null
                ));
                if (acceptanceTeardown != null) {
                    acceptanceTeardown.run();
                } else {
                    closeRejectedOpen(gateway, binding, controller);
                }
                return Optional.empty();
            }
            activeGateway = gateway;
            persistentBinding.presentationAttached();
            if (checkpoint.isPresent()) {
                gateway.requestResume(
                        checkpoint.orElseThrow().draftRootHash(),
                        checkpoint.orElseThrow().ackCursor()
                );
                target.markResumeAttempt();
            } else {
                gateway.requestOpen(WireEditor.OpenMode.OPEN_EXISTING);
            }
            return Optional.of(handle);
        } catch (RuntimeException failure) {
            report(diagnostic, new AcceptanceOpenFailure(
                    AcceptanceOpenFailure.Stage.OPEN_EXCEPTION,
                    failure.getClass().getName()
            ));
            if (acceptanceTeardown != null) {
                acceptanceTeardown.run();
            } else {
                closeRejectedOpen(gateway, binding, controller);
            }
            if (target != null && minecraft.screen == target) {
                target.abandonFailedInstallation();
                minecraft.setScreen(parent);
            }
            throw failure;
        }
    }

    private static void restorePresentation(
            Minecraft minecraft,
            long generation,
            Screen parent,
            MapKey mapKey,
            LocalEditorSessionGateway gateway,
            MinimapEditorController controller,
            ClientEditorBinding persistentBinding,
            AcceptanceHandle handle,
            Consumer<AcceptanceOpenFailure> diagnostic
    ) {
        if (generation != PRESENTATION_GENERATION.get()
                || activeGateway != gateway
                || controller.isClosed()
                || minecraft.screen != parent) {
            return;
        }
        Ldlib2MinimapEditorScreen restored = null;
        try {
            restored = new Ldlib2MinimapEditorScreen(
                    controller, gateway, persistentBinding, parent, mapKey,
                    handle.acceptanceOnly(), handle.acceptanceTeardown(),
                    handle.acceptanceOnly() ? handle::publish : null
            );
            if (generation != PRESENTATION_GENERATION.get()
                    || activeGateway != gateway
                    || controller.isClosed()
                    || minecraft.screen != parent) {
                cleanupFailedPresentation(
                        minecraft, parent, restored, gateway, persistentBinding
                );
                return;
            }
            minecraft.setScreen(restored);
            if (generation != PRESENTATION_GENERATION.get()
                    || activeGateway != gateway
                    || controller.isClosed()) {
                cleanupFailedPresentation(
                        minecraft, parent, restored, gateway, persistentBinding
                );
                return;
            }
            if (minecraft.screen != restored) {
                cleanupFailedPresentation(
                        minecraft, parent, restored, gateway, persistentBinding
                );
                report(diagnostic, new AcceptanceOpenFailure(
                        AcceptanceOpenFailure.Stage.SCREEN_NOT_OWNED, null
                ));
                return;
            }
            persistentBinding.presentationAttached();
            handle.replaceScreen(restored);
        } catch (RuntimeException failure) {
            cleanupFailedPresentation(
                    minecraft, parent, restored, gateway, persistentBinding
            );
            report(diagnostic, new AcceptanceOpenFailure(
                    AcceptanceOpenFailure.Stage.OPEN_EXCEPTION,
                    failure.getClass().getName()
            ));
        }
    }

    private static void cleanupFailedPresentation(
            Minecraft minecraft,
            Screen parent,
            Ldlib2MinimapEditorScreen restored,
            LocalEditorSessionGateway gateway,
            ClientEditorBinding persistentBinding
    ) {
        gateway.clearPublishCompletionListener();
        // Retry only after the next transport-ready transition; never spin on a UI failure.
        persistentBinding.presentationRemovedForTransport();
        if (restored == null) {
            return;
        }
        restored.abandonFailedInstallation();
        if (minecraft.screen == restored) {
            try {
                minecraft.setScreen(parent);
            } catch (RuntimeException ignored) {
                // A failed parent restore must not escape the queued client task.
            }
        }
    }

    private static void report(
            Consumer<AcceptanceOpenFailure> diagnostic,
            AcceptanceOpenFailure failure
    ) {
        try {
            diagnostic.accept(failure);
        } catch (RuntimeException ignored) {
            // Diagnostics must never change the editor open/cleanup outcome.
        }
    }

    private static String boundedExceptionClass(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String candidate = value;
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '.'
                    && character != '_'
                    && character != '$') {
                return "unknown_exception";
            }
        }
        return candidate.length() <= 256 ? candidate : candidate.substring(0, 256);
    }

    public static final class AcceptanceHandle {
        private volatile Ldlib2MinimapEditorScreen screen;
        private final LocalEditorSessionGateway gateway;
        private final MinimapEditorController controller;
        private final UUID actorId;
        private final AcceptancePublishHold publishHold;
        private final AcceptanceTeardown acceptanceTeardown;

        private AcceptanceHandle(
                Ldlib2MinimapEditorScreen screen,
                LocalEditorSessionGateway gateway,
                MinimapEditorController controller,
                UUID actorId,
                AcceptancePublishHold publishHold,
                AcceptanceTeardown acceptanceTeardown
        ) {
            this.screen = Objects.requireNonNull(screen, "screen");
            this.gateway = Objects.requireNonNull(gateway, "gateway");
            this.controller = Objects.requireNonNull(controller, "controller");
            this.actorId = Objects.requireNonNull(actorId, "actorId");
            this.publishHold = publishHold;
            this.acceptanceTeardown = acceptanceTeardown;
        }

        public boolean isCurrent() {
            return ownsCurrentScreen() && gateway.isServerSessionReady();
        }

        public boolean ownsScreen(Screen candidate) {
            Ldlib2MinimapEditorScreen current = screen;
            return candidate == current && current.presents(gateway);
        }

        public Optional<WireIdentity.EditorContext> currentContext() {
            return isCurrent()
                    ? Optional.of(gateway.context())
                    : Optional.empty();
        }

        public Optional<EditorStatus> status() {
            return isCurrent()
                    ? Optional.of(controller.status())
                    : Optional.empty();
        }

        public boolean isDirty() {
            return isCurrent() && controller.isDirty();
        }

        public Optional<LocalEditorSessionGateway.GatewayError> lastError() {
            if (!ownsCurrentScreen()) {
                return Optional.empty();
            }
            if (acceptanceOnly()) {
                Optional<LocalEditorSessionGateway.GatewayError> observed =
                        controller.acceptanceErrorObservation();
                if (observed.isPresent()) {
                    return observed;
                }
            }
            return gateway.lastError();
        }

        public boolean applyLocalEdit(EditorEdit edit) {
            Objects.requireNonNull(edit, "edit");
            if (!isCurrent()) {
                return false;
            }
            controller.applyLocalEdit(edit);
            return true;
        }

        public boolean applyAcceptanceEdit() {
            if (!isCurrent()) {
                return false;
            }
            Optional<EditorEdit> edit = createAcceptanceEdit(controller.document());
            if (edit.isEmpty()) {
                return false;
            }
            controller.applyLocalEdit(edit.orElseThrow());
            return true;
        }

        public boolean requestAcceptanceError() {
            if (!isCurrent()) {
                return false;
            }
            if (!controller.armAcceptanceErrorObservation()) {
                return false;
            }
            try {
                WireIdentity.EditorContext context = gateway.context();
                gateway.rebase(
                        gateway.sessionId(), actorId, context.baseSourceHash(), true
                );
                return true;
            } catch (RuntimeException failure) {
                controller.clearAcceptanceErrorObservation();
                return false;
            }
        }

        public boolean saveDraft() {
            if (!isCurrent()) {
                return false;
            }
            controller.saveDraft();
            return true;
        }

        public boolean publish() {
            if (!isCurrent()) {
                return false;
            }
            WireIdentity.EditorContext expectedContext = gateway.context();
            if (publishHold != null
                    && AcceptancePublishHold.matchesCurrentPublish(
                    expectedContext,
                    publishHold.capturedReserve(),
                    controller.status(),
                    gateway.isPublishInFlight()
            )) {
                return true;
            }
            if (publishHold != null) {
                publishHold.arm(expectedContext);
            }
            try {
                controller.publish();
            } catch (EditorCommandException pendingServerAck) {
                if (publishHold != null) {
                    publishHold.clearAttempt();
                }
                return false;
            } catch (RuntimeException publishFailure) {
                if (publishHold == null) {
                    throw publishFailure;
                }
                publishHold.clearAttempt();
                return false;
            }
            if (publishHold == null) {
                return true;
            }
            boolean matched = AcceptancePublishHold.matchesCurrentPublish(
                    expectedContext,
                    publishHold.capturedReserve(),
                    controller.status(),
                    gateway.isPublishInFlight()
            );
            if (!matched) {
                publishHold.clearAttempt();
            }
            return matched;
        }

        public boolean closeAcceptance() {
            Ldlib2MinimapEditorScreen current = screen;
            boolean owned = ownsCurrentScreen(current);
            boolean closed = current.closeAcceptance();
            if (acceptanceTeardown != null) {
                acceptanceTeardown.run();
            }
            return closed || owned;
        }

        /** Cancels a held acceptance publish when the platform removes the screen. */
        public void cancelAcceptancePublishHold() {
            if (publishHold != null) {
                publishHold.cancel();
            }
        }

        private boolean ownsCurrentScreen() {
            return ownsCurrentScreen(screen);
        }

        private boolean ownsCurrentScreen(Ldlib2MinimapEditorScreen current) {
            return Minecraft.getInstance().screen == current
                    && current.presents(gateway);
        }

        private void replaceScreen(Ldlib2MinimapEditorScreen restored) {
            screen = Objects.requireNonNull(restored, "restored");
        }

        private boolean acceptanceOnly() {
            return acceptanceTeardown != null;
        }

        private AcceptanceTeardown acceptanceTeardown() {
            return acceptanceTeardown;
        }
    }

    /** Idempotent acceptance-only cleanup shared by every presentation/lifecycle exit. */
    static final class AcceptanceTeardown {
        private final List<Runnable> actions;
        private final AtomicBoolean completed = new AtomicBoolean();

        AcceptanceTeardown(Runnable... actions) {
            Objects.requireNonNull(actions, "actions");
            this.actions = List.of(actions.clone());
            if (this.actions.stream().anyMatch(Objects::isNull)) {
                throw new NullPointerException("acceptance teardown action");
            }
        }

        boolean run() {
            if (!completed.compareAndSet(false, true)) {
                return false;
            }
            for (Runnable action : actions) {
                try {
                    action.run();
                } catch (RuntimeException ignored) {
                    // One failed transport/UI cleanup must not skip the remaining actions.
                }
            }
            return true;
        }

        boolean completed() {
            return completed.get();
        }
    }

    private static Optional<EditorEdit> createAcceptanceEdit(
            EditorDocument document
    ) {
        for (String floorId : document.floorIds()) {
            for (String layerId : document.floor(floorId).layerIds()) {
                EditableLayer layer = document.layer(floorId, layerId);
                if (layer.type() != LayerType.RASTER_PAINT
                        || !layer.visible() || layer.locked()) {
                    continue;
                }
                return Optional.of(createAcceptanceTileEdit(
                        document, floorId, layerId
                ));
            }
        }
        return Optional.empty();
    }

    private static EditorEdit createAcceptanceTileEdit(
            EditorDocument document,
            String floorId,
            String layerId
    ) {
        int width = Math.min(document.tileEdge(), document.canvas().width());
        int height = Math.min(document.tileEdge(), document.canvas().height());
        Optional<int[]> existing = document.tilePixelsOptional(
                floorId, layerId, 0, 0
        );
        int[] pixels = existing.map(value -> value.clone()).orElseGet(() -> {
            int[] empty = new int[Math.multiplyExact(width, height)];
            Arrays.fill(empty, RasterSurface.inheritedPixel());
            return empty;
        });
        int firstColor = Rgba8.of(35, 196, 131, 255);
        int secondColor = Rgba8.of(239, 68, 68, 255);
        pixels[0] = pixels[0] == firstColor ? secondColor : firstColor;

        byte[] nextPng = encodeTile(pixels, width, height);
        Sha256 nextHash = Sha256Digest.of(nextPng);
        Optional<Sha256> previousHash = existing.map(value ->
                Sha256Digest.of(encodeTile(value, width, height))
        );
        EditorOperation forward = EditorOperation.putTile(
                floorId, layerId, 0, 0, previousHash, nextHash
        );
        EditorOperation inverse = previousHash.<EditorOperation>map(hash ->
                EditorOperation.putTile(
                        floorId, layerId, 0, 0, Optional.of(nextHash), hash
                )
        ).orElseGet(() -> EditorOperation.deleteTile(
                floorId, layerId, 0, 0, nextHash
        ));
        Map<Sha256, byte[]> payloads = new LinkedHashMap<>();
        payloads.put(nextHash, nextPng);
        if (previousHash.isPresent()) {
            byte[] previousPng = encodeTile(existing.orElseThrow(), width, height);
            payloads.put(previousHash.orElseThrow(), previousPng);
        }
        return new EditorEdit(
                List.of(forward), List.of(inverse), payloads
        );
    }

    private static byte[] encodeTile(int[] pixels, int width, int height) {
        byte[] rgba = new byte[Math.multiplyExact(pixels.length, 4)];
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            if (RasterSurface.isInheritedPixel(pixel)) {
                continue;
            }
            int offset = index * 4;
            rgba[offset] = (byte) Rgba8.red(pixel);
            rgba[offset + 1] = (byte) Rgba8.green(pixel);
            rgba[offset + 2] = (byte) Rgba8.blue(pixel);
            rgba[offset + 3] = (byte) Rgba8.alpha(pixel);
        }
        return CanonicalPngCodecV1.encode(width, height, rgba);
    }

    /** Returns only the context projected by the real, visible, ready editor. */
    public static Optional<WireIdentity.EditorContext> currentContext() {
        LocalEditorSessionGateway gateway = activeGateway;
        Screen screen = Minecraft.getInstance().screen;
        if (gateway == null || !gateway.isServerSessionReady()
                || !(screen instanceof Ldlib2MinimapEditorScreen editor)
                || !editor.presents(gateway)) {
            return Optional.empty();
        }
        return Optional.of(gateway.context());
    }

    static void invalidatePresentationGeneration() {
        PRESENTATION_GENERATION.incrementAndGet();
    }

    static void clearActiveGateway(LocalEditorSessionGateway candidate) {
        if (activeGateway == candidate) {
            activeGateway = null;
        }
    }

    static void rememberDraft(
            UUID actorId,
            WireIdentity.EditorContext context,
            byte[] sourceBytes
    ) {
        EditorResumeCheckpointRegistry.global().remember(actorId, context, sourceBytes);
    }

    /** Builds the editor transport from the server-projected map-room identity. */
    static LocalEditorSessionGateway createGateway(
            MapRoomSummary summary,
            UUID playerId,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds
    ) {
        return createGateway(summary, playerId, sender, requestIds, Optional.empty());
    }

    static LocalEditorSessionGateway createGateway(
            MapRoomSummary summary,
            UUID playerId,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds,
            Optional<EditorResumeCheckpoint> checkpoint
    ) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(requestIds, "requestIds");
        MapKey mapKey = new MapKey(summary.gameType(), summary.mapName());
        Optional<MapRoomMinimapIdentity> identity = summary.minimapIdentity();
        NamespacedId dimension = identity.map(MapRoomMinimapIdentity::dimension)
                .orElseGet(() -> parseDimension(summary.dimension()));
        NamespacedId documentId = identity.map(MapRoomMinimapIdentity::documentId)
                .orElseGet(() -> LocalEditorSessionGateway.documentIdFor(mapKey));
        long revision = checkpoint.map(EditorResumeCheckpoint::baseRevision)
                .orElseGet(() -> identity.map(MapRoomMinimapIdentity::revision).orElse(0L));
        Optional<Sha256> sourceHash = checkpoint.map(value -> Optional.of(value.baseSourceHash()))
                .orElseGet(() -> identity.map(value -> Optional.of(value.sourceHash()))
                        .orElseGet(Optional::empty));
        Optional<Sha256> runtimeHash = identity.map(value -> Optional.of(value.runtimeHash()))
                .orElseGet(Optional::empty);
        UUID sessionId = UUID.randomUUID();
        UUID draftId = checkpoint.map(EditorResumeCheckpoint::draftId).orElseGet(UUID::randomUUID);
        if (sessionId.equals(draftId)) {
            draftId = UUID.randomUUID();
        }
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, System.currentTimeMillis(), 1L);
        return new LocalEditorSessionGateway(
                playerId, mapKey, dimension, documentId, sessionId, draftId, lease,
                revision, sourceHash, runtimeHash, sender, requestIds
        );
    }

    private static WireIdentity.DocumentBinding bindingFor(
            MapRoomSummary summary,
            MapKey mapKey
    ) {
        Optional<MapRoomMinimapIdentity> identity = summary.minimapIdentity();
        NamespacedId dimension = identity.map(MapRoomMinimapIdentity::dimension)
                .orElseGet(() -> parseDimension(summary.dimension()));
        NamespacedId documentId = identity.map(MapRoomMinimapIdentity::documentId)
                .orElseGet(() -> LocalEditorSessionGateway.documentIdFor(mapKey));
        return new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(mapKey, dimension), documentId
        );
    }

    private static void closeRejectedOpen(
            LocalEditorSessionGateway gateway,
            ClientEditorBinding binding,
            MinimapEditorController controller
    ) {
        gateway.requestClose(WireEditor.CloseMode.KEEP_DRAFT);
        gateway.clearPublishCompletionListener();
        if (binding != null) binding.close();
        controller.close();
    }

    private static NamespacedId parseDimension(String raw) {
        try {
            return NamespacedId.parse(raw);
        } catch (RuntimeException ignored) {
            return NamespacedId.parse("minecraft:overworld");
        }
    }
}

/**
 * Acceptance-only transport wrapper. It leaves the real editor lifecycle in
 * PUBLISHING while preventing the visual scene from mutating CURRENT.
 */
final class AcceptancePublishHold implements Consumer<MinimapWireMessage> {
    private final Consumer<MinimapWireMessage> delegate;
    private WireIdentity.EditorContext expectedContext;
    private PublishWireMessage.ReservePublish heldReserve;
    private boolean armed;
    private boolean cancelled;

    AcceptancePublishHold(Consumer<MinimapWireMessage> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public synchronized void accept(MinimapWireMessage message) {
        Objects.requireNonNull(message, "message");
        if (message instanceof PublishWireMessage.ReservePublish reserve) {
            if (cancelled) {
                throw new IllegalStateException("Acceptance publish hold is cancelled");
            }
            if (!armed) {
                throw new IllegalStateException(
                        "Acceptance publish hold must be armed before reserve"
                );
            }
            if (heldReserve != null) {
                throw new IllegalStateException(
                        "Acceptance publish hold permits only one acceptance publish"
                );
            }
            if (!expectedContext.equals(reserve.context())) {
                throw new IllegalStateException(
                        "Acceptance publish context does not match the armed context"
                );
            }
            heldReserve = reserve;
            return;
        }
        // OPEN/RESUME, draft operations, saves, rebases, and status queries remain real.
        delegate.accept(message);
    }

    synchronized Optional<PublishWireMessage.ReservePublish> heldReserve() {
        return capturedReserve();
    }

    synchronized void arm(WireIdentity.EditorContext context) {
        if (cancelled) {
            throw new IllegalStateException("Acceptance publish hold is cancelled");
        }
        expectedContext = Objects.requireNonNull(context, "context");
        heldReserve = null;
        armed = true;
    }

    synchronized Optional<PublishWireMessage.ReservePublish> capturedReserve() {
        return Optional.ofNullable(heldReserve);
    }

    static boolean matchesCurrentPublish(
            WireIdentity.EditorContext expectedContext,
            Optional<PublishWireMessage.ReservePublish> capturedReserve,
            EditorStatus status,
            boolean publishInFlight
    ) {
        Objects.requireNonNull(expectedContext, "expectedContext");
        Objects.requireNonNull(capturedReserve, "capturedReserve");
        Objects.requireNonNull(status, "status");
        return publishInFlight
                && status == EditorStatus.PUBLISHING
                && capturedReserve.filter(reserve ->
                expectedContext.equals(reserve.context())).isPresent();
    }

    synchronized void clearAttempt() {
        expectedContext = null;
        heldReserve = null;
        armed = false;
    }

    synchronized void cancel() {
        cancelled = true;
        clearAttempt();
    }
}
