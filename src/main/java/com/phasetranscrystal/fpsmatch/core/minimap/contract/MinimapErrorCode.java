package com.phasetranscrystal.fpsmatch.core.minimap.contract;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MinimapErrorCode {
    MALFORMED_MESSAGE(0x0001),
    UNSUPPORTED_WIRE_VERSION(0x0002),
    UNKNOWN_OPCODE(0x0003),
    WRONG_DIRECTION(0x0004),
    UNAUTHORIZED(0x0010),
    SESSION_NOT_FOUND(0x0011),
    SESSION_EXPIRED(0x0012),
    SCOPE_MISMATCH(0x0013),
    INVALID_MAP_KEY(0x0020),
    INVALID_RESOURCE_ID(0x0021),
    INVALID_PATH(0x0022),
    FORMAT_UNSUPPORTED(0x0023),
    VALIDATION_FAILED(0x0024),
    HASH_MISMATCH(0x0025),
    QUOTA_EXCEEDED(0x0026),
    FRAGMENT_CONFLICT(0x0027),
    REVISION_CONFLICT(0x0030),
    PUBLISH_TOKEN_INVALID(0x0031),
    PUBLISH_TOKEN_EXPIRED(0x0032),
    PUBLISH_IO_FAILED(0x0033),
    PUBLISH_IO_DEGRADED(0x0034),
    PUBLISH_STATUS_UNKNOWN(0x0035),
    MAP_UNAVAILABLE(0x0040),
    ENTRY_NOT_FOUND(0x0041),
    SNAPSHOT_UNAVAILABLE(0x0042),
    INTERNAL_ERROR(0x7fff);

    private static final Map<Integer, MinimapErrorCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(MinimapErrorCode::code, Function.identity()));

    private final int code;

    MinimapErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<MinimapErrorCode> fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
