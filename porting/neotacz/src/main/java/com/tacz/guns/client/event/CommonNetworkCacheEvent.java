package com.tacz.guns.client.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class CommonNetworkCacheEvent {
    @SubscribeEvent
    public static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (event.getConnection() == null || event.getConnection().isMemoryConnection()) {
            return;
        }
        CommonAssetsManager.clearInstance();
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }
}
