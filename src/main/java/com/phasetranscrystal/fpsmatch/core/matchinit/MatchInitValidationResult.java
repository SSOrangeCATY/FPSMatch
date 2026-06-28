package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MatchInitValidationResult(
        boolean accepted,
        Optional<AcceptedMatchContext> context,
        List<String> errors
) {
    public MatchInitValidationResult {
        Objects.requireNonNull(context, "context");
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    public static MatchInitValidationResult accepted(AcceptedMatchContext context) {
        return new MatchInitValidationResult(true, Optional.of(Objects.requireNonNull(context, "context")), List.of());
    }

    public static MatchInitValidationResult rejected(List<String> errors) {
        return new MatchInitValidationResult(false, Optional.empty(), errors);
    }
}
