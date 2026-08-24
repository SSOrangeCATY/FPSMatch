package com.ptcrys.fpsmatch.core.minimap.storage;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Centralizes strict-capability classification so authority reads and writes
 * cannot accidentally fall back to the legacy filesystem lock.
 */
final class StrictRepositoryDispatch {
    enum Mode {
        LEGACY,
        STRICT
    }

    @FunctionalInterface
    interface Action<T> {
        T run() throws IOException;
    }

    private final RepositoryFileSystem fileSystem;
    private final AuthorityJournalProvider authorityProvider;
    private final StrictRepositoryStorage storage;
    private final MinimapRepositoryJournalAccess journalAccess;

    StrictRepositoryDispatch(
            RepositoryFileSystem fileSystem,
            AuthorityJournalProvider authorityProvider,
            MinimapRepositoryJournalAccess journalAccess
    ) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.authorityProvider = Objects.requireNonNull(
                authorityProvider, "authorityProvider"
        );
        this.storage = StrictRepositoryStorage.compose(fileSystem, authorityProvider);
        this.journalAccess = Objects.requireNonNull(journalAccess, "journalAccess");
    }

    Mode mode(Path mapDirectory, MapKey key) {
        Objects.requireNonNull(mapDirectory, "mapDirectory");
        Objects.requireNonNull(key, "key");
        StrictRepositoryStorage.Capability capability = storage.capability();
        if (capability.status() == StrictRepositoryStorage.CapabilityStatus.UNSUPPORTED) {
            return Mode.LEGACY;
        }
        if (capability.status() != StrictRepositoryStorage.CapabilityStatus.SUPPORTED) {
            throw unavailable(capability.detail());
        }
        StrictRepositoryStorage.ProbeResult probe = storage.probe(mapDirectory);
        if (probe.status() == StrictRepositoryStorage.ProbeStatus.UNSUPPORTED) {
            return Mode.LEGACY;
        }
        if (probe.status() == StrictRepositoryStorage.ProbeStatus.UNAVAILABLE) {
            throw unavailable(probe.detail());
        }
        try {
            return switch (journalAccess.detect(mapDirectory, key)) {
                case JOURNAL -> Mode.STRICT;
                case LEGACY -> Mode.LEGACY;
                case UNAVAILABLE -> throw unavailable(
                        "Strict authority journal is unavailable"
                );
            };
        } catch (IOException exception) {
            throw unavailable("Unable to inspect strict authority journal", exception);
        }
    }

    boolean hasStrictCapability() {
        StrictRepositoryStorage.Capability capability = storage.capability();
        if (capability.status() == StrictRepositoryStorage.CapabilityStatus.UNSUPPORTED) {
            return false;
        }
        if (capability.status() != StrictRepositoryStorage.CapabilityStatus.SUPPORTED) {
            throw unavailable(capability.detail());
        }
        return true;
    }

    <T> T withSession(Path mapDirectory, Action<T> action) {
        Objects.requireNonNull(mapDirectory, "mapDirectory");
        Objects.requireNonNull(action, "action");
        StrictRepositoryStorage.Capability capability = storage.capability();
        if (capability.status() != StrictRepositoryStorage.CapabilityStatus.SUPPORTED) {
            throw unavailable(capability.detail());
        }
        try (RepositorySessionProvider.SessionLease session =
                     capability.provider().open(mapDirectory);
             RepositorySessionProvider.OperationLease operation =
                     session.openOperation()) {
            return action.run();
        } catch (ContainerStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("Strict repository operation failed", exception);
        } catch (RuntimeException exception) {
            throw unavailable("Strict repository operation failed", exception);
        }
    }

    void ensureMapDirectory(Path mapDirectory) {
        try {
            fileSystem.createDirectories(mapDirectory);
        } catch (IOException exception) {
            throw unavailable("Unable to create strict map directory", exception);
        }
    }

    void activate(Path mapDirectory, MapKey key) {
        ensureMapDirectory(mapDirectory);
        withSession(mapDirectory, () -> {
            journalAccess.activate(mapDirectory, key);
            return null;
        });
    }

    boolean active(Path mapDirectory) {
        return withSession(mapDirectory, () -> journalAccess.isActive(mapDirectory));
    }

    Optional<MinimapAuthorityJournal.Snapshot> snapshot(Path mapDirectory) {
        return withSession(mapDirectory, () -> journalAccess.snapshot(mapDirectory));
    }

    AuthorityJournalProvider.StrictReservationResult reserveStrict(
            Path mapDirectory,
            AuthorityJournalProvider.StrictReservationRequest request
    ) {
        return withSession(mapDirectory, () -> {
            Path journalDirectory = mapDirectory.resolve("authority-journal");
            try (AuthorityJournalProvider.JournalHandle handle = authorityProvider.open(
                    journalDirectory, MinimapAuthorityJournal.defaults().config()
            )) {
                return handle.reserveStrict(request);
            }
        });
    }

    AuthorityJournalProvider.StrictReservationResult reservePublish(
            Path mapDirectory,
            String operationId
    ) {
        Objects.requireNonNull(operationId, "operationId");
        return withSession(mapDirectory, () -> {
            MinimapAuthorityJournal journal = MinimapAuthorityJournal.defaults();
            Path journalDirectory = mapDirectory.resolve("authority-journal");
            try (AuthorityJournalProvider.JournalHandle handle = authorityProvider.open(
                    journalDirectory, journal.config()
            )) {
                AuthorityJournalProvider.Inspection inspection = handle.inspect();
                MinimapAuthorityJournal.Snapshot snapshot = journal.parse(inspection);
                if (!snapshot.active() || snapshot.pending().isPresent()) {
                    return AuthorityJournalProvider.StrictReservationResult.unavailable(
                            "Strict journal is not ready for publication"
                    );
                }
                ArrayList<AuthorityJournalProvider.CapacityTarget> targets =
                        new ArrayList<>(journal.config().reservationWidth());
                for (int offset = 1;
                     offset <= journal.config().reservationWidth();
                     offset++) {
                    long generation = snapshot.headGeneration() + offset;
                    targets.add(AuthorityJournalProvider.CapacityTarget.absent(
                            generation, journal.slotIndex(generation)
                    ));
                }
                byte[] headDigest = AuthorityJournalCodec.digestBytes(
                        snapshot.headDigest()
                );
                byte[] capacityReceipt = AuthorityJournalCodec.encodeCapacityReceipt(
                        MinimapAuthorityJournal.PreflightDecision.RESERVE_OPERATION,
                        snapshot.journalInstance(), operationId,
                        snapshot.headGeneration(), headDigest, targets
                );
                AuthorityJournalProvider.CapacityRequest capacityRequest =
                        new AuthorityJournalProvider.CapacityRequest(
                                snapshot.journalInstance(), operationId,
                                snapshot.headGeneration(), headDigest,
                                targets, capacityReceipt
                        );
                byte[] attempt = operationId.getBytes(StandardCharsets.US_ASCII);
                AuthorityJournalProvider.CapacityTarget first = targets.get(0);
                List<AuthorityJournalProvider.StrictManifestRow> manifest = List.of(
                        new AuthorityJournalProvider.StrictManifestRow(
                                0, first.generation(), first.slotIndex(),
                                MinimapAuthorityJournal.Operation.PUBLISH,
                                MinimapAuthorityJournal.Phase.INTENT,
                                AuthorityJournalCodec.digestBytes(
                                        AuthorityJournalCodec.digestHex(attempt)
                                )
                        )
                );
                return handle.reserveStrict(
                        new AuthorityJournalProvider.StrictReservationRequest(
                                AuthorityJournalProvider.StrictReservationKind.PUBLISH,
                                capacityRequest, attempt, manifest
                        )
                );
            }
        });
    }

    private static ContainerStorageException unavailable(String detail) {
        return new ContainerStorageException(
                detail == null || detail.isBlank()
                        ? "Strict repository capability is unavailable"
                        : detail
        );
    }

    private static ContainerStorageException unavailable(
            String detail,
            Throwable cause
    ) {
        return new ContainerStorageException(detail, cause);
    }
}
