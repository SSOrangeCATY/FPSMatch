package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FPSMEventHook {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.PickupItemEvent pickupItemEvent = new FPSMapEvent.PlayerEvent.PickupItemEvent(map, player, event.getOriginalEntity(), event.getStack());
                if (MinecraftForge.EVENT_BUS.post(pickupItemEvent)) {
                    event.setCanceled(true);
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
                if (MinecraftForge.EVENT_BUS.post(tossItemEvent)) {
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
            if (MinecraftForge.EVENT_BUS.post(chatEvent)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 玩家登录事件处理
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            opt.ifPresentOrElse(map -> {
                FPSMapEvent.PlayerEvent.LoggedInEvent loggedInEvent = new FPSMapEvent.PlayerEvent.LoggedInEvent(map, player);
                MinecraftForge.EVENT_BUS.post(loggedInEvent);
            }, () -> {
                if (FPSMConfig.common.autoAdventureMode.get()) {
                    if (!player.isCreative()) {
                        player.heal(player.getMaxHealth());
                        player.setGameMode(GameType.ADVENTURE);
                    }
                }
            });
        }
    }

    /**
     * 玩家登出事件处理
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            boolean leave = true;
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.LoggedOutEvent loggedOutEvent = new FPSMapEvent.PlayerEvent.LoggedOutEvent(map, player);
                if (MinecraftForge.EVENT_BUS.post(loggedOutEvent)) {
                    leave = false;
                }
            }

            if (leave) {
                FPSMCore.checkAndLeaveTeam(player);
            }
        }
    }

    /**
     * 玩家受伤事件处理
     *
     * @param event 玩家受伤事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer hurt) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(hurt);
            if (opt.isEmpty()) return;
            BaseMap map = opt.get();
            if (map.isStart()) {
                FPSMapEvent.PlayerEvent.HurtEvent hurtEvent = new FPSMapEvent.PlayerEvent.HurtEvent(map, hurt, event.getSource(), event.getAmount());

                if (MinecraftForge.EVENT_BUS.post(hurtEvent)) {
                    event.setCanceled(true);
                    return;
                }

                event.setAmount(hurtEvent.getAmount());

                if (event.getAmount() <= 0){
                    event.setCanceled(true);
                    return;
                }

                map.getAttackerFromDamageSource(event.getSource()).ifPresent(attacker->{
                    if (!map.isValidAttack(attacker, hurt)) return;
                    map.getMapTeams().addHurtData(attacker, hurt, event.getAmount());
                });
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isEmpty()) return;

            BaseMap map = opt.get();
            if (map.isStart()) {
                FPSMapEvent.PlayerEvent.DeathEvent deathEvent = new FPSMapEvent.PlayerEvent.DeathEvent(map, player, event.getSource());
                if (MinecraftForge.EVENT_BUS.post(deathEvent)) {
                    event.setCanceled(true);
                }

                MapTeams mapTeams = map.getMapTeams();
                mapTeams.getPlayerData(player).ifPresent(data->{
                    data.setLiving(false);
                    data.addDeath();
                });

                Optional<ServerPlayer> optional = deathEvent.getAttacker();
                if (optional.isPresent()) {
                    ServerPlayer killer = optional.get();

                    mapTeams.getPlayerData(killer).ifPresent(PlayerData::addKill);

                    FPSMUtil.calculateAssistPlayer(map,player,map.getMinAssistDamageRatio()).ifPresent(assistData -> {
                        if (!killer.getUUID().equals(assistData.getOwner())) {
                            assistData.addAssist();
                        }
                    });

                    FPSMapEvent.PlayerEvent.KillEvent killEvent = new FPSMapEvent.PlayerEvent.KillEvent(map,killer,player,event.getSource());
                    MinecraftForge.EVENT_BUS.post(killEvent);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerKillEvent(EntityKillByGunEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getKilledEntity() instanceof ServerPlayer deadPlayer)) return;
        Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(deadPlayer);
        if (mapOpt.isEmpty()) return;
        BaseMap map = mapOpt.get();
        if (!(event.getAttacker() instanceof ServerPlayer attacker) || !IGun.mainHandHoldGun(attacker)) return;
        if (!FPSMCore.getInstance().getMapByPlayer(attacker).map(m -> m.equals(map)).orElse(false)) return;

        if(event.isHeadShot()){
            map.getMapTeams().getPlayerData(attacker).ifPresent(PlayerData::addHeadshotKill);
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

}
