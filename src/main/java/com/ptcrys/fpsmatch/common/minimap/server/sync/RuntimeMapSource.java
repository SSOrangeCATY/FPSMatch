package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface RuntimeMapSource extends AutoCloseable {
    WireIdentity.RuntimeIdentity identity();

    byte[] manifestBytes();

    Optional<RuntimeEntryDescriptor> descriptor(ContainerPath path);

    InputStream openEntry(ContainerPath path) throws IOException;

    @Override
    void close() throws IOException;
}
