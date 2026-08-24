package com.ptcrys.fpsmatch.core.minimap.storage;

import java.util.Objects;
import java.util.Optional;

public record RepositorySessionCapability(
        Protocol protocol,
        Optional<RepositorySessionProvider> provider
) {
    public enum Protocol {
        UNSUPPORTED,
        STRICT_SESSION_V1
    }

    private static final RepositorySessionCapability UNSUPPORTED =
            new RepositorySessionCapability(Protocol.UNSUPPORTED, Optional.empty());

    public RepositorySessionCapability {
        Objects.requireNonNull(protocol, "protocol");
        provider = Objects.requireNonNull(provider, "provider");
        if ((protocol == Protocol.STRICT_SESSION_V1) != provider.isPresent()) {
            throw new IllegalArgumentException(
                    "Strict session capability and provider must be present together"
            );
        }
    }

    public static RepositorySessionCapability unsupported() {
        return UNSUPPORTED;
    }

    public static RepositorySessionCapability strict(
            RepositorySessionProvider provider
    ) {
        return new RepositorySessionCapability(
                Protocol.STRICT_SESSION_V1,
                Optional.of(Objects.requireNonNull(provider, "provider"))
        );
    }

    public boolean supported() {
        return protocol == Protocol.STRICT_SESSION_V1;
    }
}
