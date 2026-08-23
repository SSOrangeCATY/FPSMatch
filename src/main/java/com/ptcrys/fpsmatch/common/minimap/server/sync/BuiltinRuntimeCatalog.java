package com.ptcrys.fpsmatch.common.minimap.server.sync;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class BuiltinRuntimeCatalog {
    private final BuiltinRuntimeResourceLoader loader;
    private final AtomicReference<BuiltinRuntimeMapRegistry> current =
            new AtomicReference<>(BuiltinRuntimeMapRegistry.builder().build());

    public BuiltinRuntimeCatalog(BuiltinRuntimeResourceLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public BuiltinRuntimeMapRegistry snapshot() {
        return current.get();
    }

    public void reload(
            Path cacheRoot,
            List<BuiltinRuntimeResourceLoader.ResourcePair> resources
    ) {
        BuiltinRuntimeMapRegistry replacement = loader.load(cacheRoot, resources);
        current.set(replacement);
    }

    public void clear() {
        current.set(BuiltinRuntimeMapRegistry.builder().build());
    }
}
