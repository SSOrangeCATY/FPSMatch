package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.core.event.FPSMapEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class FPSMBukkitEventBirge {

    protected FPSMBukkitEventBirge(){}

    public static void register(){
        if(FPSMBukkit.isBukkitEnvironment()){
            MinecraftForge.EVENT_BUS.register(new FPSMBukkitEventBirge());
            FPSMatch.LOGGER.info("FPSMatch : Bukkit API checked, successfully registered event bridge!");
        }
    }

    @SubscribeEvent
    public void onForgeKillEvent(FPSMapEvent.PlayerEvent.DeathEvent event) {
        if(!FPSMBukkit.isBukkitEnvironment()) return;
        Player forgeDead = event.getPlayer();
        Player forgeKiller = event.getKiller();
        BukkitPlayerKillOnMapEvent bukkitEvent = new BukkitPlayerKillOnMapEvent(
                event.getMap(), forgeDead.getUUID(), forgeKiller != null ? forgeKiller.getUUID() : null
        );
        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }

    @SubscribeEvent
    public void onForgeGameWinnerEvent(FPSMapEvent.VictoryEvent event) {
        if (!FPSMBukkit.isBukkitEnvironment()) return;

        ServerLevel forgeLevel = event.getMap().getServerLevel();
        World bukkitWorld = Bukkit.getWorld(FPSMBukkit.getLevelName(forgeLevel));

        BukkitGameWinnerEvent bukkitEvent = new BukkitGameWinnerEvent(
                event.getMap(),
                bukkitWorld
        );
        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }


}
