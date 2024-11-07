package com.phasetranscrystal.fpsmatch.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.ClientData;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.net.ShopActionC2SPacket;
import com.phasetranscrystal.fpsmatch.net.ShopActionS2CPacket;
import com.tacz.guns.client.event.ClientPreventGunClick;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
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

    public static BaseMap registerMap(String type, BaseMap map){
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
                if(baseMap.getMapName().equals(name)) {
                    map.set(baseMap);
                }
            });
        });
       return map.get();
    }

    public static List<String> getMapNames(){
        List<String> names = new ArrayList<>();
        GAMES.forEach((type,mapList)->{
            mapList.forEach((map->{
                names.add(map.getMapName());
            }));
        });
        return names;
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

    @SubscribeEvent
    public static void onPlayerDropItem(ItemTossEvent event){
        if(event.getEntity().level().isClientSide) return;
        BaseMap map = FPSMCore.getMapByPlayer(event.getPlayer());
        if (map == null) return;
        FPSMShop shop = FPSMShop.getShopByMapName(map.getMapName());
        if (shop == null) return;
        ShopData.ShopSlot slot = shop.getPlayerShopData(event.getPlayer().getUUID()).checkItemStackIsInData(event.getEntity().getItem());
        if(slot != null){
            if (event.getEntity().getItem().getCount() > 1 && slot.canReturn()){
                shop.resetSlot((ServerPlayer) event.getPlayer(),slot.type(),slot.index());
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getPlayer()), new ShopActionS2CPacket(map.getMapName(),slot,2,shop.getPlayerShopData(event.getPlayer().getUUID()).getMoney()));
            }else{
                slot.returnGoods();
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getPlayer()), new ShopActionS2CPacket(map.getMapName(),slot,0,shop.getPlayerShopData(event.getPlayer().getUUID()).getMoney()));
            }
        }
    }


    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event){
        if(event.getEntity().level().isClientSide) return;
        BaseMap map = FPSMCore.getMapByPlayer(event.getEntity());
        if (map == null) return;
        FPSMShop shop = FPSMShop.getShopByMapName(map.getMapName());
        if (shop == null) return;

        // 根据名称判断的！！！ 他会先遍历装备到投掷物 index 0 到 4 所以尽量不要重名!
        ShopData.ShopSlot slot = shop.getPlayerShopData(event.getEntity().getUUID()).checkItemStackIsInData(event.getStack());
        if(slot != null){
            if (event.getStack().getCount() > 1 && slot.type() == ShopData.ItemType.THROWABLE){
                if(slot.boughtCount() < 2 && slot.index() == 0){
                    slot.bought(slot.boughtCount() != 1);
                }else{
                    slot.bought();
                }
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getEntity()), new ShopActionS2CPacket(map.getMapName(),slot,1,shop.getPlayerShopData(event.getEntity().getUUID()).getMoney()));
            }else{
                slot.bought();
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getEntity()), new ShopActionS2CPacket(map.getMapName(),slot,1,shop.getPlayerShopData(event.getEntity().getUUID()).getMoney()));
            }
        }
    }

}
