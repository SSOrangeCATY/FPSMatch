package com.tacz.guns.client.renderer.other;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public final class LivingEntityRenderStateTracker {
    private static final Map<LivingEntityRenderState, WeakReference<LivingEntity>> ENTITIES = new WeakHashMap<>();

    private LivingEntityRenderStateTracker() {
    }

    public static void remember(LivingEntityRenderState state, LivingEntity entity) {
        ENTITIES.put(state, new WeakReference<>(entity));
    }

    public static LivingEntity get(LivingEntityRenderState state) {
        WeakReference<LivingEntity> reference = ENTITIES.get(state);
        return reference == null ? null : reference.get();
    }
}
