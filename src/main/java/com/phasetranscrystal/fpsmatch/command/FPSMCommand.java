package com.phasetranscrystal.fpsmatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.FileHelper;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ChangeShopItemModule;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.sound.MVPMusicManager;
import com.phasetranscrystal.fpsmatch.cs.CSGameMap;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FPSMCommand {
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("fpsm").requires((permission)-> permission.hasPermission(2))
                .then(Commands.literal("mvp")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("sound", ResourceLocationArgument.id())
                                        .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                                        .executes(FPSMCommand::handleMvp))))
                .then(Commands.literal("loadOld").executes(FPSMCommand::handleLoadOld))
                .then(Commands.literal("save").executes(FPSMCommand::handleSave))
                .then(Commands.literal("sync").executes(FPSMCommand::handleSync))
                .then(Commands.literal("reload").executes(FPSMCommand::handleReLoad))
                .then(Commands.literal("listenerModule")
                       .then(Commands.literal("add")
                               .then(Commands.literal("changeItemModule")
                                      .then(Commands.argument("changedCost", IntegerArgumentType.integer(1))
                                              .then(Commands.argument("defaultCost", IntegerArgumentType.integer(1))
                                                      .executes(FPSMCommand::handleChangeItemModule))))))
                .then(Commands.literal("shop")
                        .then(Commands.argument("gameType", StringArgumentType.string())
                                .suggests(CommandSuggests.MAP_NAMES_WITH_IS_ENABLE_SHOP_SUGGESTION)
                                .then(Commands.argument("mapName", StringArgumentType.string())
                                        .suggests(CommandSuggests.MAP_NAMES_WITH_GAME_TYPE_SUGGESTION)
                                        .then(Commands.literal("modify")
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("shopName",StringArgumentType.string())
                                                                .suggests(CommandSuggests.SHOP_NAMES_SUGGESTION)
                                                                .then(Commands.argument("shopType",StringArgumentType.string())
                                                                        .suggests(CommandSuggests.SHOP_ITEM_TYPES_SUGGESTION)
                                                                        .then(Commands.argument("shopSlot", IntegerArgumentType.integer(1,5))
                                                                                .suggests(CommandSuggests.SHOP_SET_SLOT_ACTION_SUGGESTION)
                                                                                .then(Commands.literal("listenerModule")
                                                                                        .then(Commands.literal("add")
                                                                                                .then(Commands.argument("listenerModule", StringArgumentType.string())
                                                                                                        .suggests(CommandSuggests.SHOP_SLOT_ADD_LISTENER_MODULES_SUGGESTION)
                                                                                                        .executes(FPSMCommand::handleAddListenerModule)))
                                                                                .then(Commands.literal("remove")
                                                                                       .then(Commands.argument("listenerModule", StringArgumentType.string())
                                                                                               .suggests(CommandSuggests.SHOP_SLOT_REMOVE_LISTENER_MODULES_SUGGESTION)
                                                                                               .executes(FPSMCommand::handleRemoveListenerModule))))
                                                                        .then(Commands.literal("groupID")
                                                                               .then(Commands.argument("groupID", IntegerArgumentType.integer(0))
                                                                                       .executes(FPSMCommand::handleModifyShopGroupID)))
                                                                        .then(Commands.literal("cost")
                                                                                .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                                                                                        .executes(FPSMCommand::handleModifyCost)))
                                                                        .then(Commands.literal("item")
                                                                                .executes(FPSMCommand::handleModifyItemWithoutValue)
                                                                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                                                                       .executes(FPSMCommand::handleModifyItem)))
                                                                        .then(Commands.literal("dummyAmmoAmount")
                                                                                .then(Commands.argument("dummyAmmoAmount", IntegerArgumentType.integer(0))
                                                                                        .executes(FPSMCommand::handleGunModifyGunAmmoAmount)))
                                                                ))))))))
                .then(Commands.literal("map")
                        .then(Commands.literal("create")
                                .then(Commands.argument("gameType", StringArgumentType.string())
                                        .suggests(CommandSuggests.GAME_TYPES_SUGGESTION)
                                        .then(Commands.argument("mapName", StringArgumentType.string())
                                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                                .executes(FPSMCommand::handleCreateMapWithoutSpawnPoint))))))
                        .then(Commands.literal("modify")
                                .then(Commands.argument("mapName", StringArgumentType.string())
                                .suggests(CommandSuggests.MAP_NAMES_SUGGESTION)
                                        .then(Commands.literal("matchEndTeleportPoint")
                                                .then(Commands.argument("point", BlockPosArgument.blockPos())
                                                        .executes(FPSMCommand::handleModifyMatchEndTeleportPoint)))
                                        .then(Commands.literal("bombArea").requires((permission)-> permission.hasPermission(2))
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                                        .executes(FPSMCommand::handleBombAreaAction)))))
                                        .then(Commands.literal("debug")
                                                .then(Commands.argument("action", StringArgumentType.string())
                                                        .suggests(CommandSuggests.MAP_DEBUG_SUGGESTION)
                                                        .executes(FPSMCommand::handleDebugAction)))
                                        .then(Commands.literal("team")
                                                .then(Commands.literal("join")
                                                        .executes(FPSMCommand::handleJoinMapWithoutTarget)
                                                        .then(Commands.argument("targets", EntityArgument.players())
                                                                .executes(FPSMCommand::handleJoinMapWithTarget)))
                                                .then(Commands.literal("leave")
                                                        .executes(FPSMCommand::handleLeaveMapWithoutTarget)
                                                        .then(Commands.argument("targets", EntityArgument.players())
                                                                .executes(FPSMCommand::handleLeaveMapWithTarget)))
                                                .then(Commands.literal("teams")
                                                        .then(Commands.literal("spectator")
                                                                .then(Commands.literal("players")
                                                                        .then(Commands.argument("targets", EntityArgument.players())
                                                                                .then(Commands.argument("action", StringArgumentType.string())
                                                                                        .suggests(CommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                                        .executes(FPSMCommand::handleSpecTeamAction)))))
                                                        .then(Commands.argument("teamName", StringArgumentType.string())
                                                                .suggests(CommandSuggests.TEAM_NAMES_SUGGESTION)
                                                                .then(Commands.literal("kits")
                                                                        .then(Commands.argument("action", StringArgumentType.string())
                                                                                .suggests(CommandSuggests.SKITS_SUGGESTION)
                                                                                .executes(FPSMCommand::handleKitsWithoutItemAction)
                                                                                .then(Commands.literal("dummyAmmoAmount")
                                                                                        .then(Commands.argument("dummyAmmoAmount", IntegerArgumentType.integer(0))
                                                                                                .executes(FPSMCommand::handleKitsGunModifyGunAmmoAmount)))
                                                                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                                                                        .executes((c) -> FPSMCommand.handleKitsWithItemAction(c,1))
                                                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                                                .executes((c) -> FPSMCommand.handleKitsWithItemAction(c,IntegerArgumentType.getInteger(c,"amount")))))))
                                                                .then(Commands.literal("spawnpoints")
                                                                        .then(Commands.argument("action", StringArgumentType.string())
                                                                                .suggests(CommandSuggests.SPAWNPOINTS_ACTION_SUGGESTION)
                                                                                .executes(FPSMCommand::handleSpawnAction)))
                                                                .then(Commands.literal("players")
                                                                        .then(Commands.argument("targets", EntityArgument.players())
                                                                                .then(Commands.argument("action", StringArgumentType.string())
                                                                                        .suggests(CommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                                        .executes(FPSMCommand::handleTeamAction))))))))));
        dispatcher.register(literal);
    }

    private static int handleMvp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context,"targets");
        ResourceLocation sound = ResourceLocationArgument.getId(context,"sound");
        players.forEach(player -> MVPMusicManager.getInstance().addMvpMusic(player.getUUID().toString(),sound));
        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.mvp.success",players.size(),sound), true);
        return 1;
    }

    private static int handleJoinMapWithoutTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if(baseMap != null){
            baseMap.join(context.getSource().getPlayerOrException());
        }
        return 1;
    }

    private static int handleJoinMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context,"targets");
        if(baseMap != null){
            for (ServerPlayer player : players){
                baseMap.join(player);
            }
        }
        return 1;
    }

    private static int handleLeaveMapWithoutTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if(baseMap != null){
            baseMap.leave(context.getSource().getPlayerOrException());
        }
        return 1;
    }

    private static int handleLeaveMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context,"targets");
        if(baseMap != null){
            for (ServerPlayer player : players){
                baseMap.leave(player);
            }
        }
        return 1;
    }

    private static int handleModifyMatchEndTeleportPoint(CommandContext<CommandSourceStack> context) {
        BlockPos point = BlockPosArgument.getBlockPos(context,"point").above();
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if(baseMap instanceof CSGameMap csGameMap){
            SpawnPointData pointData = new SpawnPointData(context.getSource().getLevel().dimension(), point, 0f, 0f);
            csGameMap.setMatchEndTeleportPoint(pointData);
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.matchEndTeleportPoint.success",pointData.toString()),true);
            return 1;
        }else{
            context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.matchEndTeleportPoint.failed"));
            return 0;
        }
    }


    private static int handleSync(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        // TODO /fpsm sync shop <gameType> <gameName> <Player>
        FPSMCore.getInstance().getAllMaps().forEach((gameName,gameList)-> gameList.forEach(game->{
            if(game instanceof ShopMap<?> shopMap){
                shopMap.clearAndSyncShopData();
            }
        }));
        commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.sync.success"), true);
        return 1;
    }

    private static int handleModifyShopGroupID(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        int groupID = IntegerArgumentType.getInteger(commandSourceStackCommandContext, "groupID");
        String mapName = StringArgumentType.getString(commandSourceStackCommandContext, "mapName");
        String shopName = StringArgumentType.getString(commandSourceStackCommandContext, "shopName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        String shopTypeS = StringArgumentType.getString(commandSourceStackCommandContext, "shopType").toUpperCase(Locale.ROOT);
        ItemType shopType = ItemType.valueOf(shopTypeS);
        int slotNum = IntegerArgumentType.getInteger(commandSourceStackCommandContext,"shopSlot") - 1;
        if (map instanceof ShopMap<?> shopMap) {
            shopMap.getShop(shopName).setDefaultShopDataGroupId(shopType,slotNum,groupID);
            commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.slot.modify.group.success",shopType,slotNum,groupID), true);
            return 1;
        }else{
            commandSourceStackCommandContext.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.group.fail",mapName));
            return 0;
        }
    }

    private static int handleRemoveListenerModule(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        String moduleName = StringArgumentType.getString(commandSourceStackCommandContext, "listenerModule");
        String mapName = StringArgumentType.getString(commandSourceStackCommandContext, "mapName");
        String shopName = StringArgumentType.getString(commandSourceStackCommandContext, "shopName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        ItemType shopType = ItemType.valueOf(StringArgumentType.getString(commandSourceStackCommandContext, "shopType").toUpperCase(Locale.ROOT));
        int slotNum = IntegerArgumentType.getInteger(commandSourceStackCommandContext,"shopSlot") - 1;
        if (map instanceof ShopMap<?> shopMap) {
            shopMap.getShop(shopName).removeDefaultShopDataListenerModule(shopType,slotNum,moduleName);
            commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.slot.listener.remove.success",moduleName), true);
            return 1;
        }else{
            commandSourceStackCommandContext.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.listener.remove.fail",moduleName));
            return 0;
        }
    }

    private static int handleAddListenerModule(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        String moduleName = StringArgumentType.getString(commandSourceStackCommandContext, "listenerModule");
        String mapName = StringArgumentType.getString(commandSourceStackCommandContext, "mapName");
        String shopName = StringArgumentType.getString(commandSourceStackCommandContext, "shopName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        ItemType shopType = ItemType.valueOf(StringArgumentType.getString(commandSourceStackCommandContext, "shopType").toUpperCase(Locale.ROOT));
        int slotNum = IntegerArgumentType.getInteger(commandSourceStackCommandContext,"shopSlot") - 1;
        LMManager manager = FPSMatch.listenerModuleManager;
        if(map instanceof ShopMap<?> shopMap){
            ListenerModule module = manager.getListenerModule(moduleName);
            shopMap.getShop(shopName).addDefaultShopDataListenerModule(shopType,slotNum,module);
            commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.listener.add.success",moduleName), true);
            return 1;
        }else{
            commandSourceStackCommandContext.getSource().sendFailure(Component.translatable("commands.fpsm.listener.add.fail",moduleName));
            return 0;
        }
    }

    private static int handleChangeItemModule(CommandContext<CommandSourceStack> commandSourceStackCommandContext) throws CommandSyntaxException {
        int defaultCost = IntegerArgumentType.getInteger(commandSourceStackCommandContext,"defaultCost");
        int changedCost = IntegerArgumentType.getInteger(commandSourceStackCommandContext,"changedCost");
        Player player = commandSourceStackCommandContext.getSource().getPlayerOrException();
        ItemStack changedItem = player.getMainHandItem().copy();
        ItemStack defaultItem = player.getOffhandItem().copy();
        ChangeShopItemModule module = new ChangeShopItemModule(defaultItem,defaultCost,changedItem,changedCost);
        FPSMatch.listenerModuleManager.addListenerType(module);
        commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.listener.add.success",module.getName()), true);
        return 1;
    }

    private static int handleReLoad(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        FPSMatch.listenerModuleManager = new LMManager();
        //TODO MORE
        commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.reload.success"), true);
        return 1;
    }

    private static int handleSave(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        FPSMDataManager.getInstance().saveData();
        commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.save.success"), true);
        return 1;
    }

    private static int handleLoadOld(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        List<FileHelper.RawMapData> rawMapDataList = FileHelper.loadMaps(FPSMCore.getInstance().archiveName);
        for(FileHelper.RawMapData rawMapData : rawMapDataList){
            String mapType = rawMapData.mapRL.getNamespace();
            String mapName = rawMapData.mapRL.getPath();
            Function3<ServerLevel,String, AreaData,BaseMap> game = FPSMCore.getInstance().getPreBuildGame(mapType);
            Map<String, List<SpawnPointData>> data = rawMapData.teamsData;
            if(!data.isEmpty()){
                ResourceKey<Level> level = rawMapData.levelResourceKey;
                if (game != null) {
                    BaseMap map = FPSMCore.getInstance().registerMap(mapType, game.apply(commandSourceStackCommandContext.getSource().getServer().getLevel(level), mapName, rawMapData.areaData));
                    if(map != null){
                        map.getMapTeams().putAllSpawnPoints(data);

                        if(map instanceof ShopMap<?> shopMap && rawMapData.shop != null){
                            rawMapData.shop.forEach((k,v)->{
                                // TODO ERROR BUG????
                                shopMap.getShop(k).setDefaultShopData(v);
                            });
                        }

                        if(map instanceof BlastModeMap<?> blastModeMap){
                            if (rawMapData.blastAreaDataList != null) {
                                rawMapData.blastAreaDataList.forEach(blastModeMap::addBombArea);
                            }
                        }

                        if(map instanceof GiveStartKitsMap<?> startKitsMap && rawMapData.startKits != null){
                            startKitsMap.setStartKits(rawMapData.startKits);
                        }

                        if(map instanceof CSGameMap csGameMap && rawMapData.matchEndTeleportPoint != null){
                            csGameMap.setMatchEndTeleportPoint(rawMapData.matchEndTeleportPoint);
                        }

                    }
                }
            }
        }

        commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.save.success"), true);
        return 1;
    }

    private static int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String type = StringArgumentType.getString(context, "gameType");
        BlockPos pos1 = BlockPosArgument.getBlockPos(context,"from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context,"to");
        Function3<ServerLevel,String, AreaData,BaseMap> game = FPSMCore.getInstance().getPreBuildGame(type);
        if(game != null){
            FPSMCore.getInstance().registerMap(type, game.apply(context.getSource().getLevel(),mapName,new AreaData(pos1,pos2)));
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.create.success", mapName), true);
            return 1;
        }else{
            return 0;
        }
    }

    private static SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context){
        SpawnPointData data;
        Entity entity = context.getSource().getEntity();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition()).above();
        if(entity!=null){
            data = new SpawnPointData(context.getSource().getLevel().dimension(),pos,entity.getXRot(),entity.getYRot());
        }else{
            data = new SpawnPointData(context.getSource().getLevel().dimension(),pos,0f,0f);
        }
        return data;
    }


    private static int handleModifyCost(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, "shopName");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        ItemType itemType = ItemType.valueOf(shopType);
        int slotNum = IntegerArgumentType.getInteger(context,"shopSlot") - 1;
        int cost = IntegerArgumentType.getInteger(context,"cost");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            if (map instanceof ShopMap<?> shopMap) {
                shopMap.getShop(shopName).setDefaultShopDataCost(itemType,slotNum,cost);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.cost.success",shopType,slotNum,cost), true);
                return 1;
            } else {
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.modify.failed.noSupport"));
                return 0;
            }
        }else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
    }

    private static int handleModifyItemWithoutValue(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, "shopName");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context,"shopSlot") - 1;
        ItemType itemType = ItemType.valueOf(shopType);
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            if (context.getSource().getEntity() instanceof Player player && map instanceof ShopMap<?> shopMap) {
                ItemStack itemStack = player.getMainHandItem().copy();
                if(itemStack.getItem() instanceof IGun iGun){
                    FPSMUtil.fixGunItem(itemStack,iGun);
                }
                shopMap.getShop(shopName).setDefaultShopDataItemStack(itemType,slotNum,itemStack);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.item.success",shopType,slotNum,itemStack.getDisplayName()), true);
                return 1;
            } else {
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.modify.failed.noSupport"));
                return 0;
            }
        }else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
    }

    private static int handleModifyItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String shopName = StringArgumentType.getString(context, "shopName");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context,"shopSlot") - 1;
        ItemType itemType = ItemType.valueOf(shopType);
        ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(1,false);
        if(itemStack.getItem() instanceof IGun iGun){
            FPSMUtil.fixGunItem(itemStack,iGun);
        }
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            if (map instanceof ShopMap<?> shopMap) {
                shopMap.getShop(shopName).setDefaultShopDataItemStack(itemType,slotNum,itemStack);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.item.success",shopType,slotNum,itemStack.getDisplayName()), true);
                return 1;
            } else {
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.modify.failed.noSupport"));
                return 0;
            }
        }else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
    }

    private static int handleKitsGunModifyGunAmmoAmount(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String team = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        int dummyAmmoAmount = IntegerArgumentType.getInteger(context, "dummyAmmoAmount");

        if (map instanceof GiveStartKitsMap<?> startKitMap && context.getSource().getEntity() instanceof Player player) {
            switch (action) {
                case "add":
                    if (map.getMapTeams().checkTeam(team)) {
                                ItemStack itemStack = player.getMainHandItem().copy();
                                if (itemStack.getItem() instanceof IGun iGun && TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).isPresent()) {
                                    GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).get().getGunData();
                                    iGun.useDummyAmmo(itemStack);
                                    iGun.setMaxDummyAmmoAmount(itemStack, dummyAmmoAmount);
                                    iGun.setDummyAmmoAmount(itemStack, dummyAmmoAmount);
                                    iGun.setCurrentAmmoCount(itemStack, gunData.getAmmoAmount());
                                }
                        startKitMap.addKits(map.getMapTeams().getTeamByName(team), itemStack);
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.add.success", itemStack.getDisplayName(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        startKitMap.clearTeamKits(map.getMapTeams().getTeamByName(team));
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.clear.success", team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "list":
                    handleKitsListAction(context, team, map, startKitMap);
                    break;
                default:
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.kits.invalidAction"));
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
        return 1;
    }

    private static int handleGunModifyGunAmmoAmount(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, "shopName");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        ItemType itemType = ItemType.valueOf(shopType);
        int slotNum = IntegerArgumentType.getInteger(context,"shopSlot") - 1;
        int dummyAmmoAmount = IntegerArgumentType.getInteger(context,"dummyAmmoAmount");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            if (map instanceof ShopMap<?> shopMap) {
                ItemStack itemStack = shopMap.getShop(shopName).getDefaultShopDataMap().get(itemType).get(slotNum).process();
                if (itemStack.getItem() instanceof IGun iGun && TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).isPresent()){
                    GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).get().getGunData();
                    iGun.useDummyAmmo(itemStack);
                    iGun.setMaxDummyAmmoAmount(itemStack,dummyAmmoAmount);
                    iGun.setDummyAmmoAmount(itemStack,dummyAmmoAmount);
                    iGun.setCurrentAmmoCount(itemStack,gunData.getAmmoAmount());
                }
                shopMap.getShop(shopName).setDefaultShopDataItemStack(itemType,slotNum,itemStack);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.gun.success",shopType,slotNum,itemStack.getDisplayName(),dummyAmmoAmount), true);
                return 1;
            } else {
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.modify.gun.failed"));
                return 0;
            }

        }else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.modify.gun.failed"));
            return 0;
        }
    }

    private static int handleBombAreaAction(CommandContext<CommandSourceStack> context) {
        BlockPos pos1 = BlockPosArgument.getBlockPos(context,"from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context,"to");
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if (baseMap instanceof BlastModeMap<?> map) {
            map.addBombArea(new AreaData(pos1,pos2));
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.bombarea.success"),true);
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.bombarea.failed"));
        return 0;
    }

    private static int handleDebugAction(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            switch (action) {
                case "start":
                    map.startGame();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.debug.start.success", mapName), true);
                    break;
                case "reset":
                    map.resetGame();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.debug.reset.success", mapName), true);
                    break;
                case "newRound":
                    map.startNewRound();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.debug.newround.success", mapName), true);
                    break;
                case "cleanup":
                    map.cleanupMap();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.debug.cleanup.success", mapName), true);
                    break;
                case "switch":
                    boolean debug = map.switchDebugMode();
                    context.getSource().sendSuccess(() -> Component.literal("Debug Mode : "+ debug), true);
                    break;
                default:
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound", mapName));
            return 0;
        }
        return 1;
    }

    private static int handleSpecTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException{
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context,"targets");
        String mapName = StringArgumentType.getString(context, "mapName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            BaseTeam team = map.getMapTeams().getSpectatorTeam();
            switch (action) {
                case "join":
                    for(ServerPlayer player : players) {
                        map.joinSpec(player);
                        context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.team.join.success", player.getDisplayName(), team.getFixedName()), true);
                    }
                    break;
                case "leave":
                    if (team != null) {
                        for(ServerPlayer player : players) {
                            map.leave(player);
                            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.team.leave.success", player.getDisplayName()), true);
                        }
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.leave.failure", "spectator"));
                    }
                    break;
                default:
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.team.invalidAction"));
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }

        return 1;
    }

    private static int handleTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context,"targets");
        String mapName = StringArgumentType.getString(context, "mapName");
        String teamName = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            BaseTeam team = map.getMapTeams().getTeamByName(teamName);
            switch (action) {
                case "join":
                    if (team != null && team.getRemainingLimit() - players.size() >= 0) {
                        for(ServerPlayer player : players) {
                            map.join(teamName, player);
                            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.team.join.success", player.getDisplayName(), team.getFixedName()), true);
                        }
                    } else {
                        // 翻译文本
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.join.failure", team));
                    }
                    break;
                case "leave":
                    if (team != null) {
                        for(ServerPlayer player : players) {
                            map.leave(player);
                            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.team.leave.success", player.getDisplayName()), true);
                        }
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.leave.failure", teamName));
                    }
                    break;
                default:
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.team.invalidAction"));
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
        return 1;
    }

    private static int handleKitsWithoutItemAction(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String team = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);

        if (map instanceof GiveStartKitsMap<?> startKitMap && context.getSource().getEntity() instanceof Player player) {
            switch (action) {
                case "add":
                    if (map.getMapTeams().checkTeam(team)) {
                        ItemStack itemStack = player.getMainHandItem().copy();
                        startKitMap.addKits(map.getMapTeams().getTeamByName(team), itemStack);
                        context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.kits.add.success", itemStack.getDisplayName(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        startKitMap.clearTeamKits(map.getMapTeams().getTeamByName(team));
                        context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.kits.clear.success", team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "list":
                    handleKitsListAction(context, team, map, startKitMap);
                    break;
                default:
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.kits.invalidAction"));
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
        return 1;
    }
    private static int handleKitsWithItemAction(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        String team = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(count, false);
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);

        if (map instanceof GiveStartKitsMap<?> startKitMap) {
            switch (action) {
                case "add":
                    if (map.getMapTeams().checkTeam(team)) {
                        startKitMap.addKits(map.getMapTeams().getTeamByName(team), itemStack);
                        context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.kits.add.success",itemStack.getDisplayName(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        boolean flag = startKitMap.removeItem(map.getMapTeams().getTeamByName(team),itemStack);
                        if(flag){
                            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.kits.clear.success", team), true);
                        }else{
                            context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.kits.clear.failed"));
                        }
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "list":
                    handleKitsListAction(context, team, map, startKitMap);
                    break;
                default:
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.kits.invalidAction"));
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
        return 1;
    }

    private static void handleKitsListAction(CommandContext<CommandSourceStack> context, String team, BaseMap map, GiveStartKitsMap<?> startKitMap) {
        if (map.getMapTeams().checkTeam(team)) {
            List<ItemStack> itemStacks = startKitMap.getKits(map.getMapTeams().getTeamByName(team));
            for(ItemStack itemStack1 : itemStacks) {
                context.getSource().sendSuccess(itemStack1::getDisplayName, true);
            }
            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.kits.list.success", team, itemStacks.size()), true);
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
        }
    }

    private static int handleSpawnAction(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String team = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);

        if (map != null) {
            switch (action) {
                case "add":
                    if (map.getMapTeams().checkTeam(team)) {
                        map.getMapTeams().defineSpawnPoint(team, getSpawnPointData(context));
                        context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.spawn.add.success", team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        map.getMapTeams().resetSpawnPoints(team);
                        context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.spawn.clear.success", team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clearall":
                    map.getMapTeams().resetAllSpawnPoints();
                    context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.modify.spawn.clearall.success"), true);
                    break;
                default:
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.spawn.invalidAction"));
                    return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.map.notFound"));
            return 0;
        }
        return 1;
    }

}