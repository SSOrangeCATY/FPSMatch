package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.DeathMessage;
import com.phasetranscrystal.fpsmatch.core.data.save.FileHelper;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.event.PlayerKillOnMapEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterListenerModuleEvent;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.ShopData;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ChangeShopItemModule;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ReturnGoodsModule;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.item.CompositionC4;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.net.CSGameTabStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.net.DeathMessageS2CPacket;
import com.phasetranscrystal.fpsmatch.net.FPSMatchLoginMessageS2CPacket;
import com.phasetranscrystal.fpsmatch.net.FPSMatchStatsResetS2CPacket;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModDamageTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;


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
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new FPSMatchStatsResetS2CPacket());
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new FPSMatchLoginMessageS2CPacket());
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
                    playerTeam.handleOffline(player);
                    int im = player.getInventory().clearOrCountMatchingItems((i) -> i.getItem() instanceof CompositionC4, -1, player.inventoryMenu.getCraftSlots());
                    if (im > 0) {
                        ItemEntity entity = player.drop(new ItemStack(FPSMItemRegister.C4.get(), 1), false, false);
                        if (entity != null) {
                            entity.setGlowingTag(true);
                        }
                        player.getInventory().setChanged();
                    }
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
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerPlayer from = null;
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map != null && map.checkGameHasPlayer(player)) {
                if(event.getSource().getEntity() instanceof ServerPlayer fromPlayer){
                    BaseMap fromMap = FPSMCore.getInstance().getMapByPlayer(player);
                    if (fromMap != null && fromMap.equals(map)) {
                        from = fromPlayer;
                            if(event.getSource().is(ModDamageTypes.BULLET) || event.getSource().is(ModDamageTypes.BULLET_IGNORE_ARMOR)){
                                if(fromPlayer.getMainHandItem().getItem() instanceof IGun) {
                                    Component killerName = fromPlayer.getDisplayName();
                                    Component deadName = event.getEntity().getDisplayName();
                                    DeathMessage deathMessage = new DeathMessage(killerName, deadName, fromPlayer.getMainHandItem(), false);
                                    DeathMessageS2CPacket killMessageS2CPacket = new DeathMessageS2CPacket(deathMessage);
                                    fromMap.getMapTeams().getJoinedPlayers().forEach((uuid -> {
                                        ServerPlayer serverPlayer = (ServerPlayer) fromMap.getServerLevel().getPlayerByUUID(uuid);
                                        if(serverPlayer != null){
                                            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), killMessageS2CPacket);
                                        }
                                    }));
                                }
                            }
                    }
                }
                handlePlayerDeath(map,player,from);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(ItemTossEvent event){
        if(event.getEntity().level().isClientSide) return;
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());

        //商店逻辑
        if (map instanceof ShopMap shopMap){
            FPSMShop shop = shopMap.getShop();
            if (shop == null) return;
            ItemStack itemStack = event.getEntity().getItem();

            ShopData shopData = shop.getPlayerShopData(event.getEntity().getUUID());
            Pair<ItemType, ShopSlot> pair = shopData.checkItemStackIsInData(itemStack);
            if(pair != null){
                ShopSlot slot = pair.getSecond();
                if(pair.getFirst() != ItemType.THROWABLE){
                    slot.unlock(itemStack.getCount());
                    shop.syncShopData((ServerPlayer) event.getPlayer(),pair.getFirst(),slot);
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

            ShopData shopData = shop.getPlayerShopData(event.getEntity().getUUID());
            Pair<ItemType, ShopSlot> pair = shopData.checkItemStackIsInData(event.getStack());
            if(pair != null){
                ShopSlot slot = pair.getSecond();
                slot.lock(event.getStack().getCount());
                shop.syncShopData((ServerPlayer) event.getEntity(),pair.getFirst(),slot);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStoppingEvent(ServerStoppingEvent event){
        String name = event.getServer().getWorldData().getLevelName();
        FileHelper.saveMaps(name);
        FPSMatch.listenerModuleManager.save();
    }

    @SubscribeEvent
    public static void onServerStartedEvent(ServerStartedEvent event) {
        FPSMatch.listenerModuleManager = new LMManager();
        List<FileHelper.RawMapData> rawMapDataList = FileHelper.loadMaps(FPSMCore.getInstance().archiveName);
            for(FileHelper.RawMapData rawMapData : rawMapDataList){
                String mapType = rawMapData.mapRL.getNamespace();
                String mapName = rawMapData.mapRL.getPath();
                Function3<ServerLevel,String, AreaData,BaseMap> game = FPSMCore.getInstance().getPreBuildGame(mapType);
                Map<String, List<SpawnPointData>> data = rawMapData.teamsData;
                if(!data.isEmpty()){
                    ResourceKey<Level> level = rawMapData.levelResourceKey;
                    if (game != null) {
                        BaseMap map = FPSMCore.getInstance().registerMap(mapType, game.apply(event.getServer().getLevel(level), mapName, rawMapData.areaData));
                        if(map != null){
                            map.setGameType(mapType);
                            map.getMapTeams().putAllSpawnPoints(data);

                            if(map instanceof ShopMap shopMap && rawMapData.shop != null){
                                shopMap.getShop().setDefaultShopData(rawMapData.shop);
                            }

                            if(map instanceof BlastModeMap<?> blastModeMap){
                                if (rawMapData.blastAreaDataList != null) {
                                    rawMapData.blastAreaDataList.forEach(blastModeMap::addBombArea);
                                }
                            }

                            if(map instanceof GiveStartKitsMap<?> startKitsMap && rawMapData.startKits != null){
                                startKitsMap.setStartKits(rawMapData.startKits);
                            }

                        }
                    }
                }
            }
    }


    public static void handlePlayerDeath(BaseMap map, ServerPlayer player, @Nullable ServerPlayer from){
        if(map.isStart) {

            if(map instanceof ShopMap shopMap){
                shopMap.getShop().clearPlayerShopData(player.getUUID());
            }

            MapTeams teams = map.getMapTeams();
            BaseTeam deadPlayerTeam = teams.getTeamByPlayer(player);
            if (deadPlayerTeam != null) {
                PlayerData data = deadPlayerTeam.getPlayerData(player.getUUID());
                if (data == null) return;
                data.getTabData().addDeaths();
                data.setLiving(false);

                // 清除c4,并掉落c4
                int im = player.getInventory().clearOrCountMatchingItems((i) -> i.getItem() instanceof CompositionC4, -1, player.inventoryMenu.getCraftSlots());
                if (im > 0) {
                   ItemEntity entity = player.drop(new ItemStack(FPSMItemRegister.C4.get(), 1), false, false);
                    if (entity != null) {
                        entity.setGlowingTag(true);
                    }
                    player.getInventory().setChanged();
                }

                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.SPECTATOR);
                List<UUID> uuids = teams.getSameTeamPlayerUUIDs(player);
                Entity entity = null;
                if (uuids.size() > 1) {
                    Random random = new Random();
                    entity = map.getServerLevel().getEntity(uuids.get(random.nextInt(0, uuids.size())));
                } else if (!uuids.isEmpty()) {
                    entity = map.getServerLevel().getEntity(uuids.get(0));
                }
                if (entity != null) player.setCamera(entity);
                player.setRespawnPosition(player.level().dimension(), player.getOnPos().above(), 0f, true, false);
                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(player.getUUID(), data.getTabData()));
            }


            Map<UUID, Float> hurtDataMap = teams.getLivingHurtData().get(player.getUUID());
            if (hurtDataMap != null && !hurtDataMap.isEmpty()) {

                List<Map.Entry<UUID, Float>> sortedDamageEntries = hurtDataMap.entrySet().stream()
                        .filter(entry -> entry.getValue() > 4)
                        .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                        .limit(2)
                        .toList();

                for (Map.Entry<UUID, Float> sortedDamageEntry : sortedDamageEntries) {
                    UUID assistId = sortedDamageEntry.getKey();
                    ServerPlayer assist = (ServerPlayer) map.getServerLevel().getPlayerByUUID(assistId);
                    if (assist != null && teams.getJoinedPlayers().contains(assistId)) {
                        BaseTeam assistPlayerTeam = teams.getTeamByPlayer(assist);
                        if (assistPlayerTeam != null) {
                            PlayerData assistData = assistPlayerTeam.getPlayerData(assistId);
                            // 如果是击杀者就不添加助攻
                            if (assistData == null || from != null && from.getUUID().equals(assistId)) continue;
                            assistData.getTabData().addAssist();
                            FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(assistData.getOwner(), assistData.getTabData()));
                        }
                    }
                }
            }

            if(from == null) return;
            BaseTeam killerPlayerTeam = teams.getTeamByPlayer(from);
            if (killerPlayerTeam != null) {
                PlayerData data = killerPlayerTeam.getPlayerData(from.getUUID());
                if (data == null) return;
                data.getTabData().addKills();
                MinecraftForge.EVENT_BUS.post(new PlayerKillOnMapEvent(map, player, from));
                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(from.getUUID(), data.getTabData()));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterListenerModuleEvent(RegisterListenerModuleEvent event){
        event.register(new ReturnGoodsModule());
        ChangeShopItemModule changeShopItemModule = new ChangeShopItemModule(new ItemStack(Items.APPLE), 50, new ItemStack(Items.GOLDEN_APPLE), 300);
        event.register(changeShopItemModule);
    }
}
