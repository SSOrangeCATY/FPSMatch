package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface RepositorySessionProvider {
    SessionLease open(Path mapDirectory) throws IOException;

    interface SessionLease extends AutoCloseable {
        OperationLease openOperation() throws IOException;

        @Override
        void close() throws IOException;
    }

    @FunctionalInterface
    interface OperationLease extends AutoCloseable {
        @Override
        void close() throws IOException;
    }
}
