package com.phasetranscrystal.fpsmatch.core.minimap.format;

import java.util.Objects;

public record MinimapValidationIssue(MinimapValidationCode code, String path, String message) {
    public MinimapValidationIssue {
        Objects.requireNonNull(code, "code");
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Validation issue path must be an absolute JSON pointer");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Validation issue message cannot be empty");
        }
    }
}
