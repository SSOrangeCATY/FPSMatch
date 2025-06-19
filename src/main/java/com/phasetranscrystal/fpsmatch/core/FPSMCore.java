package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.common.entity.drop.MatchDropEntity;
import com.phasetranscrystal.fpsmatch.common.entity.drop.DropType;
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
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    private static FPSMCore INSTANCE;
    public final String archiveName;
    private final Map<String, List<BaseMap>> GAMES = new HashMap<>();
    private final Map<String, Function3<ServerLevel,String,AreaData, BaseMap>> REGISTRY = new HashMap<>();
    private final FPSMDataManager fpsmDataManager;
    private final LMManager listenerModuleManager;

    private FPSMCore(String archiveName) {
        this.archiveName = archiveName;
        this.listenerModuleManager = new LMManager();
        this.fpsmDataManager = new FPSMDataManager(archiveName);
    }

    public static FPSMCore getInstance(){
        if(INSTANCE == null) throw new RuntimeException("fpsm not install.");
        return INSTANCE;
    }

    public static boolean initialized(){
        return INSTANCE != null;
    }

    @Nullable public BaseMap getMapByPlayer(Player player){
        for (List<BaseMap> list : GAMES.values()) {
            for (BaseMap map : list){
                if(map.checkGameHasPlayer(player)) return map;
            }
        }
        return null;
    }

    @Nullable public BaseMap getMapByPlayerWithSpec(Player player){
        for (List<BaseMap> list : GAMES.values()) {
            for (BaseMap map : list){
                if(map.checkGameHasPlayer(player) || map.checkSpecHasPlayer(player)) return map;
            }
        }
        return null;
    }

    public void registerMap(String type, BaseMap map){
        if(REGISTRY.containsKey(type)) {
            if(getMapNames(type).contains(map.getMapName())){
                FPSMatch.LOGGER.error("error : has same map name -> {}", map.getMapName());
                return;
            }
            List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
            maps.add(map);
            GAMES.put(type,maps);
        }else{
            FPSMatch.LOGGER.error("error : unregister game type {}", type);
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
        for (List<BaseMap> maps : GAMES.values()) {
            for (BaseMap map : maps) {
                if (clazz.isInstance(map)){
                    list.add((T) map);
                }
            }
        }
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

    @Nullable public Function3<ServerLevel,String, AreaData, BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    public void registerGameType(String typeName, Function3<ServerLevel,String, AreaData, BaseMap> map){
        ResourceLocation.isValidResourceLocation(typeName);
        REGISTRY.put(typeName,map);
    }

    public boolean checkGameIsEnableShop(String gameType){
        return GAMES.containsKey(gameType) && !GAMES.get(gameType).isEmpty() && GAMES.get(gameType).get(0) instanceof ShopMap<?>;
    }

    public List<String> getEnableShopGames(){
        return GAMES.values().stream()
                .flatMap(List::stream)
                .filter(baseMap -> baseMap instanceof ShopMap<?>)
                .map(BaseMap::getMapName)
                .collect(Collectors.toList());
    }

    public List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }


    public Map<String, List<BaseMap>> getAllMaps(){
        return GAMES;
    }

    public void onServerTick(){
        this.GAMES.forEach((type,mapList) -> mapList.forEach((map)->{
            try{
                map.mapTick();
            }catch(Exception e){
                FPSMatch.LOGGER.error("{} map error: ", map.getMapName(), e);
                map.resetGame();
            }
        }));
    }

    protected void clearData(){
        GAMES.clear();
    }

    public static void checkAndLeaveTeam(ServerPlayer player){
        BaseMap map = FPSMCore.getInstance().getMapByPlayerWithSpec(player);
        if(map != null){
            map.leave(player);
        }
    }
    @SubscribeEvent
    public static void onServerStoppingEvent(ServerStoppingEvent event){
        FPSMCore.getInstance().fpsmDataManager.saveData();
    }

    @SubscribeEvent
    public static void onServerStartedEvent(ServerStartedEvent event) {
        // 设置实例
        INSTANCE = new FPSMCore(event.getServer().getWorldData().getLevelName());
        // 注册地图
        MinecraftForge.EVENT_BUS.post(new RegisterFPSMapEvent(INSTANCE));
        // 注册数据
        MinecraftForge.EVENT_BUS.post(new RegisterFPSMSaveDataEvent(INSTANCE.fpsmDataManager));
        // 读取数据
        INSTANCE.fpsmDataManager.readData();
    }

    public static void playerDropMatchItem(ServerPlayer player, ItemStack itemStack){
        RandomSource random = player.getRandom();
        DropType type = DropType.getItemDropType(itemStack);
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
            map.getMapTeams().getTeamByPlayer(serverPlayer).ifPresent(team->{
                ItemStack itemStack = ItemStack.EMPTY;
                for(DropType type : DropType.values()){
                    if(type == DropType.MISC){
                        break;
                    }
                    if(!itemStack.isEmpty()){
                        break;
                    }
                    Predicate<ItemStack> predicate = DropType.getPredicateByDropType(type);
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
            });
        }
    }

    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid){
        return Optional.ofNullable(this.getServer().getPlayerList().getPlayer(uuid));
    }


    public FPSMDataManager getFPSMDataManager() {
        return fpsmDataManager;
    }

    public LMManager getListenerModuleManager(){
        return listenerModuleManager;
    }

}
