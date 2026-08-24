package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Side-effect-free capability composition followed by the provider's strict
 * probe. Legacy lock and metadata paths are intentionally outside this facade.
 */
final class StrictRepositoryStorage {
    enum CapabilityStatus {
        SUPPORTED,
        UNSUPPORTED,
        MISMATCHED,
        UNAVAILABLE
    }

    record Capability(
            CapabilityStatus status,
            RepositorySessionProvider provider,
            String detail
    ) {
        Capability {
            Objects.requireNonNull(status, "status");
            detail = detail == null ? "" : detail;
            if (status == CapabilityStatus.SUPPORTED) {
                Objects.requireNonNull(provider, "provider");
            } else if (provider != null) {
                throw new IllegalArgumentException(
                        "Fail-closed capability cannot expose a provider"
                );
            }
        }

        static Capability unsupported(String detail) {
            return new Capability(CapabilityStatus.UNSUPPORTED, null, detail);
        }

        static Capability unavailable(String detail) {
            return new Capability(CapabilityStatus.UNAVAILABLE, null, detail);
        }

        static Capability mismatched(String detail) {
            return new Capability(CapabilityStatus.MISMATCHED, null, detail);
        }
    }

    enum ProbeStatus {
        SUPPORTED,
        UNSUPPORTED,
        UNAVAILABLE
    }

    record ProbeResult(ProbeStatus status, String detail) {
        ProbeResult {
            Objects.requireNonNull(status, "status");
            detail = detail == null ? "" : detail;
        }

        static ProbeResult supported(String detail) {
            return new ProbeResult(ProbeStatus.SUPPORTED, detail);
        }

        static ProbeResult unsupported(String detail) {
            return new ProbeResult(ProbeStatus.UNSUPPORTED, detail);
        }

        static ProbeResult unavailable(String detail) {
            return new ProbeResult(ProbeStatus.UNAVAILABLE, detail);
        }
    }

    private final RepositoryFileSystem fileSystem;
    private final AuthorityJournalProvider authorityProvider;

    private StrictRepositoryStorage(
            RepositoryFileSystem fileSystem,
            AuthorityJournalProvider authorityProvider
    ) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.authorityProvider = Objects.requireNonNull(
                authorityProvider, "authorityProvider"
        );
    }

    static StrictRepositoryStorage compose(
            RepositoryFileSystem fileSystem,
            AuthorityJournalProvider authorityProvider
    ) {
        return new StrictRepositoryStorage(fileSystem, authorityProvider);
    }

    Capability capability() {
        try {
            RepositorySessionCapability fileSystemCapability =
                    fileSystem.repositorySessionCapability();
            RepositorySessionCapability authorityCapability =
                    authorityProvider.repositorySessionCapability();
            boolean fileSystemSupported = fileSystemCapability.supported();
            boolean authoritySupported = authorityCapability.supported();
            if (!fileSystemSupported && !authoritySupported) {
                return Capability.unsupported(
                        "Strict repository session capability is unsupported"
                );
            }
            if (fileSystemSupported != authoritySupported) {
                if (fileSystemSupported && !authoritySupported) {
                    return Capability.unsupported(
                            "Authority provider does not opt into strict sessions"
                    );
                }
                if (!fileSystemSupported
                        && authorityProvider instanceof WindowsAuthorityJournalProvider) {
                    return Capability.unsupported(
                            "Legacy filesystem adapter does not opt into strict sessions"
                    );
                }
                return Capability.mismatched(
                        "Filesystem and authority strict capabilities are asymmetric"
                );
            }
            RepositorySessionProvider fileSystemProvider =
                    fileSystemCapability.provider().orElseThrow();
            RepositorySessionProvider authoritySessionProvider =
                    authorityCapability.provider().orElseThrow();
            if (fileSystemProvider != authoritySessionProvider) {
                return Capability.mismatched(
                        "Filesystem and authority providers are different objects"
                );
            }
            return new Capability(
                    CapabilityStatus.SUPPORTED, fileSystemProvider, ""
            );
        } catch (RuntimeException failure) {
            return Capability.unavailable(
                    "Unable to classify strict repository capability: "
                            + failure.getMessage()
            );
        }
    }

    ProbeResult probe(Path mapDirectory) {
        Objects.requireNonNull(mapDirectory, "mapDirectory");
        Capability capability = capability();
        if (capability.status() != CapabilityStatus.SUPPORTED) {
            return ProbeResult.unavailable(capability.detail());
        }
        try {
            AuthorityJournalProvider.Availability availability =
                    authorityProvider.strictAvailability(mapDirectory);
            return availability.supported()
                    ? ProbeResult.supported(availability.detail())
                    : ProbeResult.unsupported(availability.detail());
        } catch (RuntimeException failure) {
            return ProbeResult.unavailable(
                    "Strict repository probe failed: " + failure.getMessage()
            );
        }
    }
}
