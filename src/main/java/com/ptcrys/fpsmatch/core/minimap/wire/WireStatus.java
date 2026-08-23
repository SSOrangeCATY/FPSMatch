package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public final class WireStatus {
    private WireStatus() {
    }

    public enum RebaseResultStatus {
        MERGED(0),
        CONFLICTS(1);

        private final int code;

        RebaseResultStatus(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static RebaseResultStatus fromCode(int code) {
            for (RebaseResultStatus value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown rebase-result status"
            );
        }
    }

    public enum PublishOutcome {
        COMMITTED(0),
        ABORTED(1),
        STATUS_UNKNOWN(2);

        private final int code;

        PublishOutcome(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static PublishOutcome fromCode(int code) {
            for (PublishOutcome value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown publish outcome"
            );
        }
    }

    public enum RetryDisposition {
        DO_NOT_RETRY(0),
        RETRY_NEW_REQUEST(1),
        REOPEN_SESSION(2),
        RESYNC_SCOPE(3),
        QUERY_PUBLISH_STATUS(4);

        private final int code;

        RetryDisposition(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static RetryDisposition fromCode(int code) {
            for (RetryDisposition value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown retry disposition"
            );
        }
    }

    public enum PublishState {
        RESERVED(0),
        PREPARED(1),
        COMMITTED(2),
        ABORTED(3),
        STATUS_UNKNOWN(4);

        private final int code;

        PublishState(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static PublishState fromCode(int code) {
            for (PublishState value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "unknown publish state"
            );
        }
    }

    public record HashTriple(
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        public HashTriple {
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
        }
    }

    public record ErrorInfo(
            int errorCode,
            RetryDisposition retryDisposition,
            String detail
    ) {
        public ErrorInfo {
            requireKnownErrorCode(errorCode);
            Objects.requireNonNull(retryDisposition, "retryDisposition");
            detail = WireText.requireUtf8(
                    detail,
                    MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES,
                    "error detail"
            );
        }

        public MinimapErrorCode knownCode() {
            return requireKnownErrorCode(errorCode);
        }
    }

    static MinimapErrorCode requireKnownErrorCode(int errorCode) {
        return MinimapErrorCode.fromCode(errorCode).orElseThrow(() ->
                new IllegalArgumentException("Unknown minimap error code")
        );
    }
}
