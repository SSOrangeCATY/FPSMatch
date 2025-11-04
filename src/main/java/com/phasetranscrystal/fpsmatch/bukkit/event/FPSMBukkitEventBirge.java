package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.core.event.map.GameWinnerEvent;
import com.phasetranscrystal.fpsmatch.core.event.map.PlayerKillOnMapEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    public void onForgeKillEvent(PlayerKillOnMapEvent event) {
        if(!FPSMBukkit.isBukkitEnvironment()) return;
        ServerPlayer forgeDead = event.getDead();
        ServerPlayer forgeKiller = event.getKiller();
        BukkitPlayerKillOnMapEvent bukkitEvent = new BukkitPlayerKillOnMapEvent(
                event.getBaseMap(), forgeDead.getUUID(), forgeKiller.getUUID()
        );
        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }

    @SubscribeEvent
    public void onForgeGameWinnerEvent(GameWinnerEvent event) {
        if (!FPSMBukkit.isBukkitEnvironment()) return;

        ServerLevel forgeLevel = event.getLevel();
        World bukkitWorld = Bukkit.getWorld(FPSMBukkit.getLevelName(forgeLevel));

        BukkitGameWinnerEvent bukkitEvent = new BukkitGameWinnerEvent(
                event.getMap(),
                event.getWinner(),
                event.getLoser(),
                bukkitWorld
        );
        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }


}
