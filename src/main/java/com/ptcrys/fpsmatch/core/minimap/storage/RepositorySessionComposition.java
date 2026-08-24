package com.ptcrys.fpsmatch.core.minimap.storage;

/** Shared JVM session identity used by all strict repository adapters. */
final class RepositorySessionComposition {
    private static final RepositorySessionProvider PRODUCTION_PROVIDER =
            new RepositorySessionManager(
                    new NioNativeSessionFacade(), SessionObserver.none()
            );

    private RepositorySessionComposition() {
    }

    static RepositorySessionProvider productionProvider() {
        return PRODUCTION_PROVIDER;
    }
}
