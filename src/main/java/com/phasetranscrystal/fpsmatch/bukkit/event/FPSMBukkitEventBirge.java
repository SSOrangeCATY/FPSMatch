package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.core.event.CSGameRoundEndEvent;
import com.phasetranscrystal.fpsmatch.core.event.GameWinnerEvent;
import com.phasetranscrystal.fpsmatch.core.event.PlayerGetMvpEvent;
import com.phasetranscrystal.fpsmatch.core.event.PlayerKillOnMapEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class FPSMBukkitEventBirge {
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
    public void onForgeMvpEvent(PlayerGetMvpEvent event) {
        if (!FPSMBukkit.isBukkitEnvironment()) return;

        BukkitPlayerGetMvpEvent bukkitEvent = new BukkitPlayerGetMvpEvent(
                event.getReason().uuid,
                event.getMap(),
                event.getReason()
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

    @SubscribeEvent
    public void onForgeRoundEndEvent(CSGameRoundEndEvent event) {
        if (!FPSMBukkit.isBukkitEnvironment()) return;

        BukkitCSGameRoundEndEvent bukkitEvent = new BukkitCSGameRoundEndEvent(
                event.getMap(),
                event.getWinner(),
                event.getReason(),
                event.getWinnerMoney()
        );
        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }

}
