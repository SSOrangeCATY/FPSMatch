package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.entity.MatchDropEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    private static FPSMCore INSTANCE;
    private final MinecraftServer server;
    public final String archiveName;
    private final Map<String, List<BaseMap>> GAMES = new HashMap<>();
    private final Map<String, Function3<ServerLevel,String,AreaData,BaseMap>> REGISTRY = new HashMap<>();
    private final Map<String, Boolean> GAMES_SHOP = new HashMap<>();
    private FPSMCore(String archiveName, MinecraftServer server) {
        this.archiveName = archiveName;
        this.server = server;
    }

    public static FPSMCore getInstance(){
        if(INSTANCE == null) throw new RuntimeException("error : fpsm not install.");
        return INSTANCE;
    }

    protected static void setInstance(String archiveName, MinecraftServer server){
        INSTANCE = new FPSMCore(archiveName,server);
    }

    @Nullable public BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMapList -> baseMapList.forEach((baseMap)->{
            if(baseMap.checkGameHasPlayer(player)) map.set(baseMap);
        })));
         return map.get();
    }

    @Nullable public BaseMap getMapByPlayerWithSpec(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMapList -> baseMapList.forEach((baseMap)->{
            if(baseMap.checkGameHasPlayer(player)){
                map.set(baseMap);
            }else if (baseMap.checkSpecHasPlayer(player)){
                map.set(baseMap);
            }
        })));
        return map.get();
    }

    public BaseMap registerMap(String type, BaseMap map){
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

    @Nullable public BaseMap getMapByName(String name){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((baseMap)->{
            if(baseMap.getMapName().equals(name)) {
                map.set(baseMap);
            }
        }));
       return map.get();
    }

    public <T> List<T> getMapByClass(Class<T> clazz){
        ArrayList<T> list = new ArrayList<>();
        this.GAMES.values().forEach(mapList -> {
            mapList.forEach(map -> {
                if (clazz.isInstance(map)){
                    list.add((T) map);
                }
            });
        });
        return list;
    }

    public List<String> getMapNames(){
        List<String> names = new ArrayList<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((map-> names.add(map.getMapName()))));
        return names;
    }

    public List<String> getMapNames(String type){
        List<String> names = new ArrayList<>();
        List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
        maps.forEach((map-> names.add(map.getMapName())));
        return names;
    }

    public boolean checkGameType(String mapType){
       return REGISTRY.containsKey(mapType);
    }

    @Nullable public Function3<ServerLevel,String, AreaData,BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    public void registerGameType(String typeName, Function3<ServerLevel,String, AreaData,BaseMap> map, boolean enableShop){
        ResourceLocation.isValidResourceLocation(typeName);
        REGISTRY.put(typeName,map);
        GAMES_SHOP.put(typeName,enableShop);
    }

    public boolean checkGameIsEnableShop(String gameType){
        return GAMES_SHOP.getOrDefault(gameType,false);
    }

    public List<String> getEnableShopGames(){
        return GAMES_SHOP.keySet().stream().filter((type)-> GAMES_SHOP.getOrDefault(type,false)).toList();
    }

    public List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }


    public Map<String, List<BaseMap>> getAllMaps(){
        return GAMES;
    }

    public void onServerTick(){
        this.GAMES.forEach((type,mapList) -> mapList.forEach(BaseMap::mapTick));
    }

    protected void clearData(){
        GAMES.clear();
        GAMES_SHOP.clear();
    }

    public static void checkAndLeaveTeam(ServerPlayer player){
        BaseMap map = FPSMCore.getInstance().getMapByPlayerWithSpec(player);
        if(map != null){
            map.leave(player);
        }
    }

    @SubscribeEvent
    public static void onServerStartingEvent(ServerStartingEvent event) {
         FPSMCore.setInstance(event.getServer().getWorldData().getLevelName(),event.getServer());
         MinecraftForge.EVENT_BUS.post(new RegisterFPSMapEvent(FPSMCore.getInstance()));
    }

    public static void playerDropMatchItem(ServerPlayer player, ItemStack itemStack){
        RandomSource random = player.getRandom();
        MatchDropEntity.DropType type = MatchDropEntity.getItemType(itemStack);
        MatchDropEntity dropEntity = new MatchDropEntity(player.level(),itemStack,type);
        double d0 = player.getEyeY() - (double)0.3F;
        Vec3 pos = new Vec3(player.getX(), d0, player.getZ());
        dropEntity.setPos(pos);
        float f8 = Mth.sin(player.getXRot() * ((float)Math.PI / 180F));
        float f2 = Mth.cos(player.getXRot() * ((float)Math.PI / 180F));
        float f3 = Mth.sin(player.getYRot() * ((float)Math.PI / 180F));
        float f4 = Mth.cos(player.getYRot() * ((float)Math.PI / 180F));
        float f5 = random.nextFloat() * ((float)Math.PI * 2F);
        float f6 = 0.02F * random.nextFloat();
        dropEntity.setDeltaMovement((double)(-f3 * f2 * 0.3F) + Math.cos(f5) * (double)f6, -f8 * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F, (double)(f4 * f2 * 0.3F) + Math.sin(f5) * (double)f6);
        player.level().addFreshEntity(dropEntity);
    }

    public static void playerDeadDropWeapon(ServerPlayer serverPlayer){
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(serverPlayer);
        if(map != null){
            BaseTeam team = map.getMapTeams().getTeamByPlayer(serverPlayer);
            if(team != null){
                ItemStack itemStack = ItemStack.EMPTY;
                for(MatchDropEntity.DropType type : MatchDropEntity.DropType.values()){
                    if(type == MatchDropEntity.DropType.MISC){
                        break;
                    }
                    if(!itemStack.isEmpty()){
                        break;
                    }
                    Predicate<ItemStack> predicate = MatchDropEntity.getPredicateByDropType(type);
                    Inventory inventory = serverPlayer.getInventory();
                    List<List<ItemStack>> itemStackList = new ArrayList<>();
                    itemStackList.add(inventory.items);
                    itemStackList.add(inventory.armor);
                    itemStackList.add(inventory.offhand);
                    for(List<ItemStack> itemStacks : itemStackList){
                        for(ItemStack stack : itemStacks){
                            if (predicate.test(stack)){
                                itemStack = stack;
                                break;
                            }
                        }
                    }
                }

                if(!itemStack.isEmpty()){
                    playerDropMatchItem(serverPlayer,itemStack);
                }
            }
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

}
