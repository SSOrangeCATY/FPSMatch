package com.tacz.guns.init;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.GunPackLoader;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber
public final class CommonRegistry {
    private static boolean LOAD_COMPLETE = false;

    @SubscribeEvent
    public static void onSetupEvent(FMLCommonSetupEvent event) {
        event.enqueueWork(ModSyncedEntityData::init);
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        NetworkHandler.registerPayloadHandlers(event);
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        LOAD_COMPLETE = true;
    }

    public static boolean isLoadComplete() {
        return LOAD_COMPLETE;
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeModificationEvent event) {
        event.getTypes().forEach(type -> {
            event.add(type, ModAttributes.BULLET_RESISTANCE);
        });
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        event.addRepositorySource(GunPackLoader.INSTANCE);
    }
}
