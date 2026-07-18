package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WireEditor {
    private WireEditor() {
    }

    public enum OpenMode {
        OPEN_EXISTING(0),
        CREATE_EMPTY(1),
        CREATE_FLATTENED_RUNTIME(2),
        IMPORT_SOURCE(3);

        private final int code;

        OpenMode(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static OpenMode fromCode(int code) {
            for (OpenMode value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor open mode"
            );
        }
    }

    public enum MediaType {
        JSON(0),
        PNG(1),
        BINARY(2);

        private final int code;

        MediaType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static MediaType fromCode(int code) {
            for (MediaType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor media type"
            );
        }
    }

    public sealed interface DraftMutation permits Put, Delete {
        int tag();
    }

    public enum UploadPurpose {
        SOURCE_CONTAINER(0, MinimapHardLimits.MAX_SOURCE_CONTAINER_UPLOAD_BYTES),
        RUNTIME_CONTAINER(1, MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES),
        SOURCE_ENTRY(2, MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES);

        private final int code;
        private final long maximumBytes;

        UploadPurpose(int code, long maximumBytes) {
            this.code = code;
            this.maximumBytes = maximumBytes;
        }

        public int code() {
            return code;
        }

        long maximumBytes() {
            return maximumBytes;
        }

        public static UploadPurpose fromCode(int code) {
            for (UploadPurpose value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor upload purpose"
            );
        }
    }

    public enum CloseMode {
        KEEP_DRAFT(0),
        DISCARD_DRAFT(1);

        private final int code;

        CloseMode(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static CloseMode fromCode(int code) {
            for (CloseMode value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor close mode"
            );
        }
    }

    public enum SourceAvailability {
        FULL_SOURCE(0),
        FLATTEN_ONLY(1),
        NONE(2);

        private final int code;

        SourceAvailability(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static SourceAvailability fromCode(int code) {
            for (SourceAvailability value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown editor source availability"
            );
        }
    }

    public sealed interface UploadActionData permits UploadBegin, UploadData,
            UploadFinish, UploadAbort {
        int tag();
    }

    public record UploadBegin(
            UploadPurpose purpose,
            Optional<ContainerPath> path,
            long totalLength,
            int fragmentCount,
            Sha256 expectedHash
    ) implements UploadActionData {
        public UploadBegin {
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(expectedHash, "expectedHash");
            if ((purpose == UploadPurpose.SOURCE_ENTRY) != path.isPresent()) {
                throw new IllegalArgumentException(
                        "Source-entry uploads alone require a container path"
                );
            }
            if (totalLength <= 0 || totalLength > purpose.maximumBytes()) {
                throw new IllegalArgumentException("Upload length exceeds its purpose limit");
            }
            long requiredFragments = (totalLength - 1L)
                    / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L;
            if (fragmentCount <= 0
                    || fragmentCount > MinimapHardLimits.MAX_WIRE_PAGE_COUNT
                    || fragmentCount != requiredFragments) {
                throw new IllegalArgumentException(
                        "Upload fragment count is not canonical"
                );
            }
        }

        @Override
        public int tag() {
            return 0;
        }
    }

    public record UploadData(WireTransfer.TransferFragment transfer)
            implements UploadActionData {
        public UploadData {
            Objects.requireNonNull(transfer, "transfer");
        }

        @Override
        public int tag() {
            return 1;
        }
    }

    public record UploadFinish(UUID uploadId) implements UploadActionData {
        public UploadFinish {
            Objects.requireNonNull(uploadId, "uploadId");
        }

        @Override
        public int tag() {
            return 2;
        }
    }

    public record UploadAbort(UUID uploadId) implements UploadActionData {
        public UploadAbort {
            Objects.requireNonNull(uploadId, "uploadId");
        }

        @Override
        public int tag() {
            return 3;
        }
    }

    public sealed interface AckData permits OperationAck, DraftSaved, UploadAck, Closed {
        int tag();
    }

    public record OperationAck() implements AckData {
        @Override
        public int tag() {
            return 0;
        }
    }

    public record DraftSaved(boolean compacted) implements AckData {
        @Override
        public int tag() {
            return 1;
        }
    }

    public record UploadAck(
            UUID uploadId,
            int receivedFragments,
            long receivedBytes,
            boolean complete,
            Optional<Sha256> objectHash
    ) implements AckData {
        public UploadAck {
            Objects.requireNonNull(uploadId, "uploadId");
            if (receivedFragments < 0
                    || receivedFragments > MinimapHardLimits.MAX_WIRE_PAGE_COUNT) {
                throw new IllegalArgumentException(
                        "Upload ACK fragment count exceeds its limit"
                );
            }
            if (receivedBytes < 0
                    || receivedBytes > MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES) {
                throw new IllegalArgumentException(
                        "Upload ACK byte count exceeds its limit"
                );
            }
            Objects.requireNonNull(objectHash, "objectHash");
            if (complete != objectHash.isPresent()) {
                throw new IllegalArgumentException(
                        "Upload ACK completion and object hash must agree"
                );
            }
        }

        @Override
        public int tag() {
            return 2;
        }
    }

    public record Closed(CloseMode closeMode) implements AckData {
        public Closed {
            Objects.requireNonNull(closeMode, "closeMode");
        }

        @Override
        public int tag() {
            return 3;
        }
    }

    public record Put(
            ContainerPath path,
            MediaType mediaType,
            Optional<Sha256> oldHash,
            Sha256 newHash,
            UUID completedUploadId
    ) implements DraftMutation {
        public Put {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(mediaType, "mediaType");
            Objects.requireNonNull(oldHash, "oldHash");
            Objects.requireNonNull(newHash, "newHash");
            Objects.requireNonNull(completedUploadId, "completedUploadId");
        }

        @Override
        public int tag() {
            return 0;
        }
    }

    public record Delete(ContainerPath path, Sha256 oldHash)
            implements DraftMutation {
        public Delete {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(oldHash, "oldHash");
        }

        @Override
        public int tag() {
            return 1;
        }
    }

    public enum ResolutionChoice {
        OURS(0),
        THEIRS(1);

        private final int code;

        ResolutionChoice(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static ResolutionChoice fromCode(int code) {
            for (ResolutionChoice value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown rebase resolution choice"
            );
        }
    }

    public sealed interface RebaseData permits RebaseStart, RebaseResolve {
        int tag();
    }

    public record RebaseStart(long theirsRevision, Sha256 theirsHash)
            implements RebaseData {
        public RebaseStart {
            if (theirsRevision < 0) {
                throw new IllegalArgumentException(
                        "Rebase revision must be non-negative"
                );
            }
            Objects.requireNonNull(theirsHash, "theirsHash");
        }

        @Override
        public int tag() {
            return 0;
        }
    }

    public record Resolution(Sha256 conflictHash, ResolutionChoice choice) {
        public Resolution {
            Objects.requireNonNull(conflictHash, "conflictHash");
            Objects.requireNonNull(choice, "choice");
        }
    }

    public record RebaseResolve(UUID rebaseId, List<Resolution> resolutions)
            implements RebaseData {
        public RebaseResolve {
            Objects.requireNonNull(rebaseId, "rebaseId");
            resolutions = WireCollections.copyBounded(
                    resolutions,
                    MinimapHardLimits.MAX_REBASE_ITEMS,
                    "Rebase resolutions"
            );
        }

        @Override
        public int tag() {
            return 1;
        }
    }

    public sealed interface ConflictSubject permits PathSubject, IdSubject {
        int tag();
    }

    public record PathSubject(ContainerPath path) implements ConflictSubject {
        public PathSubject {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public int tag() {
            return 0;
        }
    }

    public record IdSubject(
            com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId id
    ) implements ConflictSubject {
        public IdSubject {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public int tag() {
            return 1;
        }
    }

    public record Conflict(
            Sha256 conflictHash,
            ConflictSubject subject,
            Optional<Sha256> oursHash,
            Optional<Sha256> theirsHash
    ) {
        public Conflict {
            Objects.requireNonNull(conflictHash, "conflictHash");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(oursHash, "oursHash");
            Objects.requireNonNull(theirsHash, "theirsHash");
        }
    }

    static void requireEditorLease(WireIdentity.ScopeLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (lease.scope() != WireIdentity.Scope.EDITOR) {
            throw new IllegalArgumentException("Editor messages require editor scope");
        }
    }
}
