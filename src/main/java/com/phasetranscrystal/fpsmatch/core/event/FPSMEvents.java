package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.*;
import com.phasetranscrystal.fpsmatch.core.data.save.FileHelper;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.net.CSGameTabStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopActionS2CPacket;
import com.tacz.guns.GunMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FPSMEvents {
    @SubscribeEvent
    public static void onServerTickEvent(TickEvent.ServerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            FPSMCore.getInstance().onServerTick();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if(map != null && map.isStart){
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null) {
                    PlayerData data = playerTeam.getPlayerData(player.getUUID());
                    if(data == null) return;
                    data.setOffline(false);
                    //TODO
                }
            }else{
                if(!player.isCreative()){
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.SURVIVAL);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if(map != null && map.isStart){
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null) {
                    PlayerData data = playerTeam.getPlayerData(player.getUUID());
                    if(data == null) return;
                    data.setOffline(true);
                    data.getTabDataTemp().addDeaths();
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.SPECTATOR);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurtEvent(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource damageSource = event.getSource();
            if(damageSource.getEntity() instanceof ServerPlayer from){
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map != null && map.checkGameHasPlayer(player) && map.checkGameHasPlayer(from)) {
                    float damage = event.getAmount();
                    map.getMapTeams().addHurtData(player,from.getUUID(),damage);
                    if(damage >= player.getHealth()){
                        if(map.isStart){
                            MapTeams teams = map.getMapTeams();
                            BaseTeam deadPlayerTeam = teams.getTeamByPlayer(player);
                            if(deadPlayerTeam != null){
                                PlayerData data = deadPlayerTeam.getPlayerData(player.getUUID());
                                if(data == null) return;
                                data.getTabData().addDeaths();
                                data.setLiving(false);
                                player.heal(player.getMaxHealth());
                                player.setGameMode(GameType.SPECTATOR);
                                List<UUID> uuids = teams.getSameTeamPlayerUUIDs(player);
                                Entity entity = null;
                                if(uuids.size() > 1){
                                    Random random = new Random();
                                    entity = map.getServerLevel().getEntity(uuids.get(random.nextInt(0,uuids.size())));
                                }else if(!uuids.isEmpty()){
                                    entity = map.getServerLevel().getEntity(uuids.get(0));
                                }
                                if(entity != null) player.setCamera(entity);
                                player.setRespawnPosition(player.level().dimension(),player.getOnPos().above(),0f,true,false);
                                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(player.getUUID(),data.getTabData()));
                            }
                            BaseTeam killerPlayerTeam = teams.getTeamByPlayer(from);
                            if(killerPlayerTeam != null){
                                PlayerData data = killerPlayerTeam.getPlayerData(from.getUUID());
                                if(data == null) return;
                                data.getTabData().addKills();
                                MinecraftForge.EVENT_BUS.post(new PlayerKillOnMapEvent(map,player,from));
                                Map<UUID, Float> hurtDataMap = teams.getLivingHurtData().get(player.getUUID());
                                if(hurtDataMap != null && !hurtDataMap.isEmpty()){

                                    List<Map.Entry<UUID, Float>> sortedDamageEntries = hurtDataMap.entrySet().stream()
                                            .filter(entry -> !entry.getKey().equals(from.getUUID()) && entry.getValue() > 4)
                                            .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                                            .limit(2)
                                            .toList();

                                    for (Map.Entry<UUID, Float> sortedDamageEntry : sortedDamageEntries) {
                                        UUID assistId = sortedDamageEntry.getKey();
                                        ServerPlayer assist = (ServerPlayer) map.getServerLevel().getPlayerByUUID(assistId);
                                        if (assist != null && teams.getJoinedPlayers().contains(assistId)) {
                                            BaseTeam assistPlayerTeam = teams.getTeamByPlayer(from);
                                            if(assistPlayerTeam != null){
                                                PlayerData assistData = assistPlayerTeam.getPlayerData(assistId);
                                                if(assistData == null) return;
                                                assistData.getTabData().addAssist();
                                                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(assistData.getOwner(),assistData.getTabData()));
                                            }
                                        }
                                    }
                                }
                                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(from.getUUID(),data.getTabData()));
                            }
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(ItemTossEvent event){
        if(event.getEntity().level().isClientSide) return;
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());
        if (map instanceof ShopMap shopMap){
            FPSMShop shop = shopMap.getShop();
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
    }


    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event){
        if(event.getEntity().level().isClientSide) return;
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getEntity());
        if (map instanceof ShopMap shopMap) {
            FPSMShop shop = shopMap.getShop();
            if (shop == null) return;
            // 如果不是Tacz的枪就是根据名称判断的！！！ 他会先遍历装备到投掷物 index 0 到 4 所以尽量不要重名!
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

    @SubscribeEvent
    public static void onServerStoppingEvent(ServerStoppingEvent event){
        String name = event.getServer().getWorldData().getLevelName();
        FPSMCore.getInstance().getAllMaps().forEach((type,gameList)->{
            gameList.forEach((map)->{
                if(map instanceof ShopMap shopMap){
                    FileHelper.saveShopData(name,shopMap.getShop());
                }
            });
        });
        FileHelper.saveMaps(name);
    }

    @SubscribeEvent
    public static void onServerStartedEvent(ServerStartedEvent event) {
        Map<ResourceLocation, Map<String, List<SpawnPointData>>> savedMaps = FileHelper.loadMaps(FPSMCore.getInstance().archiveName);
        savedMaps.forEach((rl, teamData) -> {
            String mapName = rl.getPath();
            String type = rl.getNamespace();
            BiFunction<ServerLevel, String, BaseMap> game = FPSMCore.getInstance().getPreBuildGame(type);
            List<SpawnPointData> data = teamData.values().stream().findFirst().get();
            if(!data.isEmpty()){
                ResourceKey<Level> level = data.get(0).getDimension();
                if (game != null) {
                    BaseMap map = FPSMCore.getInstance().registerMap(type, game.apply(event.getServer().getLevel(level), mapName));
                    if(map != null){
                        map.setGameType(type);
                        map.getMapTeams().putAllSpawnPoints(teamData);
                    }
                }
            }
        });
    }
}
