package com.phasetranscrystal.fpsmatch.client;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.key.OpenShopKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = FPSMatch.MODID)
public class FPSMClient {
    @SubscribeEvent
    public static void onClientSetup(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.register(OpenShopKey.OPEN_SHOP_KEY);
    }
}
