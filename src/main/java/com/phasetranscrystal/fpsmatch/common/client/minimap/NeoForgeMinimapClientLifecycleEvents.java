package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.event.FPSMClientResetEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = FPSMatch.MODID, value = Dist.CLIENT)
public final class NeoForgeMinimapClientLifecycleEvents {
    public static final MinimapClientLifecycle LIFECYCLE = new MinimapClientLifecycle();

    private NeoForgeMinimapClientLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LIFECYCLE.onLogoutOrReset();
    }

    @SubscribeEvent
    public static void onClientReset(FPSMClientResetEvent event) {
        LIFECYCLE.onLogoutOrReset();
    }
}
