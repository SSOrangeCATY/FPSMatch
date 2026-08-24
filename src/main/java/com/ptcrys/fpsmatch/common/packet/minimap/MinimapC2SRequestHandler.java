package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.SnapshotWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Routes decoded C2S messages while enforcing the editor authorization boundary. */
public final class MinimapC2SRequestHandler {
    private static final int FIRST_EDITOR_OPCODE = 0x10;
    private static final int LAST_EDITOR_OPCODE = 0x3f;

    private final MinimapRuntimeRequestGate runtimeGate;
    private final MinimapEditorRequestGate editorGate;
    private final MinimapC2SDispatcher dispatcher;
    private final BiConsumer<UUID, MinimapWireMessage> errorSender;

    public MinimapC2SRequestHandler(
            MinimapEditorRequestGate editorGate,
            MinimapC2SDispatcher dispatcher
    ) {
        this((actorId, message) -> true, editorGate, dispatcher, (actorId, message) -> {
        });
    }

    public MinimapC2SRequestHandler(
            MinimapRuntimeRequestGate runtimeGate,
            MinimapEditorRequestGate editorGate,
            MinimapC2SDispatcher dispatcher
    ) {
        this(runtimeGate, editorGate, dispatcher, (actorId, message) -> {
        });
    }

    public MinimapC2SRequestHandler(
            MinimapRuntimeRequestGate runtimeGate,
            MinimapEditorRequestGate editorGate,
            MinimapC2SDispatcher dispatcher,
            BiConsumer<UUID, MinimapWireMessage> errorSender
    ) {
        this.runtimeGate = Objects.requireNonNull(runtimeGate, "runtimeGate");
        this.editorGate = Objects.requireNonNull(editorGate, "editorGate");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.errorSender = Objects.requireNonNull(errorSender, "errorSender");
    }

    public static MinimapC2SRequestHandler disabled() {
        return new MinimapC2SRequestHandler(
                (actorId, message) -> false,
                (actorId, message) -> false,
                (actorId, message) -> {
                }
        );
    }

    public boolean handle(UUID actorId, MinimapWireMessage message) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(message, "message");
        MinimapOpcode opcode = message.opcode();
        if (opcode.direction() != MinimapMessageDirection.C2S) {
            return false;
        }
        if (isEditorOpcode(opcode)) {
            if (!revalidateEditorRequest(actorId, message)) {
                return false;
            }
        } else if (!allowRuntimeRequest(actorId, message)) {
            return false;
        }
        dispatcher.dispatch(actorId, message);
        return true;
    }

    private boolean revalidateEditorRequest(UUID actorId, MinimapWireMessage message) {
        try {
            if (editorGate.revalidatePermissionAndScope(actorId, message)) {
                return true;
            }
            sendEditorGateError(
                    actorId, message, MinimapErrorCode.UNAUTHORIZED,
                    WireStatus.RetryDisposition.DO_NOT_RETRY,
                    "Editor request is not authorized"
            );
            return false;
        } catch (RuntimeException unavailableOrRejected) {
            sendEditorGateError(
                    actorId, message, MinimapErrorCode.MAP_UNAVAILABLE,
                    WireStatus.RetryDisposition.RETRY_NEW_REQUEST,
                    "Editor authorization is unavailable"
            );
            return false;
        }
    }

    private void sendEditorGateError(
            UUID actorId,
            MinimapWireMessage message,
            MinimapErrorCode code,
            WireStatus.RetryDisposition retry,
            String detail
    ) {
        Optional<EditorRequestIdentity> identity = EditorRequestIdentity.from(message);
        PublishWireMessage.ErrorMessage error = new PublishWireMessage.ErrorMessage(
                identity.map(EditorRequestIdentity::requestId),
                identity.map(EditorRequestIdentity::lease),
                identity.map(EditorRequestIdentity::binding),
                Optional.of(message.opcode().code()),
                new WireStatus.ErrorInfo(code.code(), retry, detail)
        );
        try {
            errorSender.accept(actorId, error);
        } catch (RuntimeException ignored) {
            // A permission failure must never escape the network callback.
        }
    }

    private boolean allowRuntimeRequest(UUID actorId, MinimapWireMessage message) {
        try {
            return runtimeGate.allow(actorId, message);
        } catch (RuntimeException unavailableOrRejected) {
            return false;
        }
    }

    private static boolean isEditorOpcode(MinimapOpcode opcode) {
        return opcode.code() >= FIRST_EDITOR_OPCODE && opcode.code() <= LAST_EDITOR_OPCODE;
    }

    private record EditorRequestIdentity(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding
    ) {
        private static Optional<EditorRequestIdentity> from(MinimapWireMessage message) {
            if (message instanceof EditorWireMessage.EditorOpen value) {
                return Optional.of(new EditorRequestIdentity(
                        value.requestId(), value.lease(),
                        new WireIdentity.DocumentBinding(value.target(), value.documentId())
                ));
            }
            if (message instanceof EditorWireMessage.EditorResume value) {
                return Optional.of(new EditorRequestIdentity(
                        value.requestId(), value.lease(), value.binding()
                ));
            }
            if (message instanceof EditorWireMessage.RequestSourceEntries value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof EditorWireMessage.EditorOperation value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof EditorWireMessage.UploadFragment value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof EditorWireMessage.SaveDraft value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof EditorWireMessage.EditorClose value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof SnapshotWireMessage.RequestWorldSnapshot value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof SnapshotWireMessage.RequestDirtySections value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof PublishWireMessage.EditorRebase value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof PublishWireMessage.ReservePublish value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof PublishWireMessage.CommitPublish value) {
                return from(value.requestId(), value.context());
            }
            if (message instanceof PublishWireMessage.QueryPublishStatus value) {
                return Optional.of(new EditorRequestIdentity(
                        value.requestId(), value.lease(), value.binding()
                ));
            }
            return Optional.empty();
        }

        private static Optional<EditorRequestIdentity> from(
                UUID requestId,
                WireIdentity.EditorContext context
        ) {
            return Optional.of(new EditorRequestIdentity(
                    requestId, context.lease(), context.binding()
            ));
        }
    }
}
