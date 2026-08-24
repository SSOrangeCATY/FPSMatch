package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

interface NativeSessionFacade {
    record Handle(long nativeId, Path path) {
        public Handle {
            if (nativeId <= 0) {
                throw new IllegalArgumentException("Native handle ID must be positive");
            }
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        }
    }

    record ObjectState(
            byte[] identity,
            boolean plain,
            boolean secure,
            int linkCount,
            long length
    ) {
        public ObjectState {
            identity = Objects.requireNonNull(identity, "identity").clone();
            if (linkCount < 0 || length < 0) {
                throw new IllegalArgumentException("Native object state is invalid");
            }
        }

        @Override
        public byte[] identity() {
            return identity.clone();
        }
    }

    Handle openMapDirectory(Path mapDirectory) throws IOException;

    Handle openOrCreateLock(Path lockFile) throws IOException;

    /** Opens the existing lock without creating it or acquiring its lock. */
    Handle openExistingLock(Path lockFile) throws IOException;

    void lock(Handle lockHandle) throws IOException;

    ObjectState inspect(Handle handle) throws IOException;

    void force(Handle handle) throws IOException;

    void flushParent(Handle parentHandle) throws IOException;

    void unlock(Handle lockHandle) throws IOException;

    void closeLockHandle(Handle lockHandle) throws IOException;

    /** Closes an identity/proof handle through its own failure boundary. */
    void closeProofHandle(Handle proofHandle) throws IOException;

    void closeParentHandle(Handle parentHandle) throws IOException;
}
