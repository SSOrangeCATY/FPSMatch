package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedMapPairSnapshotChannelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void closesSourceAndRuntimeChannelsWhenSnapshotCloses() throws Exception {
        ValidPair pair = validPair();
        Path sourceFile = temporaryDirectory.resolve("valid-source.fpsmap");
        Path runtimeFile = temporaryDirectory.resolve("valid-runtime.fpsmapc");
        Files.write(sourceFile, pair.sourceBytes());
        Files.write(runtimeFile, pair.runtimeBytes());

        FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
        FileChannel runtimeChannel = FileChannel.open(runtimeFile, StandardOpenOption.READ);
        try {
            try (CommittedMapPairSnapshot ignored = pair.open(
                    sourceChannel, runtimeChannel
            )) {
                assertTrue(sourceChannel.isOpen());
                assertTrue(runtimeChannel.isOpen());
            }
            assertFalse(sourceChannel.isOpen());
            assertFalse(runtimeChannel.isOpen());
        } finally {
            sourceChannel.close();
            runtimeChannel.close();
        }
    }

    @Test
    void closesRuntimeChannelWhenSourceValidationFailsBeforeRuntimeIsOpened() throws Exception {
        Path sourceFile = temporaryDirectory.resolve("source.fpsmap");
        Path runtimeFile = temporaryDirectory.resolve("runtime.fpsmapc");
        Files.write(sourceFile, new byte[]{0});
        Files.write(runtimeFile, new byte[]{0});

        FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
        FileChannel runtimeChannel = FileChannel.open(runtimeFile, StandardOpenOption.READ);
        try {
            Sha256 placeholderHash = Sha256Digest.of(new byte[0]);
            assertThrows(
                    ContainerValidationException.class,
                    () -> CommittedMapPairSnapshot.open(
                            sourceChannel, 1,
                            runtimeChannel, 1,
                            new MapKey("fpsmatch:test", "Test Map"),
                            7,
                            placeholderHash,
                            placeholderHash,
                            placeholderHash
                    )
            );
            assertFalse(sourceChannel.isOpen(), "source channel must be closed on failure");
            assertFalse(runtimeChannel.isOpen(), "runtime channel must be closed on failure");
        } finally {
            sourceChannel.close();
            runtimeChannel.close();
        }
    }

    private static ValidPair validPair() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(
                MinimapContainerFixtures.sourceDefinition()
        );
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair compiled = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:snapshot-channel-test"),
                                    MinimapFormatContract.CURRENT
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            );
            return new ValidPair(
                    sourceBytes,
                    compiled.runtimeBytes(),
                    source.manifest().binding(),
                    source.manifest().revision(),
                    compiled.sourceHash(),
                    compiled.runtimeHash(),
                    compiled.runtimeContainerHash()
            );
        }
    }

    private record ValidPair(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            MapKey binding,
            long revision,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        private CommittedMapPairSnapshot open(
                FileChannel sourceChannel,
                FileChannel runtimeChannel
        ) {
            return CommittedMapPairSnapshot.open(
                    sourceChannel, sourceBytes.length,
                    runtimeChannel, runtimeBytes.length,
                    binding,
                    revision,
                    sourceHash,
                    runtimeHash,
                    runtimeContainerHash
            );
        }
    }
}
