package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.event.FPSMClientResetEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MinimapClientLifecycleEvents {
    public static final MinimapClientLifecycle LIFECYCLE = new MinimapClientLifecycle();

    private MinimapClientLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onClientReset(FPSMClientResetEvent event) {
        LIFECYCLE.onLogoutOrReset();
    }
}