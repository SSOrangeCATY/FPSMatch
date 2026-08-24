package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Fixed, flat strict-capability probe namespace. */
final class WindowsStrictAvailabilityProbe {
    private static final byte[] MANIFEST =
            "FPSM-AUTHORITY-PROBE-V2\n".getBytes(StandardCharsets.US_ASCII);

    private WindowsStrictAvailabilityProbe() {
    }

    static AuthorityJournalProvider.Availability probe(Path requestedDirectory) {
        Path parent = nearestExistingParent(requestedDirectory);
        if (parent == null) {
            return unsupported("No existing strict-probe parent is available");
        }
        Path lock = parent.resolve(".fpsm-authority-probe-v2.lock");
        Path manifest = parent.resolve(".fpsm-authority-probe-v2.manifest");
        Path source = parent.resolve(".fpsm-authority-probe-v2.source");
        Path linked = parent.resolve(".fpsm-authority-probe-v2.linked");
        Path occupied = parent.resolve(".fpsm-authority-probe-v2.occupied");
        try {
            WindowsAuthorityJournalNative.requirePlainDirectory(parent);
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(linked, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(occupied, LinkOption.NOFOLLOW_LINKS)) {
                return unsupported("Strict-probe transient residue is present");
            }
            ensureEmptyLock(lock);
            ensureManifest(manifest);
            WindowsAuthorityJournalNative.syncDirectory(parent);
            return new AuthorityJournalProvider.Availability(
                    AuthorityJournalProvider.Support.SUPPORTED,
                    "Win32 strict repository probe passed"
            );
        } catch (IOException | RuntimeException failure) {
            return unsupported(
                    "Win32 strict repository probe failed: "
                            + failure.getClass().getSimpleName() + ": "
                            + String.valueOf(failure.getMessage())
            );
        }
    }

    private static void ensureEmptyLock(Path lock) throws IOException {
        if (Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) {
            WindowsAuthorityJournalNative.requirePlainFile(lock);
            if (Files.size(lock) != 0) {
                throw new IOException("Strict-probe lock is not empty");
            }
            return;
        }
        Files.write(
                lock, new byte[0], StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS
        );
    }

    private static void ensureManifest(Path manifest) throws IOException {
        if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            WindowsAuthorityJournalNative.requirePlainFile(manifest);
            if (!java.util.Arrays.equals(Files.readAllBytes(manifest), MANIFEST)) {
                throw new IOException("Strict-probe manifest is not canonical");
            }
            return;
        }
        Files.write(
                manifest, MANIFEST, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS
        );
        try (var channel = java.nio.channels.FileChannel.open(
                manifest, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS
        )) {
            channel.force(true);
        }
    }

    private static Path nearestExistingParent(Path requested) {
        Path current = requested.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static AuthorityJournalProvider.Availability unsupported(String detail) {
        return new AuthorityJournalProvider.Availability(
                AuthorityJournalProvider.Support.UNSUPPORTED, detail
        );
    }
}
