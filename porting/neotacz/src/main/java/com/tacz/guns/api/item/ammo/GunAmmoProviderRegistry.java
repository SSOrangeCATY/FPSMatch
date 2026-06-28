package com.tacz.guns.api.item.ammo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GunAmmoProviderRegistry {
    private static final List<IGunAmmoProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private GunAmmoProviderRegistry() {
    }

    public static AutoCloseable register(IGunAmmoProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        PROVIDERS.add(provider);
        return () -> unregister(provider);
    }

    public static boolean unregister(IGunAmmoProvider provider) {
        return PROVIDERS.remove(provider);
    }

    public static GunAmmoTransaction query(GunAmmoRequest request) {
        return dispatch(request);
    }

    public static GunAmmoTransaction consume(GunAmmoRequest request) {
        return dispatch(request);
    }

    public static GunAmmoTransaction supply(GunAmmoRequest request) {
        return dispatch(request);
    }

    public static void clearForTests() {
        PROVIDERS.clear();
    }

    private static GunAmmoTransaction dispatch(GunAmmoRequest request) {
        for (IGunAmmoProvider provider : PROVIDERS) {
            GunAmmoTransaction transaction = provider.handle(request);
            if (transaction != null && transaction.handled()) {
                return transaction;
            }
        }
        return GunAmmoTransaction.notHandled();
    }
}
