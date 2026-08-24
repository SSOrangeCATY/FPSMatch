package com.ptcrys.fpsmatch.core.minimap.storage;

import com.ptcrys.fpsmatch.core.minimap.format.CompiledMapPair;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerLimits;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerValidationException;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMap;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

final class MinimapPairValidator {
    private MinimapPairValidator() {
    }

    static Result validate(
            PublishTransaction transaction,
            byte[] sourceBytes,
            byte[] runtimeBytes
    ) {
        try (SourceMap source = SourceMapReader.read(sourceBytes);
             RuntimeMap runtime = RuntimeMapReader.read(runtimeBytes)) {
            return validate(transaction, source, runtime);
        } catch (ContainerStorageException exception) {
            throw exception;
        } catch (ContainerValidationException exception) {
            throw new ContainerStorageException("Candidate map pair failed validation", exception);
        } catch (IOException exception) {
            throw new ContainerStorageException("Unable to close candidate map pair", exception);
        } catch (RuntimeException exception) {
            throw new ContainerStorageException("Candidate map pair failed validation", exception);
        }
    }

    static Result validate(
            PublishTransaction transaction,
            Path sourcePath,
            Path runtimePath,
            RepositoryFileSystem fileSystem,
            Consumer<Path> safePathVerifier
    ) throws IOException {
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(safePathVerifier, "safePathVerifier").accept(sourcePath);
        safePathVerifier.accept(runtimePath);
        try (RepositoryFileSystem.BoundedReadChannel sourceInput =
                     fileSystem.openBoundedReadChannel(
                             sourcePath,
                             ContainerLimits.sourceHardLimits().maxCanonicalContainerBytes()
                     );
             RepositoryFileSystem.BoundedReadChannel runtimeInput =
                     fileSystem.openBoundedReadChannel(
                             runtimePath,
                             ContainerLimits.runtimeHardLimits().maxCanonicalContainerBytes()
                     );
             SourceMap source = SourceMapReader.open(
                     sourceInput.channel(), sourceInput.size()
             );
             RuntimeMap runtime = RuntimeMapReader.open(
                     runtimeInput.channel(), runtimeInput.size()
             )) {
            return validate(transaction, source, runtime);
        } catch (ContainerStorageException exception) {
            throw exception;
        } catch (ContainerValidationException exception) {
            throw new ContainerStorageException("Candidate map pair failed validation", exception);
        } catch (RuntimeException exception) {
            throw new ContainerStorageException("Candidate map pair failed validation", exception);
        }
    }

    private static Result validate(
            PublishTransaction transaction,
            SourceMap source,
            RuntimeMap runtime
    ) {
        if (!source.manifest().binding().equals(transaction.mapKey())) {
            throw new ContainerStorageException(
                    "Source map binding does not match the reservation");
        }
        if (!source.manifest().dimension().equals(transaction.dimension())) {
            throw new ContainerStorageException(
                    "Source map dimension does not match the reservation");
        }
        if (!source.manifest().documentId().equals(transaction.documentId())) {
            throw new ContainerStorageException(
                    "Source map document ID does not match the reservation");
        }
        if (source.manifest().revision() != transaction.publishRevision()
                || runtime.manifest().publishRevision() != transaction.publishRevision()) {
            throw new ContainerStorageException(
                    "Candidate revision does not match the reservation");
        }
        CompiledMapPair.verifyBinding(source, runtime);
        return new Result(
                source.sourceHash(), runtime.runtimeHash(), runtime.containerHash());
    }

    record Result(
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
    }
}
