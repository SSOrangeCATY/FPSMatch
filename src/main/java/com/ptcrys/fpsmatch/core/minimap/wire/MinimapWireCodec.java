package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapProtocolContract;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Platform-neutral v1 frame codec used inside the two loader-specific envelopes. */
public final class MinimapWireCodec {
    private static final int FIXED_HEADER_BYTES = 3;
    private static final Map<Integer, MinimapOpcode> OPCODES = Arrays.stream(MinimapOpcode.values())
            .collect(Collectors.toUnmodifiableMap(MinimapOpcode::code, Function.identity()));
    private static final Set<MinimapOpcode> REGISTERED_OPCODES = Set.copyOf(EnumSet.of(
            MinimapOpcode.C2S_SUBSCRIBE,
            MinimapOpcode.C2S_UNSUBSCRIBE,
            MinimapOpcode.C2S_REQUEST_ENTRIES,
            MinimapOpcode.C2S_REQUEST_MARKER_RESET,
            MinimapOpcode.C2S_EDITOR_OPEN,
            MinimapOpcode.C2S_EDITOR_RESUME,
            MinimapOpcode.C2S_EDITOR_REQUEST_SOURCE_ENTRIES,
            MinimapOpcode.C2S_EDITOR_OPERATION,
            MinimapOpcode.C2S_EDITOR_UPLOAD_FRAGMENT,
            MinimapOpcode.C2S_EDITOR_SAVE_DRAFT,
            MinimapOpcode.C2S_EDITOR_REBASE,
            MinimapOpcode.C2S_EDITOR_REQUEST_WORLD_SNAPSHOT,
            MinimapOpcode.C2S_EDITOR_REQUEST_DIRTY_SECTIONS,
            MinimapOpcode.C2S_EDITOR_RESERVE_PUBLISH,
            MinimapOpcode.C2S_EDITOR_COMMIT_PUBLISH,
            MinimapOpcode.C2S_EDITOR_CLOSE,
            MinimapOpcode.C2S_EDITOR_QUERY_PUBLISH_STATUS,
            MinimapOpcode.S2C_SCOPE_ACK,
            MinimapOpcode.S2C_MANIFEST,
            MinimapOpcode.S2C_ENTRY_FRAGMENT,
            MinimapOpcode.S2C_MARKER_RESET,
            MinimapOpcode.S2C_MARKER_DELTA,
            MinimapOpcode.S2C_EDITOR_SESSION,
            MinimapOpcode.S2C_EDITOR_SOURCE_MANIFEST,
            MinimapOpcode.S2C_EDITOR_SOURCE_FRAGMENT,
            MinimapOpcode.S2C_EDITOR_ACK,
            MinimapOpcode.S2C_EDITOR_REBASE_RESULT,
            MinimapOpcode.S2C_WORLD_SNAPSHOT_MANIFEST,
            MinimapOpcode.S2C_WORLD_SNAPSHOT_FRAGMENT,
            MinimapOpcode.S2C_DIRTY_SECTIONS,
            MinimapOpcode.S2C_PUBLISH_RESERVATION,
            MinimapOpcode.S2C_PUBLISH_RESULT,
            MinimapOpcode.S2C_PUBLISH_STATUS,
            MinimapOpcode.S2C_ERROR
    ));

    private MinimapWireCodec() {
    }

    public static Set<MinimapOpcode> registeredOpcodes() {
        return REGISTERED_OPCODES;
    }

    /**
     * Encodes {@code major:u8, minor:u8, opcode:u8, payloadLength:uvarint, payload:bytes}.
     */
    public static byte[] encode(MinimapWireMessage message) {
        if (message == null) {
            throw malformed("message is null");
        }
        if (message instanceof MarkerWireMessage marker) {
            return encodeMarkerFrame(prepareMarkerFrame(marker));
        }
        byte[] payload = encodeBody(message);
        int lengthBytes = unsignedVarIntSize(payload.length);
        long frameLength = (long) FIXED_HEADER_BYTES + lengthBytes + payload.length;
        if (frameLength > MinimapHardLimits.MAX_WIRE_FRAME_BYTES) {
            throw quota("wire frame exceeds the hard byte limit");
        }

        byte[] frame = new byte[(int) frameLength];
        int cursor = 0;
        frame[cursor++] = (byte) MinimapProtocolContract.WIRE_MAJOR;
        frame[cursor++] = (byte) MinimapProtocolContract.WIRE_MINOR;
        frame[cursor++] = (byte) message.opcode().code();
        cursor = writeUnsignedVarInt(frame, cursor, payload.length);
        System.arraycopy(payload, 0, frame, cursor, payload.length);
        return frame;
    }

    public static PreparedMarkerFrame prepareMarkerFrame(
            MarkerWireMessage message
    ) {
        if (message == null) {
            throw malformed("marker message is null");
        }
        WireWriter counter = WireWriter.counting(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES
        );
        writeMarkerBody(counter, message);
        int payloadLength = counter.writtenBytes();
        int frameLength = Math.addExact(
                FIXED_HEADER_BYTES + unsignedVarIntSize(payloadLength),
                payloadLength
        );
        if (frameLength > MinimapHardLimits.MAX_WIRE_FRAME_BYTES) {
            throw quota("wire frame exceeds the hard byte limit");
        }
        return new PreparedMarkerFrame(message, payloadLength, frameLength);
    }

    public static int markerFrameLength(MarkerWireMessage message) {
        return prepareMarkerFrame(message).length();
    }

    public static void writeMarkerFrame(
            MarkerWireMessage message,
            FrameSink sink
    ) {
        prepareMarkerFrame(message).writeTo(sink);
    }

    private static byte[] encodeMarkerFrame(PreparedMarkerFrame prepared) {
        WireWriter frame = new WireWriter(prepared.length(), prepared.length());
        prepared.writeTo(frame);
        return frame.takeExactByteArray();
    }

    private static void writeMarkerFrame(
            WireWriter frame,
            MarkerWireMessage message,
            int payloadLength
    ) {
        frame.writeUnsignedByte(MinimapProtocolContract.WIRE_MAJOR);
        frame.writeUnsignedByte(MinimapProtocolContract.WIRE_MINOR);
        frame.writeUnsignedByte(message.opcode().code());
        frame.writeUnsignedVarInt(payloadLength);
        writeMarkerBody(frame, message);
    }

    private static void writeMarkerBody(
            WireWriter writer,
            MarkerWireMessage message
    ) {
        if (message instanceof MarkerWireMessage.Reset reset) {
            MarkerWireCodec.writeReset(writer, reset);
        } else {
            MarkerWireCodec.writeDelta(
                    writer, (MarkerWireMessage.Delta) message
            );
        }
    }

    public interface FrameSink {
        void writeByte(int value);

        void writeBytes(byte[] value);
    }

    public static final class PreparedMarkerFrame {
        private final MarkerWireMessage message;
        private final int payloadLength;
        private final int length;

        private PreparedMarkerFrame(
                MarkerWireMessage message,
                int payloadLength,
                int length
        ) {
            this.message = message;
            this.payloadLength = payloadLength;
            this.length = length;
        }

        public int length() {
            return length;
        }

        public MarkerWireMessage message() {
            return message;
        }

        public void writeTo(FrameSink sink) {
            WireWriter writer = WireWriter.streaming(length, sink);
            writeTo(writer);
        }

        private void writeTo(WireWriter writer) {
            writeMarkerFrame(writer, message, payloadLength);
            if (writer.writtenBytes() != length) {
                throw new IllegalStateException(
                        "Prepared marker frame length changed during encoding"
                );
            }
        }
    }

    public static MinimapWireMessage decode(MinimapMessageDirection expectedDirection, byte[] frame) {
        if (frame == null) {
            throw malformed("wire frame is null");
        }
        return decode(expectedDirection, ByteBuffer.wrap(frame));
    }

    /** Decodes without mutating the supplied buffer's position or limit. */
    public static MinimapWireMessage decode(
            MinimapMessageDirection expectedDirection,
            ByteBuffer frame
    ) {
        if (expectedDirection == null || frame == null) {
            throw malformed("wire decode argument is null");
        }

        ByteBuffer input = frame.slice();
        if (input.remaining() > MinimapHardLimits.MAX_WIRE_FRAME_BYTES) {
            throw quota("wire frame exceeds the hard byte limit");
        }

        int major = readUnsignedByte(input);
        int minor = readUnsignedByte(input);
        if (major != MinimapProtocolContract.WIRE_MAJOR
                || minor != MinimapProtocolContract.WIRE_MINOR) {
            throw new MinimapWireError(
                    MinimapErrorCode.UNSUPPORTED_WIRE_VERSION,
                    "unsupported minimap wire version"
            );
        }

        int opcodeValue = readUnsignedByte(input);
        MinimapOpcode opcode = OPCODES.get(opcodeValue);
        if (opcode == null) {
            throw new MinimapWireError(MinimapErrorCode.UNKNOWN_OPCODE, "unknown minimap opcode");
        }
        if (opcode.direction() != expectedDirection) {
            throw new MinimapWireError(MinimapErrorCode.WRONG_DIRECTION, "wrong minimap message direction");
        }

        int payloadLength = readUnsignedVarInt(input, MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        if (input.remaining() < payloadLength) {
            throw malformed("truncated minimap payload");
        }
        byte[] payload = new byte[payloadLength];
        input.get(payload);
        if (input.hasRemaining()) {
            throw malformed("trailing minimap frame bytes");
        }
        if (opcode == MinimapOpcode.C2S_SUBSCRIBE) {
            try {
                return RuntimeWireCodec.decodeSubscribe(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid subscribe body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_UNSUBSCRIBE) {
            try {
                return RuntimeWireCodec.decodeUnsubscribe(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid unsubscribe body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_REQUEST_ENTRIES) {
            try {
                return RuntimeWireCodec.decodeRequestEntries(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid request-entries body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_REQUEST_MARKER_RESET) {
            try {
                return RuntimeWireCodec.decodeRequestMarkerReset(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid marker-reset request body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_OPEN) {
            try {
                return EditorWireCodec.decodeEditorOpen(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-open body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_RESUME) {
            try {
                return EditorWireCodec.decodeEditorResume(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-resume body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_REQUEST_SOURCE_ENTRIES) {
            try {
                return EditorWireCodec.decodeRequestSourceEntries(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor source-entry request body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_OPERATION) {
            try {
                return EditorWireCodec.decodeEditorOperation(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-operation body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_UPLOAD_FRAGMENT) {
            try {
                return EditorWireCodec.decodeUploadFragment(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-upload body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_SAVE_DRAFT) {
            try {
                return EditorWireCodec.decodeSaveDraft(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-save body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_REBASE) {
            try {
                return PublishWireCodec.decodeEditorRebase(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-rebase body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_REQUEST_WORLD_SNAPSHOT) {
            try {
                return SnapshotWireCodec.decodeRequestWorldSnapshot(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid world-snapshot request body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_REQUEST_DIRTY_SECTIONS) {
            try {
                return SnapshotWireCodec.decodeRequestDirtySections(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid dirty-section request body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_RESERVE_PUBLISH) {
            try {
                return PublishWireCodec.decodeReservePublish(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid publish-reservation request body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_COMMIT_PUBLISH) {
            try {
                return PublishWireCodec.decodeCommitPublish(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid publish-commit body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_CLOSE) {
            try {
                return EditorWireCodec.decodeEditorClose(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-close body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.C2S_EDITOR_QUERY_PUBLISH_STATUS) {
            try {
                return PublishWireCodec.decodeQueryPublishStatus(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid publish-status query body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_SCOPE_ACK) {
            try {
                return RuntimeWireCodec.decodeScopeAck(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid scope-ack body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_MANIFEST) {
            try {
                return RuntimeWireCodec.decodeManifest(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid manifest body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_ENTRY_FRAGMENT) {
            try {
                return RuntimeWireCodec.decodeEntryFragment(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid entry-fragment body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_MARKER_RESET) {
            try {
                return MarkerWireCodec.decodeReset(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid marker-reset body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_MARKER_DELTA) {
            try {
                return MarkerWireCodec.decodeDelta(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid marker-delta body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_EDITOR_SESSION) {
            try {
                return EditorWireCodec.decodeEditorSession(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-session body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_EDITOR_SOURCE_MANIFEST) {
            try {
                return EditorWireCodec.decodeSourceManifest(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor source-manifest body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_EDITOR_SOURCE_FRAGMENT) {
            try {
                return EditorWireCodec.decodeSourceFragment(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor source-fragment body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_EDITOR_ACK) {
            try {
                return EditorWireCodec.decodeEditorAck(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-ack body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_EDITOR_REBASE_RESULT) {
            try {
                return PublishWireCodec.decodeEditorRebaseResult(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid editor-rebase result body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_WORLD_SNAPSHOT_MANIFEST) {
            try {
                return SnapshotWireCodec.decodeWorldSnapshotManifest(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid world-snapshot manifest body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_WORLD_SNAPSHOT_FRAGMENT) {
            try {
                return SnapshotWireCodec.decodeWorldSnapshotFragment(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid world-snapshot fragment body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_DIRTY_SECTIONS) {
            try {
                return SnapshotWireCodec.decodeDirtySections(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid dirty-section body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_PUBLISH_RESERVATION) {
            try {
                return PublishWireCodec.decodePublishReservation(
                        new WireReader(payload)
                );
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid publish-reservation body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_PUBLISH_RESULT) {
            try {
                return PublishWireCodec.decodePublishResult(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid publish-result body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_PUBLISH_STATUS) {
            try {
                return PublishWireCodec.decodePublishStatus(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid publish-status body",
                        error
                );
            }
        }
        if (opcode == MinimapOpcode.S2C_ERROR) {
            try {
                return PublishWireCodec.decodeErrorMessage(new WireReader(payload));
            } catch (MinimapWireError error) {
                throw error;
            } catch (IllegalArgumentException error) {
                throw new MinimapWireError(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "invalid error body",
                        error
                );
            }
        }
        throw malformed("registered opcode has no typed decoder");
    }

    private static byte[] encodeBody(MinimapWireMessage message) {
        if (message instanceof RuntimeWireMessage.Subscribe subscribe) {
            return RuntimeWireCodec.encodeSubscribe(subscribe);
        }
        if (message instanceof RuntimeWireMessage.Unsubscribe unsubscribe) {
            return RuntimeWireCodec.encodeUnsubscribe(unsubscribe);
        }
        if (message instanceof RuntimeWireMessage.RequestEntries requestEntries) {
            return RuntimeWireCodec.encodeRequestEntries(requestEntries);
        }
        if (message instanceof RuntimeWireMessage.RequestMarkerReset requestMarkerReset) {
            return RuntimeWireCodec.encodeRequestMarkerReset(requestMarkerReset);
        }
        if (message instanceof RuntimeWireMessage.ScopeAck scopeAck) {
            return RuntimeWireCodec.encodeScopeAck(scopeAck);
        }
        if (message instanceof RuntimeWireMessage.Manifest manifest) {
            return RuntimeWireCodec.encodeManifest(manifest);
        }
        if (message instanceof RuntimeWireMessage.EntryFragment entryFragment) {
            return RuntimeWireCodec.encodeEntryFragment(entryFragment);
        }
        if (message instanceof EditorWireMessage.EditorOpen editorOpen) {
            return EditorWireCodec.encodeEditorOpen(editorOpen);
        }
        if (message instanceof EditorWireMessage.EditorResume editorResume) {
            return EditorWireCodec.encodeEditorResume(editorResume);
        }
        if (message instanceof EditorWireMessage.RequestSourceEntries requestSourceEntries) {
            return EditorWireCodec.encodeRequestSourceEntries(requestSourceEntries);
        }
        if (message instanceof EditorWireMessage.EditorOperation editorOperation) {
            return EditorWireCodec.encodeEditorOperation(editorOperation);
        }
        if (message instanceof EditorWireMessage.UploadFragment uploadFragment) {
            return EditorWireCodec.encodeUploadFragment(uploadFragment);
        }
        if (message instanceof EditorWireMessage.SaveDraft saveDraft) {
            return EditorWireCodec.encodeSaveDraft(saveDraft);
        }
        if (message instanceof PublishWireMessage.EditorRebase editorRebase) {
            return PublishWireCodec.encodeEditorRebase(editorRebase);
        }
        if (message instanceof SnapshotWireMessage.RequestWorldSnapshot request) {
            return SnapshotWireCodec.encodeRequestWorldSnapshot(request);
        }
        if (message instanceof SnapshotWireMessage.RequestDirtySections request) {
            return SnapshotWireCodec.encodeRequestDirtySections(request);
        }
        if (message instanceof PublishWireMessage.ReservePublish reservePublish) {
            return PublishWireCodec.encodeReservePublish(reservePublish);
        }
        if (message instanceof PublishWireMessage.CommitPublish commitPublish) {
            return PublishWireCodec.encodeCommitPublish(commitPublish);
        }
        if (message instanceof EditorWireMessage.EditorClose editorClose) {
            return EditorWireCodec.encodeEditorClose(editorClose);
        }
        if (message instanceof PublishWireMessage.QueryPublishStatus query) {
            return PublishWireCodec.encodeQueryPublishStatus(query);
        }
        if (message instanceof EditorWireMessage.EditorSession editorSession) {
            return EditorWireCodec.encodeEditorSession(editorSession);
        }
        if (message instanceof EditorWireMessage.SourceManifest sourceManifest) {
            return EditorWireCodec.encodeSourceManifest(sourceManifest);
        }
        if (message instanceof EditorWireMessage.SourceFragment sourceFragment) {
            return EditorWireCodec.encodeSourceFragment(sourceFragment);
        }
        if (message instanceof EditorWireMessage.EditorAck editorAck) {
            return EditorWireCodec.encodeEditorAck(editorAck);
        }
        if (message instanceof PublishWireMessage.EditorRebaseResult result) {
            return PublishWireCodec.encodeEditorRebaseResult(result);
        }
        if (message instanceof SnapshotWireMessage.WorldSnapshotManifest manifest) {
            return SnapshotWireCodec.encodeWorldSnapshotManifest(manifest);
        }
        if (message instanceof SnapshotWireMessage.WorldSnapshotFragment fragment) {
            return SnapshotWireCodec.encodeWorldSnapshotFragment(fragment);
        }
        if (message instanceof SnapshotWireMessage.DirtySections dirtySections) {
            return SnapshotWireCodec.encodeDirtySections(dirtySections);
        }
        if (message instanceof PublishWireMessage.PublishReservation reservation) {
            return PublishWireCodec.encodePublishReservation(reservation);
        }
        if (message instanceof PublishWireMessage.PublishResult publishResult) {
            return PublishWireCodec.encodePublishResult(publishResult);
        }
        if (message instanceof PublishWireMessage.PublishStatus publishStatus) {
            return PublishWireCodec.encodePublishStatus(publishStatus);
        }
        if (message instanceof PublishWireMessage.ErrorMessage errorMessage) {
            return PublishWireCodec.encodeErrorMessage(errorMessage);
        }
        if (message instanceof MarkerWireMessage.Reset reset) {
            return MarkerWireCodec.encodeReset(reset);
        }
        if (message instanceof MarkerWireMessage.Delta delta) {
            return MarkerWireCodec.encodeDelta(delta);
        }
        throw malformed("unregistered typed minimap message");
    }

    private static int readUnsignedByte(ByteBuffer input) {
        if (!input.hasRemaining()) {
            throw malformed("truncated minimap frame");
        }
        return Byte.toUnsignedInt(input.get());
    }

    private static int readUnsignedVarInt(ByteBuffer input, int maximum) {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            int next = readUnsignedByte(input);
            if (index == 4 && (next & 0xf0) != 0) {
                throw malformed("overflowing unsigned VarInt");
            }
            value |= (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) {
                if (unsignedVarIntSize(value) != index + 1) {
                    throw malformed("non-canonical unsigned VarInt");
                }
                if (value > maximum) {
                    throw quota("length exceeds its hard limit");
                }
                return value;
            }
        }
        throw malformed("overflowing unsigned VarInt");
    }

    private static int writeUnsignedVarInt(byte[] output, int cursor, int value) {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            output[cursor++] = (byte) ((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        output[cursor++] = (byte) remaining;
        return cursor;
    }

    private static int unsignedVarIntSize(int value) {
        if (value < 0) {
            throw malformed("negative unsigned VarInt");
        }
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(MinimapErrorCode.MALFORMED_MESSAGE, message);
    }

    private static MinimapWireError quota(String message) {
        return new MinimapWireError(MinimapErrorCode.QUOTA_EXCEEDED, message);
    }
}
