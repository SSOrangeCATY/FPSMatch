package com.tacz.guns.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber
public class SyncBaseTimestamp {
    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && !event.getLevel().isClientSide()) {
            NetworkHandler.sendToClientPlayer(new ServerMessageSyncBaseTimestamp(), player);
        }
    }
}
