package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMEventHook {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerPickupItem(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.PickupItemEvent pickupItemEvent = new FPSMapEvent.PlayerEvent.PickupItemEvent(map, player, event.getItemEntity(), event.getItemEntity().getItem());
                if (NeoForge.EVENT_BUS.post(pickupItemEvent).isCanceled()) {
                    event.setCanPickup(TriState.FALSE);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTossItemEvent(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.TossItemEvent tossItemEvent = new FPSMapEvent.PlayerEvent.TossItemEvent(map, player, event.getEntity());
                if (NeoForge.EVENT_BUS.post(tossItemEvent).isCanceled()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChatEvent(ServerChatEvent event) {
        Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());
        if (opt.isPresent()) {
            BaseMap map = opt.get();
            FPSMapEvent.PlayerEvent.ChatEvent chatEvent = new FPSMapEvent.PlayerEvent.ChatEvent(map, event.getPlayer(), event.getMessage().getString());
            if (NeoForge.EVENT_BUS.post(chatEvent).isCanceled()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            opt.ifPresentOrElse(map -> {
                FPSMapEvent.PlayerEvent.LoggedInEvent loggedInEvent = new FPSMapEvent.PlayerEvent.LoggedInEvent(map, player);
                NeoForge.EVENT_BUS.post(loggedInEvent);
            }, () -> {
                if (FPSMConfig.common.autoAdventureMode.get() && !player.isCreative()) {
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.ADVENTURE);
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            boolean leave = true;
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.LoggedOutEvent loggedOutEvent = new FPSMapEvent.PlayerEvent.LoggedOutEvent(map, player);
                if (NeoForge.EVENT_BUS.post(loggedOutEvent).isCanceled()) {
                    leave = false;
                }
            }

            if (leave) {
                FPSMCore.checkAndLeaveTeam(player);
            }
        }
    }

    @SubscribeEvent
    public static void onMapPlayerLoggedInEvent(FPSMapEvent.PlayerEvent.LoggedInEvent event) {
        BaseMap map = event.getMap();
        ServerPlayer player = event.getPlayer();
        map.getMapTeams().getTeamByPlayer(player)
                .flatMap(team -> team.getPlayerData(player.getUUID()))
                .ifPresent(playerData -> {
                    playerData.setLiving(false);
                    player.setGameMode(GameType.SPECTATOR);
                });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerRespawnEvent(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map -> {
            if (!map.isStart()) {
                return;
            }
            map.getMapTeams().getPlayerData(player).ifPresent(data -> data.setLiving(true));
            map.teleportPlayerToReSpawnPoint(player);
        });
    }
}
