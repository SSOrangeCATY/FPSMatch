package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandHasher;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Builds upload and operation messages while the gateway owns authoritative state. */
final class EditorOperationTransport {
    private final Map<UUID, Long> operationRequests;
    private final Map<UUID, EditorPendingUpload> uploads;
    private final Map<UUID, EditorRequestKind> requests;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final Supplier<WireIdentity.EditorContext> context;
    private final BooleanSupplier ready;

    EditorOperationTransport(
            Map<UUID, Long> operationRequests,
            Map<UUID, EditorPendingUpload> uploads,
            Map<UUID, EditorRequestKind> requests,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds,
            Supplier<WireIdentity.EditorContext> context,
            BooleanSupplier ready
    ) {
        this.operationRequests = operationRequests;
        this.uploads = uploads;
        this.requests = requests;
        this.sender = sender;
        this.requestIds = requestIds;
        this.context = context;
        this.ready = ready;
    }

    void dispatchReady(Map<Long, EditorCommand> pendingCommands) {
        if (!ready.getAsBoolean()) return;
        for (EditorCommand command : pendingCommands.values().stream()
                .sorted(Comparator.comparingLong(EditorCommand::sequence)).toList()) {
            if (!operationRequests.containsValue(command.sequence())) dispatch(command);
            if (operationRequests.containsValue(command.sequence())) break;
        }
    }

    void dispatchUpload(EditorPendingUpload pending) {
        for (EditorPendingUpload.Outbound outbound : pending.dispatch(context.get(), requestIds)) {
            sendUpload(outbound);
            if (!ready.getAsBoolean()
                    || uploads.get(pending.beginRequest()) != pending) return;
        }
    }

    void clearUploadRequests(EditorPendingUpload pending) {
        pending.activeRequestIds().forEach(requests::remove);
    }

    void dispatch(EditorCommand command) {
        List<EditorOperation.PutTile> puts = command.edit().forward().stream()
                .filter(EditorOperation.PutTile.class::isInstance)
                .map(EditorOperation.PutTile.class::cast).toList();
        for (EditorOperation operation : command.edit().forward()) {
            if (!(operation instanceof EditorOperation.PutTile)
                    && !(operation instanceof EditorOperation.DeleteTile)) {
                throw new EditorCommandException(
                        "The current editor wire protocol only uploads raster tile operations");
            }
        }
        for (EditorOperation.PutTile put : puts) {
            if (uploads.values().stream().noneMatch(upload ->
                    upload.matches(command.sequence(), put.path()))) {
                byte[] payload = command.edit().payload(put.newHash()).orElseThrow(
                        () -> new EditorCommandException("Missing tile payload for " + put.path()));
                beginUpload(command.sequence(), put, payload);
            }
        }
        if (!ready.getAsBoolean()) return;
        boolean uploadsComplete = uploads.values().stream()
                .filter(upload -> upload.sequence() == command.sequence())
                .allMatch(EditorPendingUpload::complete);
        if (!uploadsComplete || operationRequests.containsValue(command.sequence())) return;
        List<WireEditor.DraftMutation> mutations = mutations(command);
        UUID requestId = requestIds.get();
        operationRequests.put(requestId, command.sequence());
        requests.put(requestId, EditorRequestKind.OPERATION);
        sender.accept(new EditorWireMessage.EditorOperation(
                requestId, context.get(), command.sequence(), command.previousRoot(),
                Sha256Digest.of(EditorCommandHasher.descriptorBytes(command.edit().forward())),
                mutations
        ));
    }

    private List<WireEditor.DraftMutation> mutations(EditorCommand command) {
        List<WireEditor.DraftMutation> result = new ArrayList<>();
        for (EditorOperation operation : command.edit().forward()) {
            if (operation instanceof EditorOperation.PutTile put) {
                EditorPendingUpload upload = uploads.values().stream()
                        .filter(value -> value.matches(command.sequence(), put.path()))
                        .findFirst().orElseThrow();
                result.add(new WireEditor.Put(
                        ContainerPath.parse(put.path()), WireEditor.MediaType.PNG,
                        put.oldHash(), put.newHash(), upload.uploadId()
                ));
            } else {
                EditorOperation.DeleteTile delete = (EditorOperation.DeleteTile) operation;
                result.add(new WireEditor.Delete(
                        ContainerPath.parse(delete.path()), delete.oldHash()
                ));
            }
        }
        return result;
    }

    private void beginUpload(long sequence, EditorOperation.PutTile put, byte[] payload) {
        EditorPendingUpload pending = new EditorPendingUpload(
                requestIds.get(), sequence, ContainerPath.parse(put.path()), payload
        );
        uploads.put(pending.beginRequest(), pending);
        sendUpload(pending.begin(context.get()));
    }

    private void sendUpload(EditorPendingUpload.Outbound outbound) {
        requests.put(outbound.requestId(), outbound.requestKind());
        sender.accept(outbound.message());
    }
}
