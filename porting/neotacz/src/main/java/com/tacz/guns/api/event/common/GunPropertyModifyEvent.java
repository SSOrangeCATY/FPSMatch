package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.runtime.GunRuntimeContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public class GunPropertyModifyEvent<T> extends Event implements KubeJSGunEventPoster<GunPropertyModifyEvent<T>> {
    public enum Stage {
        SHOOT,
        PROJECTILE_CREATE,
        HIT_ENTITY,
        HIT_BLOCK,
        RELOAD,
        CYCLE
    }

    private final LivingEntity shooter;
    private final ItemStack gunItemStack;
    private final GunRuntimeContext context;
    private final String propertyId;
    private final Class<T> type;
    private final Stage stage;
    private T value;

    public GunPropertyModifyEvent(LivingEntity shooter, ItemStack gunItemStack, GunRuntimeContext context,
                                  String propertyId, Class<T> type, Stage stage, T value) {
        this.shooter = shooter;
        this.gunItemStack = gunItemStack == null ? ItemStack.EMPTY : gunItemStack;
        this.context = context;
        this.propertyId = propertyId;
        this.type = type;
        this.stage = stage;
        this.value = value;
        postEventToKubeJS(this);
    }

    public LivingEntity getShooter() {
        return shooter;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public GunRuntimeContext getContext() {
        return context;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public Class<T> getType() {
        return type;
    }

    public Stage getStage() {
        return stage;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
