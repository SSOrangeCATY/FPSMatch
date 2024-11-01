package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    private static final Map<String,BaseMap> GAMES = new HashMap<>();
    private static final Map<String, Function3<ServerLevel,List<String>,SpawnPointData,BaseMap>> REGISTRY = new HashMap<>();

    @Nullable public static BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMap -> {
            if(baseMap.checkGameHasPlayer(player)){
                map.set(baseMap);
            }
         }));
         return map.get();
    }

    public static void registerMap(String mapName ,BaseMap map){
        if(REGISTRY.containsKey(map.getType())) {
            GAMES.put(mapName,map);
        }else{
            FPSMatch.LOGGER.error("error : unregister game type " + map.getType());
        }
    }

    @Nullable public static BaseMap getMapByName(String name){
       return GAMES.getOrDefault(name,null);
    }

    public static List<String> getMapNames(){
        return GAMES.keySet().stream().toList();
    }

    public static boolean checkGameType(String mapType){
       return REGISTRY.containsKey(mapType);
    }

    @Nullable public static Function3<ServerLevel, List<String>, SpawnPointData, BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    public static void registerGameType(String typeName, Function3<ServerLevel,List<String>,SpawnPointData,BaseMap> map){
        REGISTRY.put(typeName,map);
    }

    public static List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            GAMES.values().forEach((BaseMap::mapTick));
        }
    }
}
