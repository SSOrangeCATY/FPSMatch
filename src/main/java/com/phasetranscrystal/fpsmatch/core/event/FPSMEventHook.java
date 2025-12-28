package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID ,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FPSMEventHook {

    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.PickupItemEvent pickupItemEvent = new FPSMapEvent.PlayerEvent.PickupItemEvent(map,player,event.getOriginalEntity(),event.getStack());
                if(MinecraftForge.EVENT_BUS.post(pickupItemEvent)){
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTossItemEvent(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isPresent()) {
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.TossItemEvent tossItemEvent = new FPSMapEvent.PlayerEvent.TossItemEvent(map,player,event.getEntity());
                if(MinecraftForge.EVENT_BUS.post(tossItemEvent)){
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChatEvent(ServerChatEvent event){
        Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());
        if (opt.isPresent()) {
            BaseMap map = opt.get();
            FPSMapEvent.PlayerEvent.ChatEvent chatEvent = new FPSMapEvent.PlayerEvent.ChatEvent(map,event.getPlayer(),event.getMessage().getString());
            if(MinecraftForge.EVENT_BUS.post(chatEvent)){
                event.setCanceled(true);
            }
        }
    }

    /**
     * 玩家登录事件处理
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            opt.ifPresentOrElse(map->{
                FPSMapEvent.PlayerEvent.LoggedInEvent loggedInEvent = new FPSMapEvent.PlayerEvent.LoggedInEvent(map, player);
                MinecraftForge.EVENT_BUS.post(loggedInEvent);
            },()->{
                if(FPSMConfig.common.autoAdventureMode.get()){
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
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            boolean leave = true;
            if(opt.isPresent()){
                BaseMap map = opt.get();
                FPSMapEvent.PlayerEvent.LoggedOutEvent loggedOutEvent = new FPSMapEvent.PlayerEvent.LoggedOutEvent(map, player);
                if(MinecraftForge.EVENT_BUS.post(loggedOutEvent)){
                    leave = false;
                }
            }

            if(leave){
                FPSMCore.checkAndLeaveTeam(player);
            }
        }
    }

    /**
     * 玩家受伤事件处理
     * @param event 玩家受伤事件
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event){
        if(event.getEntity() instanceof ServerPlayer hurt){
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(hurt);
            if(opt.isPresent()) {
                BaseMap map = opt.get();
                if(map.isStart()){
                    FPSMapEvent.PlayerEvent.HurtEvent hurtEvent = new FPSMapEvent.PlayerEvent.HurtEvent(map,hurt,event.getSource(),event.getAmount());
                    MinecraftForge.EVENT_BUS.post(hurtEvent);

                    if(MinecraftForge.EVENT_BUS.post(hurtEvent)){
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map.isPresent()) {
                FPSMapEvent.PlayerEvent.DeathEvent deathEvent = new FPSMapEvent.PlayerEvent.DeathEvent(map.get(), player, event.getSource());
                if(MinecraftForge.EVENT_BUS.post(deathEvent)){
                    event.setCanceled(true);
                }
            }
        }
    }

}
