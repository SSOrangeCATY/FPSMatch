package com.phasetranscrystal.fpsmatch.bukkit;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.bukkit.event.FPSMBukkitEventBirge;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;

public class FPSMBukkit {
    public static void register(){
        if(isBukkitEnvironment()){
            MinecraftForge.EVENT_BUS.register(new FPSMBukkitEventBirge());
            FPSMatch.LOGGER.info("FPSMatch : Bukkit API detected, successfully registered event bridge!");
        }
    }
    public static boolean isBukkitEnvironment() {
        try {
            Class.forName("org.bukkit.Bukkit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String getLevelName(ServerLevel level) {
        String original = level.toString();
        if (original.startsWith("ServerLevel[") && original.endsWith("]")) {
            return original.substring("ServerLevel[".length(), original.length() - 1);
        }
        return "UnknownLevel";
    }
}
