package com.tacz.guns.api.item.runtime;

import com.tacz.guns.api.DefaultAssets;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicLong;

public record GunRuntimeContext(
        long shotId,
        Identifier gunId,
        Identifier ammoId,
        String ammoSlotId,
        String runtimeItemId
) {
    private static final AtomicLong SHOT_SEQUENCE = new AtomicLong();

    public GunRuntimeContext {
        gunId = gunId == null ? DefaultAssets.EMPTY_GUN_ID : gunId;
        ammoId = ammoId == null ? DefaultAssets.EMPTY_AMMO_ID : ammoId;
        ammoSlotId = ammoSlotId == null || ammoSlotId.isBlank() ? "main" : ammoSlotId;
        runtimeItemId = runtimeItemId == null ? "" : runtimeItemId;
    }

    public static long nextShotId() {
        return SHOT_SEQUENCE.incrementAndGet();
    }

    public static GunRuntimeContext none(Identifier gunId, Identifier ammoId) {
        return new GunRuntimeContext(0L, gunId, ammoId, "main", "");
    }
}
