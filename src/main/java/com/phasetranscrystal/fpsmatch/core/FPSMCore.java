package com.phasetranscrystal.fpsmatch.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
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
    private static final Map<String,List<BaseMap>> GAMES = new HashMap<>();
    private static final Map<String, Function3<ServerLevel,List<String>,String,BaseMap>> REGISTRY = new HashMap<>();
    private static final Map<String, ShopData> GAMES_DEFAULT_SHOP_DATA = new HashMap<>();
    private static final Map<String, Map<UUID, ShopData>> GAMES_SHOP_DATA = new HashMap<>();

    @Nullable public static BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMapList -> {
            baseMapList.forEach((baseMap)->{
                if(baseMap.checkGameHasPlayer(player)){
                    map.set(baseMap);
                }
            });
         }));
         return map.get();
    }

    public static BaseMap registerMap(String type,BaseMap map){
        if(REGISTRY.containsKey(type)) {
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
        GAMES.forEach((type,mapList)->{
            mapList.forEach((baseMap)->{
                if(baseMap.mapName.equals(name)) map.set(baseMap);
            });
        });
       return map.get();
    }

    public static List<String> getMapNames(){
        return GAMES.keySet().stream().toList();
    }

    public static List<String> getMapNames(String type){
        List<String> names = new ArrayList<>();
        List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
        maps.forEach((map->{
            names.add(map.getMapName());
        }));
        return names;
    }

    public static boolean checkGameType(String mapType){
       return REGISTRY.containsKey(mapType);
    }

    @Nullable public static Function3<ServerLevel,List<String>,String,BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    public static void registerGameType(String typeName, Function3<ServerLevel,List<String>,String,BaseMap> map, boolean enableShop){
        REGISTRY.put(typeName,map);
        if(enableShop) GAMES_DEFAULT_SHOP_DATA.put(typeName,new ShopData());
    }

    public static boolean checkGameIsEnableShop(String gameType){
        return GAMES_DEFAULT_SHOP_DATA.getOrDefault(gameType,null) != null;
    }

    public static List<String> getEnableShopGames(){
        return GAMES_DEFAULT_SHOP_DATA.keySet().stream().filter((type)-> GAMES_DEFAULT_SHOP_DATA.getOrDefault(type,null) != null).toList();
    }

    public static void setGameDefaultData(String gameType, ShopData data){
        if (checkGameIsEnableShop(gameType)){
            GAMES_DEFAULT_SHOP_DATA.put(gameType,data);
        }else FPSMatch.LOGGER.error(gameType + " is unsupported shop.");
    }

    public static void addPlayerShopData(String gameType,UUID player, ShopData data){
        if (checkGameIsEnableShop(gameType)){
            Map<UUID, ShopData> shopData = GAMES_SHOP_DATA.get(gameType);
            shopData.put(player,data);
            GAMES_SHOP_DATA.put(gameType,GAMES_SHOP_DATA.put(gameType,shopData));
        }else FPSMatch.LOGGER.error(gameType + " is unsupported shop.");
    }

    public static List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            GAMES.forEach((type,mapList)->{
                mapList.forEach(BaseMap::mapTick);
            });
        }
    }
}
