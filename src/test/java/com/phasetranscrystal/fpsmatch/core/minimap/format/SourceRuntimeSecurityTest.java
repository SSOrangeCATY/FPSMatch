package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceRuntimeSecurityTest {
    @TempDir
    Path tempDirectory;

    @Test
    void preservesRootAuthorityExtensionsAndFutureMinorVersion() throws Exception {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        SourceManifest original = base.manifest();
        SourceManifest future = new SourceManifest(
                new MinimapFormatVersion(original.formatVersion().major(), 1),
                original.documentId(), original.binding(), original.revision(),
                original.dimension(), original.provenance(), original.tileEdge(), List.of()
        );
        MinimapDefinition definition = new MinimapDefinition(
                future, base.document(), base.regions(), base.connections(), base.styles()
        );
        JsonObject extension = new JsonObject();
        JsonObject payload = new JsonObject();
        payload.addProperty("futureField", 7);
        extension.add("example_mod", payload);

        byte[] bytes = SourceMapWriter.write(new SourceMapDraft(
                definition,
                List.of(),
                Map.of(MinimapContainerLayout.SOURCE_DOCUMENT, PreservedExtensions.of(extension))
        ));
        try (SourceMap source = SourceMapReader.read(bytes)) {
            assertEquals(new MinimapFormatVersion(1, 1), source.manifest().formatVersion());
            assertEquals(Set.of("example_mod"), source.authorityExtensions()
                    .get(MinimapContainerLayout.SOURCE_DOCUMENT).namespaces());
        }
    }

    @Test
    void rejectsRuntimeCompilerEntriesOutsideThePositiveAllowlist() throws Exception {
        byte[] bytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(bytes)) {
            RuntimeCompileRequest request = new RuntimeCompileRequest(
                    source.manifest().revision(),
                    new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                            com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse("fpsmatch:test"),
                            com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract.CURRENT
                    ),
                    List.of(new CanonicalZipWriter.Entry(
                            com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("document.json"),
                            new byte[0]
                    ))
            );
            assertThrows(ContainerValidationException.class,
                    () -> RuntimeMapCompiler.compile(source, request));
        }
    }

    @Test
    void aggregateContainerHashesRejectSameLengthBackingFileChanges() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        Path sourceFile = tempDirectory.resolve("mutable.fpsmap");
        Files.write(sourceFile, sourceBytes);
        FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
        try (SourceMap source = SourceMapReader.open(
                sourceChannel, Files.size(sourceFile)
        )) {
            replaceFirstEntryByte(sourceFile, "connections.json");
            assertThrows(ContainerValidationException.class, source::sourceHash);
        }

        byte[] cleanSource = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        byte[] runtimeBytes;
        try (SourceMap source = SourceMapReader.read(cleanSource)) {
            runtimeBytes = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                                    com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                            "fpsmatch:test"
                                    ),
                                    com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            ).runtimeBytes();
        }
        Path runtimeFile = tempDirectory.resolve("mutable.fpsmapc");
        Files.write(runtimeFile, runtimeBytes);
        FileChannel runtimeChannel = FileChannel.open(runtimeFile, StandardOpenOption.READ);
        try (SourceMap source = SourceMapReader.read(cleanSource);
             RuntimeMap runtime = RuntimeMapReader.open(runtimeChannel, Files.size(runtimeFile))) {
            replaceFirstEntryByte(runtimeFile, "connections.json");
            assertThrows(ContainerValidationException.class,
                    () -> CompiledMapPair.verifyBinding(source, runtime));
            assertThrows(ContainerValidationException.class, runtime::containerHash);
        }
    }

    @Test
    void preservesRuntimeAuthorityExtensionPresenceAndNamespaces() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        byte[] runtimeBytes;
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            runtimeBytes = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile(
                                    com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                            "fpsmatch:test"
                                    ),
                                    com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            ).runtimeBytes();
        }

        byte[] withExtension = addRuntimeRegionsExtension(runtimeBytes);
        try (RuntimeMap runtime = RuntimeMapReader.read(withExtension)) {
            assertEquals(Set.of("example_mod"), runtime.authorityExtensions()
                    .get(MinimapContainerLayout.RUNTIME_REGIONS).namespaces());
            assertFalse(runtime.authorityExtensions()
                    .get(MinimapContainerLayout.RUNTIME_MANIFEST).isPresent());
            assertFalse(runtime.authorityExtensions()
                    .get(MinimapContainerLayout.CONNECTIONS).isPresent());
            assertFalse(runtime.authorityExtensions()
                    .get(MinimapContainerLayout.RUNTIME_STYLES).isPresent());
        }
    }

    private static byte[] addRuntimeRegionsExtension(byte[] runtimeBytes) {
        CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                runtimeBytes, ContainerLimits.runtimeHardLimits()
        );
        try {
            Map<com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath, byte[]> entries =
                    new LinkedHashMap<>(archive.entries());
            JsonObject regions = StrictJsonParser.parse(
                    entries.get(MinimapContainerLayout.RUNTIME_REGIONS)
            ).getAsJsonObject();
            JsonObject extension = new JsonObject();
            extension.add("example_mod", new JsonObject());
            regions.add("extensions", extension);
            byte[] regionsBytes = JcsCanonicalizer.canonicalize(regions);
            entries.put(MinimapContainerLayout.RUNTIME_REGIONS, regionsBytes);

            JsonObject manifest = StrictJsonParser.parse(
                    entries.get(MinimapContainerLayout.RUNTIME_MANIFEST)
            ).getAsJsonObject();
            JsonArray descriptors = manifest.getAsJsonArray("entries");
            for (var descriptor : descriptors) {
                JsonObject object = descriptor.getAsJsonObject();
                if (object.get("path").getAsString()
                        .equals(MinimapContainerLayout.RUNTIME_REGIONS.value())) {
                    object.add("byteLength", new JsonPrimitive(Integer.toString(regionsBytes.length)));
                    object.add("sha256", new JsonPrimitive(Sha256Digest.of(regionsBytes).value()));
                }
            }
            entries.put(MinimapContainerLayout.RUNTIME_MANIFEST,
                    JcsCanonicalizer.canonicalize(manifest));
            return CanonicalZipWriter.write(entries, ContainerLimits.runtimeHardLimits());
        } finally {
            try {
                archive.close();
            } catch (java.io.IOException exception) {
                throw new AssertionError("Failed to close runtime fixture", exception);
            }
        }
    }

    private static void replaceFirstEntryByte(Path file, String firstPath) throws Exception {
        try (FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
            long payloadOffset = 30L + firstPath.length();
            mutator.position(payloadOffset);
            mutator.write(ByteBuffer.wrap(new byte[]{'!'}));
            mutator.force(true);
        }
    }
}
