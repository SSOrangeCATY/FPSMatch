package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    private static final Map<String, List<BaseMap>> GAMES = new HashMap<>();
    private static final Map<String, BiFunction<ServerLevel,String,BaseMap>> REGISTRY = new HashMap<>();
    private static final Map<String, Boolean> GAMES_SHOP = new HashMap<>();

    @Nullable public static BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMapList -> baseMapList.forEach((baseMap)->{
            if(baseMap.checkGameHasPlayer(player)){
                map.set(baseMap);
            }
        })));
         return map.get();
    }

    public static BaseMap registerMap(String type, BaseMap map){
        if(REGISTRY.containsKey(type)) {
            new ResourceLocation(type,map.mapName);

            if(getMapNames(type).contains(map.getMapName())){
                FPSMatch.LOGGER.error("error : has same map name -> " + map.getMapName());
                return null;
            }
            List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
            maps.add(map);
            GAMES.put(type,maps);
            return map;
        }else{
            FPSMatch.LOGGER.error("error : unregister game type " + type);
            return null;
        }
    }

    @Nullable public static BaseMap getMapByName(String name){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((baseMap)->{
            if(baseMap.getMapName().equals(name)) {
                map.set(baseMap);
            }
        }));
       return map.get();
    }

    public static List<String> getMapNames(){
        List<String> names = new ArrayList<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((map->{
            names.add(map.getMapName());
        })));
        return names;
    }

    public static List<String> getMapNames(String type){
        List<String> names = new ArrayList<>();
        List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
        maps.forEach((map-> names.add(map.getMapName())));
        return names;
    }

    public static boolean checkGameType(String mapType){
       return REGISTRY.containsKey(mapType);
    }

    @Nullable public static BiFunction<ServerLevel,String,BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    public static void registerGameType(String typeName, BiFunction<ServerLevel,String,BaseMap> map, boolean enableShop){
        ResourceLocation.isValidResourceLocation(typeName);
        REGISTRY.put(typeName,map);
        GAMES_SHOP.put(typeName,enableShop);
    }

    public static boolean checkGameIsEnableShop(String gameType){
        return GAMES_SHOP.getOrDefault(gameType,false);
    }

    public static List<String> getEnableShopGames(){
        return GAMES_SHOP.keySet().stream().filter((type)-> GAMES_SHOP.getOrDefault(type,false)).toList();
    }

    public static List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }


    public static Map<String, List<BaseMap>> getAllMaps(){
        return GAMES;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            FPSMCore.GAMES.forEach((type,mapList)-> mapList.forEach(BaseMap::mapTick));
        }
    }

}
