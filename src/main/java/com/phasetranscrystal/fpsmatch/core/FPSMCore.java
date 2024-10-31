package com.phasetranscrystal.fpsmatch.core;

import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class FPSMCore {
    public static final Map<String,BaseMap> games = new HashMap<>();
    @Nullable public static BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
         games.values().forEach((baseMap -> {
            if(baseMap.checkGameHasPlayer(player)){
                map.set(baseMap);
            };
        }));
         return map.get();
    }

    public static void registerMap(String mapName ,BaseMap map){
        games.put(mapName,map);
    }

    @Nullable public static BaseMap getMapByName(String name){
       return games.getOrDefault(name,null);
    }

    public static Set<String> getMapNames(){
        return games.keySet();
    }
}
