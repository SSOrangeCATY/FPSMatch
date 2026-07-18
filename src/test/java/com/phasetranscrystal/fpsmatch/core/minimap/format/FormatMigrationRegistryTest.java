package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatMigrationRegistryTest {
    @Test
    void appliesOnlyExplicitOneWayMigrationsAndKeepsSnapshotsImmutable() {
        FormatMigrationRegistry registry = new FormatMigrationRegistry();
        MinimapFormatVersion old = new MinimapFormatVersion(0, 9);
        FormatMigrationRegistry.Snapshot input = new FormatMigrationRegistry.Snapshot(
                FormatMigrationRegistry.ContainerKind.SOURCE,
                old,
                Map.of(path("document.json"), bytes("old"))
        );
        registry.register(
                FormatMigrationRegistry.ContainerKind.SOURCE,
                old,
                MinimapFormatContract.CURRENT,
                snapshot -> new FormatMigrationRegistry.Snapshot(
                        snapshot.kind(), MinimapFormatContract.CURRENT,
                        Map.of(path("document.json"), bytes("new"))
                )
        );

        FormatMigrationRegistry.Snapshot migrated = registry.migrate(
                input, MinimapFormatContract.CURRENT
        );
        assertEquals(MinimapFormatContract.CURRENT, migrated.version());
        assertArrayEquals(bytes("old"), input.entryBytes(path("document.json")));
        assertArrayEquals(bytes("new"), migrated.entryBytes(path("document.json")));
        migrated.entries().get(path("document.json"))[0] = 'x';
        assertArrayEquals(bytes("new"), migrated.entryBytes(path("document.json")));
        assertThrows(UnsupportedOperationException.class,
                () -> migrated.entries().put(path("x"), bytes("x")));
    }

    @Test
    void rejectsUnknownMajorDuplicateEdgesReverseEdgesAndFailedMutation() {
        FormatMigrationRegistry registry = new FormatMigrationRegistry();
        MinimapFormatVersion old = new MinimapFormatVersion(0, 9);
        registry.register(
                FormatMigrationRegistry.ContainerKind.SOURCE, old,
                MinimapFormatContract.CURRENT, snapshot -> snapshot
        );
        assertThrows(IllegalArgumentException.class, () -> registry.register(
                FormatMigrationRegistry.ContainerKind.SOURCE, old,
                MinimapFormatContract.CURRENT, snapshot -> snapshot
        ));
        assertThrows(IllegalArgumentException.class, () -> registry.register(
                FormatMigrationRegistry.ContainerKind.SOURCE,
                MinimapFormatContract.CURRENT, old, snapshot -> snapshot
        ));
        assertThrows(ContainerValidationException.class, () -> registry.migrate(
                new FormatMigrationRegistry.Snapshot(
                        FormatMigrationRegistry.ContainerKind.SOURCE,
                        new MinimapFormatVersion(2, 0), Map.of()
                ), MinimapFormatContract.CURRENT
        ));

        FormatMigrationRegistry failing = new FormatMigrationRegistry();
        FormatMigrationRegistry.Snapshot input = new FormatMigrationRegistry.Snapshot(
                FormatMigrationRegistry.ContainerKind.SOURCE, old,
                Map.of(path("document.json"), bytes("original"))
        );
        failing.register(
                FormatMigrationRegistry.ContainerKind.SOURCE, old,
                MinimapFormatContract.CURRENT,
                snapshot -> {
                    snapshot.entryBytes(path("document.json"))[0] = 'x';
                    throw new IllegalStateException("fail");
                }
        );
        assertThrows(ContainerValidationException.class,
                () -> failing.migrate(input, MinimapFormatContract.CURRENT));
        assertArrayEquals(bytes("original"), input.entryBytes(path("document.json")));
    }

    @Test
    void sourceReaderUsesAnExplicitRegisteredMajorMigration() throws Exception {
        com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition base =
                MinimapContainerFixtures.sourceDefinition();
        com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest inputManifest =
                base.manifest();
        MinimapFormatVersion old = new MinimapFormatVersion(0, 9);
        com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest oldManifest =
                new com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest(
                        old, inputManifest.documentId(), inputManifest.binding(),
                        inputManifest.revision(), inputManifest.dimension(),
                        inputManifest.provenance(), inputManifest.tileEdge(), List.of()
                );
        byte[] container = SourceMapWriter.write(
                new com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition(
                        oldManifest, base.document(), base.regions(), base.connections(), base.styles()
                )
        );
        assertThrows(ContainerValidationException.class, () -> SourceMapReader.read(container));

        FormatMigrationRegistry registry = new FormatMigrationRegistry();
        registry.registerSource(old, MinimapFormatContract.CURRENT, snapshot -> {
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>(snapshot.entries());
            String manifest = new String(
                    entries.get(MinimapContainerLayout.SOURCE_MANIFEST), StandardCharsets.UTF_8
            ).replace("\"formatVersion\":\"0.9\"", "\"formatVersion\":\"1.0\"");
            entries.put(MinimapContainerLayout.SOURCE_MANIFEST, bytes(manifest));
            return new FormatMigrationRegistry.Snapshot(
                    snapshot.kind(), MinimapFormatContract.CURRENT, entries
            );
        });

        try (SourceMap migrated = SourceMapReader.read(container, registry)) {
            assertEquals(MinimapFormatContract.CURRENT, migrated.manifest().formatVersion());
        }
    }

    @Test
    void runtimeReaderUsesAnExplicitRegisteredMajorMigration() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        byte[] runtimeBytes;
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            runtimeBytes = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                                    com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse("fpsmatch:test"),
                                    MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            ).runtimeBytes();
        }
        Map<ContainerPath, byte[]> oldEntries;
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                runtimeBytes, ContainerLimits.runtimeHardLimits()
        )) {
            oldEntries = new LinkedHashMap<>(archive.entries());
        }
        String oldManifest = new String(
                oldEntries.get(MinimapContainerLayout.RUNTIME_MANIFEST), StandardCharsets.UTF_8
        ).replace("\"formatVersion\":\"1.0\"", "\"formatVersion\":\"0.9\"");
        oldEntries.put(MinimapContainerLayout.RUNTIME_MANIFEST, bytes(oldManifest));
        byte[] oldContainer = CanonicalZipWriter.write(
                oldEntries, ContainerLimits.runtimeHardLimits()
        );
        assertThrows(ContainerValidationException.class,
                () -> RuntimeMapReader.read(oldContainer));

        MinimapFormatVersion old = new MinimapFormatVersion(0, 9);
        FormatMigrationRegistry registry = new FormatMigrationRegistry();
        registry.registerRuntime(old, MinimapFormatContract.CURRENT, snapshot -> {
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>(snapshot.entries());
            String manifest = new String(
                    entries.get(MinimapContainerLayout.RUNTIME_MANIFEST), StandardCharsets.UTF_8
            ).replace("\"formatVersion\":\"0.9\"", "\"formatVersion\":\"1.0\"");
            entries.put(MinimapContainerLayout.RUNTIME_MANIFEST, bytes(manifest));
            return new FormatMigrationRegistry.Snapshot(
                    snapshot.kind(), MinimapFormatContract.CURRENT, entries
            );
        });

        try (RuntimeMap migrated = RuntimeMapReader.read(oldContainer, registry)) {
            assertEquals(MinimapFormatContract.CURRENT, migrated.manifest().formatVersion());
        }
    }

    private static ContainerPath path(String value) {
        return ContainerPath.parse(value);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
