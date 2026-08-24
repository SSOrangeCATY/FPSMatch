package com.ptcrys.fpsmatch.core.minimap.storage;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class MinimapRepositoryJournalAccess {
    private static final String DIRECTORY = "authority-journal";

    enum Detection {
        LEGACY,
        JOURNAL,
        UNAVAILABLE
    }

    private final MinimapAuthorityJournal model;
    private final AuthorityJournalProvider provider;

    MinimapRepositoryJournalAccess(AuthorityJournalProvider provider) {
        this.model = MinimapAuthorityJournal.defaults();
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    Detection detect(Path mapDirectory, MapKey expectedKey) throws IOException {
        Objects.requireNonNull(mapDirectory, "mapDirectory");
        Objects.requireNonNull(expectedKey, "expectedKey");
        Path directory = journalDirectory(mapDirectory);
        AuthorityJournalProvider.Availability availability =
                provider.availability(directory);
        if (!availability.supported()) {
            return Detection.UNAVAILABLE;
        }
        try (AuthorityJournalProvider.JournalHandle handle =
                     provider.open(directory, model.config())) {
            AuthorityJournalProvider.Inspection inspection = handle.inspect();
            if (inspection.detection() == AuthorityJournalProvider.Detection.ABSENT) {
                return Detection.LEGACY;
            }
            if (inspection.detection() == AuthorityJournalProvider.Detection.UNAVAILABLE) {
                return Detection.UNAVAILABLE;
            }
            MinimapAuthorityJournal.Snapshot snapshot = model.parse(inspection);
            return snapshot.active()
                    && snapshot.journalInstance().equals(journalInstance(expectedKey))
                    ? Detection.JOURNAL : Detection.UNAVAILABLE;
        } catch (IOException | ContainerStorageException exception) {
            return Detection.UNAVAILABLE;
        }
    }

    boolean isActive(Path mapDirectory) throws IOException {
        return snapshotJournal(journalDirectory(mapDirectory))
                .map(MinimapAuthorityJournal.Snapshot::active)
                .orElse(false);
    }

    void activate(Path mapDirectory, MapKey key) throws IOException {
        Path directory = journalDirectory(mapDirectory);
        AuthorityJournalProvider.Availability availability =
                provider.availability(directory);
        if (!availability.supported()) {
            throw new IOException(
                    "Strict authority journal is unsupported: " + availability.detail());
        }
        try (AuthorityJournalProvider.JournalHandle handle =
                     provider.open(directory, model.config())) {
            AuthorityJournalProvider.Inspection inspection = handle.inspect();
            if (inspection.detection() == AuthorityJournalProvider.Detection.UNAVAILABLE) {
                throw new IOException(
                        "Strict authority journal is unavailable: " + inspection.detail());
            }
            if (inspection.detection() == AuthorityJournalProvider.Detection.PRESENT) {
                MinimapAuthorityJournal.Snapshot snapshot;
                try {
                    snapshot = model.parse(inspection);
                } catch (ContainerStorageException failure) {
                    throw new IOException("Strict authority journal is invalid", failure);
                }
                if (!snapshot.active()
                        || !snapshot.journalInstance().equals(journalInstance(key))) {
                    throw new IOException("Strict authority journal has no active head");
                }
                return;
            }
            String instance = journalInstance(key);
            model.activate(
                    handle,
                    instance,
                    "activate-" + UUID.randomUUID(),
                    identity(key)
            );
        }
    }

    Optional<MinimapAuthorityJournal.Snapshot> snapshot(Path mapDirectory)
            throws IOException {
        return snapshotJournal(journalDirectory(mapDirectory));
    }

    private Optional<MinimapAuthorityJournal.Snapshot> snapshotJournal(
            Path journalDirectory
    ) throws IOException {
        AuthorityJournalProvider.Availability availability =
                provider.availability(journalDirectory);
        if (!availability.supported()) {
            throw new IOException(
                    "Strict authority journal is unsupported: " + availability.detail());
        }
        try (AuthorityJournalProvider.JournalHandle handle =
                     provider.open(journalDirectory, model.config())) {
            AuthorityJournalProvider.Inspection inspection = handle.inspect();
            if (inspection.detection() == AuthorityJournalProvider.Detection.UNAVAILABLE) {
                throw new IOException(
                        "Strict authority journal is unavailable: " + inspection.detail());
            }
            if (inspection.detection() == AuthorityJournalProvider.Detection.ABSENT) {
                return Optional.empty();
            }
            try {
                return Optional.of(model.parse(inspection));
            } catch (ContainerStorageException failure) {
                throw new IOException("Strict authority journal is invalid", failure);
            }
        }
    }

    private static Path journalDirectory(Path mapDirectory) {
        return mapDirectory.resolve(DIRECTORY);
    }

    private static byte[] identity(MapKey key) {
        return (key.gameType() + "\n" + key.mapName())
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String journalInstance(MapKey key) {
        // Activation identity can age out of the ring; journalInstance is retained by every entry.
        return "map-" + AuthorityJournalCodec.digestHex(identity(key));
    }
}
