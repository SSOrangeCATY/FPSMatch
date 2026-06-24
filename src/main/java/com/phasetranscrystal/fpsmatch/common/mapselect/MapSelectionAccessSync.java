package com.phasetranscrystal.fpsmatch.common.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public final class MapSelectionAccessSync {
    private MapSelectionAccessSync() {
    }

    public static boolean canUseMapSelection(ServerPlayer player) {
        return FPSMUtil.hasPermissionLevel(player, 2) || FPSMConfig.Server.enableMapSelectionButtonForNonOps.get();
    }

    public static void sync(ServerPlayer player) {
        FPSMatch.sendToPlayer(player, new MapSelectionAccessS2CPacket(canUseMapSelection(player)));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
