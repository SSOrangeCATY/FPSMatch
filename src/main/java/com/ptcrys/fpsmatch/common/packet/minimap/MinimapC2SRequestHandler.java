package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.Objects;
import java.util.UUID;

/** Routes decoded C2S messages while enforcing the editor authorization boundary. */
public final class MinimapC2SRequestHandler {
    private static final int FIRST_EDITOR_OPCODE = 0x10;
    private static final int LAST_EDITOR_OPCODE = 0x3f;

    private final MinimapRuntimeRequestGate runtimeGate;
    private final MinimapEditorRequestGate editorGate;
    private final MinimapC2SDispatcher dispatcher;

    public MinimapC2SRequestHandler(
            MinimapEditorRequestGate editorGate,
            MinimapC2SDispatcher dispatcher
    ) {
        this((actorId, message) -> true, editorGate, dispatcher);
    }

    public MinimapC2SRequestHandler(
            MinimapRuntimeRequestGate runtimeGate,
            MinimapEditorRequestGate editorGate,
            MinimapC2SDispatcher dispatcher
    ) {
        this.runtimeGate = Objects.requireNonNull(runtimeGate, "runtimeGate");
        this.editorGate = Objects.requireNonNull(editorGate, "editorGate");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
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
            return editorGate.revalidatePermissionAndScope(actorId, message);
        } catch (RuntimeException unavailableOrRejected) {
            return false;
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
}
