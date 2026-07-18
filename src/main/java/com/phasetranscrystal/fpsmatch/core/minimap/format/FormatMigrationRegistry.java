package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FormatMigrationRegistry {
    private final EnumMap<ContainerKind, Map<MinimapFormatVersion, RegisteredMigration>> migrations =
            new EnumMap<>(ContainerKind.class);

    public FormatMigrationRegistry() {
        for (ContainerKind kind : ContainerKind.values()) {
            migrations.put(kind, new HashMap<>());
        }
    }

    public void register(
            ContainerKind kind,
            MinimapFormatVersion from,
            MinimapFormatVersion to,
            Migration migration
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(migration, "migration");
        if (compare(from, to) >= 0) {
            throw new IllegalArgumentException("Format migrations must be strictly one-way");
        }
        Map<MinimapFormatVersion, RegisteredMigration> bySource = migrations.get(kind);
        if (bySource.putIfAbsent(from, new RegisteredMigration(to, migration)) != null) {
            throw new IllegalArgumentException("A migration is already registered for " + from);
        }
    }

    public void registerSource(
            MinimapFormatVersion from,
            MinimapFormatVersion to,
            Migration migration
    ) {
        register(ContainerKind.SOURCE, from, to, migration);
    }

    public void registerRuntime(
            MinimapFormatVersion from,
            MinimapFormatVersion to,
            Migration migration
    ) {
        register(ContainerKind.RUNTIME, from, to, migration);
    }

    public Snapshot migrate(Snapshot input, MinimapFormatVersion target) {
        Objects.requireNonNull(input, "input");
        return migrate(input.kind(), input, target);
    }

    public Snapshot migrate(
            ContainerKind kind,
            Snapshot input,
            MinimapFormatVersion target
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(target, "target");
        if (input.kind() != kind) {
            throw new ContainerValidationException("Migration container kind does not match the snapshot");
        }
        Snapshot current = input;
        if (current.version().equals(target)) {
            return current.copy();
        }
        List<MinimapFormatVersion> visited = new ArrayList<>();
        for (int step = 0; step <= 128; step++) {
            if (current.version().equals(target)) {
                return current.copy();
            }
            if (visited.contains(current.version())) {
                throw new ContainerValidationException("Format migration cycle detected");
            }
            visited.add(current.version());
            RegisteredMigration registered = migrations.get(kind).get(current.version());
            if (registered == null || compare(registered.to(), target) > 0) {
                throw new ContainerValidationException(
                        "No explicit migration from " + current.version() + " to " + target
                );
            }
            Snapshot before = current.copy();
            Snapshot next;
            try {
                next = Objects.requireNonNull(registered.migration().apply(before), "migration result");
            } catch (RuntimeException exception) {
                throw new ContainerValidationException(
                        "Format migration failed at " + current.version(), exception
                );
            }
            if (next.kind() != kind || !next.version().equals(registered.to())) {
                throw new ContainerValidationException("Migration returned an unexpected version or kind");
            }
            current = next.copy();
        }
        throw new ContainerValidationException("Format migration chain is too long");
    }

    private static int compare(MinimapFormatVersion left, MinimapFormatVersion right) {
        int major = Integer.compare(left.major(), right.major());
        return major != 0 ? major : Integer.compare(left.minor(), right.minor());
    }

    public enum ContainerKind {
        SOURCE,
        RUNTIME
    }

    @FunctionalInterface
    public interface Migration {
        Snapshot apply(Snapshot input);
    }

    private record RegisteredMigration(MinimapFormatVersion to, Migration migration) {
    }

    public record Snapshot(
            ContainerKind kind,
            MinimapFormatVersion version,
            Map<ContainerPath, byte[]> entries
    ) {
        public Snapshot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(entries, "entries");
            LinkedHashMap<ContainerPath, byte[]> copy = new LinkedHashMap<>();
            for (Map.Entry<ContainerPath, byte[]> entry : entries.entrySet()) {
                copy.put(
                        Objects.requireNonNull(entry.getKey(), "entry path"),
                        Objects.requireNonNull(entry.getValue(), "entry bytes").clone()
                );
            }
            entries = Map.copyOf(copy);
        }

        public byte[] entryBytes(ContainerPath path) {
            byte[] value = entries.get(Objects.requireNonNull(path, "path"));
            if (value == null) {
                throw new ContainerValidationException("Migration entry is missing: " + path);
            }
            return value.clone();
        }

        @Override
        public Map<ContainerPath, byte[]> entries() {
            LinkedHashMap<ContainerPath, byte[]> copy = new LinkedHashMap<>();
            entries.forEach((path, bytes) -> copy.put(path, bytes.clone()));
            return java.util.Collections.unmodifiableMap(copy);
        }

        public Snapshot copy() {
            return new Snapshot(kind, version, entries);
        }
    }
}
