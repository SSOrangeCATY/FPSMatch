package com.phasetranscrystal.fpsmatch.common.client;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.common.client.event.FPSMClientResetEvent;
import com.phasetranscrystal.fpsmatch.common.client.key.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = FPSMatch.MODID)
public class FPSMClient {
    private static final FPSMClientGlobalData DATA = new FPSMClientGlobalData();

    public static FPSMClientGlobalData getGlobalData(){
        return DATA;
    }

    @SubscribeEvent
    public static void onClientSetup(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.register(CustomHudKey.KEY);
        event.register(SwitchPreviousItemKey.KEY);
        //event.register(DebugMVPHudKey.CUSTOM_TAB_KEY);
    }

    public static void reset() {
        DATA.reset();
        MinecraftForge.EVENT_BUS.post(new FPSMClientResetEvent());
    }
}
