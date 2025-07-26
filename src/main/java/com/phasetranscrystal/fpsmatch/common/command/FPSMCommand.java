package com.phasetranscrystal.fpsmatch.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMCommandEvent;
import com.phasetranscrystal.fpsmatch.core.map.*;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ChangeShopItemModule;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.*;

public class FPSMCommand {
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("fpsm").requires((permission) -> permission.hasPermission(2))
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
                                .suggests(CommandSuggests.GAME_TYPES_SUGGESTION)
                                .then(Commands.argument("mapName", StringArgumentType.string())
                                        .suggests(CommandSuggests.MAP_NAMES_WITH_IS_ENABLE_SHOP_SUGGESTION)
                                        .then(Commands.literal("modify")
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("shopName", StringArgumentType.string())
                                                                .suggests(CommandSuggests.SHOP_NAMES_SUGGESTION)
                                                                .then(Commands.argument("shopType", StringArgumentType.string())
                                                                        .suggests(CommandSuggests.SHOP_ITEM_TYPES_SUGGESTION)
                                                                        .then(Commands.argument("shopSlot", IntegerArgumentType.integer(1, 5))
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
                                .then(Commands.argument("gameType", StringArgumentType.string())
                                        .suggests(CommandSuggests.GAME_TYPES_SUGGESTION)
                                        .then(Commands.argument("mapName", StringArgumentType.string())
                                                .suggests(CommandSuggests.MAP_NAMES_WITH_GAME_TYPE_SUGGESTION)
                                                .then(Commands.literal("matchEndTeleportPoint")
                                                        .then(Commands.argument("point", BlockPosArgument.blockPos())
                                                                .executes(FPSMCommand::handleModifyMatchEndTeleportPoint)))
                                                .then(Commands.literal("bombArea")
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
                                                                                                .executes((c) -> FPSMCommand.handleKitsWithItemAction(c, 1))
                                                                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                                                        .executes((c) -> FPSMCommand.handleKitsWithItemAction(c, IntegerArgumentType.getInteger(c, "amount")))))))
                                                                        .then(Commands.literal("spawnpoints")
                                                                                .then(Commands.argument("action", StringArgumentType.string())
                                                                                        .suggests(CommandSuggests.SPAWNPOINTS_ACTION_SUGGESTION)
                                                                                        .then(Commands.argument("from", Vec2Argument.vec2())
                                                                                                .then(Commands.argument("to", Vec2Argument.vec2())
                                                                                                        .executes(FPSMCommand::handleSpawnAction)))
                                                                                        .executes(FPSMCommand::handleSpawnAction)))
                                                                        .then(Commands.literal("players")
                                                                                .then(Commands.argument("targets", EntityArgument.players())
                                                                                        .then(Commands.argument("action", StringArgumentType.string())
                                                                                                .suggests(CommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                                                .executes(FPSMCommand::handleTeamAction)))))))))));
        RegisterFPSMCommandEvent registerFPSMCommandEvent = new RegisterFPSMCommandEvent(literal);
        MinecraftForge.EVENT_BUS.post(registerFPSMCommandEvent);
        dispatcher.register(registerFPSMCommandEvent.get());
    }

    private static int handleJoinMapWithoutTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if (baseMap != null) {
            baseMap.join(context.getSource().getPlayerOrException());
        }
        return 1;
    }

    private static int handleJoinMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        if (baseMap != null) {
            for (ServerPlayer player : players) {
                baseMap.join(player);
            }
        }
        return 1;
    }

    private static int handleLeaveMapWithoutTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if (baseMap != null) {
            baseMap.leave(context.getSource().getPlayerOrException());
        }
        return 1;
    }

    private static int handleLeaveMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        if (baseMap != null) {
            for (ServerPlayer player : players) {
                baseMap.leave(player);
            }
        }
        return 1;
    }

    private static int handleModifyMatchEndTeleportPoint(CommandContext<CommandSourceStack> context) {
        BlockPos point = BlockPosArgument.getBlockPos(context, "point").above();
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if (baseMap instanceof EndTeleportMap<?> endTeleportMap) {
            SpawnPointData pointData = new SpawnPointData(context.getSource().getLevel().dimension(), point, 0f, 0f);
            endTeleportMap.setMatchEndTeleportPoint(pointData);
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.match_end_teleport_point.success", pointData.toString()), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.match_end_teleport_point.not_available",mapName));
            return 0;
        }
    }


    private static int handleSync(CommandContext<CommandSourceStack> context) {
        // TODO /fpsm sync shop <gameType> <gameName> <Player>
        FPSMCore.getInstance().getAllMaps().forEach((gameName, gameList) -> gameList.forEach(game -> {
            if (game instanceof ShopMap<?> shopMap) {
                shopMap.clearAndSyncShopData();
            }
        }));
        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.sync.success"), true);
        return 1;
    }

    private static int handleModifyShopGroupID(CommandContext<CommandSourceStack> context) {
        int groupID = IntegerArgumentType.getInteger(context, "groupID");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopName = StringArgumentType.getString(context, "shopName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
        if (map instanceof ShopMap<?> shopMap) {
            shopMap.getShop(shopName).ifPresentOrElse(shop -> {
                shop.setDefaultShopDataGroupId(shopType, slotNum, groupID);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.slot.modify.group.success", shopType, slotNum, groupID), true);
            },()-> context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName)));
            return 1;
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
            return 0;
        }
    }

    private static int handleRemoveListenerModule(CommandContext<CommandSourceStack> context) {
        String moduleName = StringArgumentType.getString(context, "listenerModule");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopName = StringArgumentType.getString(context, "shopName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
        if (map instanceof ShopMap<?> shopMap) {
            shopMap.getShop(shopName).ifPresentOrElse(shop -> {
                shop.removeDefaultShopDataListenerModule(shopType, slotNum, moduleName);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.slot.listener.remove.success", moduleName), true);
            },()-> context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName)));
            return 1;
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
            return 0;
        }
    }

    private static int handleAddListenerModule(CommandContext<CommandSourceStack> context) {
        String moduleName = StringArgumentType.getString(context, "listenerModule");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopName = StringArgumentType.getString(context, "shopName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
        LMManager manager = FPSMCore.getInstance().getListenerModuleManager();
        if (map instanceof ShopMap<?> shopMap) {
            shopMap.getShop(shopName).ifPresentOrElse(shop -> {
                ListenerModule module = manager.getListenerModule(moduleName);
                shop.addDefaultShopDataListenerModule(shopType, slotNum, module);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.listener.add.success", moduleName), true);
                },()-> context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName)));
            return 1;
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
            return 0;
        }
    }

    private static int handleChangeItemModule(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int defaultCost = IntegerArgumentType.getInteger(context, "defaultCost");
        int changedCost = IntegerArgumentType.getInteger(context, "changedCost");
        Player player = context.getSource().getPlayerOrException();
        ItemStack changedItem = player.getMainHandItem().copy();
        ItemStack defaultItem = player.getOffhandItem().copy();
        ChangeShopItemModule module = new ChangeShopItemModule(defaultItem, defaultCost, changedItem, changedCost);
        FPSMCore.getInstance().getListenerModuleManager().addListenerType(module);
        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.listener.add.success", module.getName()), true);
        return 1;
    }

    private static int handleReLoad(CommandContext<CommandSourceStack> context) {
        //TODO MORE
        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.reload.success"), true);
        return 1;
    }

    private static int handleSave(CommandContext<CommandSourceStack> context) {
        FPSMCore.getInstance().getFPSMDataManager().saveData();
        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.save.success"), true);
        return 1;
    }

    private static int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String type = StringArgumentType.getString(context, "gameType");
        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "to");
        Function3<ServerLevel, String, AreaData, BaseMap> game = FPSMCore.getInstance().getPreBuildGame(type);
        if (game != null) {
            FPSMCore.getInstance().registerMap(type, game.apply(context.getSource().getLevel(), mapName, new AreaData(pos1, pos2)));
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.create.success", mapName), true);
            return 1;
        } else {
            return 0;
        }
    }

    private static SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context) {
        SpawnPointData data;
        Entity entity = context.getSource().getEntity();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition()).above();
        if (entity != null) {
            data = new SpawnPointData(context.getSource().getLevel().dimension(), pos, entity.getXRot(), entity.getYRot());
        } else {
            data = new SpawnPointData(context.getSource().getLevel().dimension(), pos, 0f, 0f);
        }
        return data;
    }

    // 粒子效果将沿着立方体的12条棱渲染
    private static void generateCubeEdgesParticles(CommandSourceStack source, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // 定义所有12条棱的端点坐标，依次为每条棱的两个端点
        BlockPos[] edges = new BlockPos[]{
                // Front face edges
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, minY, minZ), // Bottom front
                new BlockPos(minX, minY, minZ), new BlockPos(minX, maxY, minZ), // Left front
                new BlockPos(maxX, minY, minZ), new BlockPos(maxX, maxY, minZ), // Right front
                new BlockPos(minX, maxY, minZ), new BlockPos(maxX, maxY, minZ), // Top front

                // Back face edges
                new BlockPos(minX, minY, maxZ), new BlockPos(maxX, minY, maxZ), // Bottom back
                new BlockPos(minX, minY, maxZ), new BlockPos(minX, maxY, maxZ), // Left back
                new BlockPos(maxX, minY, maxZ), new BlockPos(maxX, maxY, maxZ), // Right back
                new BlockPos(minX, maxY, maxZ), new BlockPos(maxX, maxY, maxZ), // Top back

                // Connecting edges between front and back
                new BlockPos(minX, minY, minZ), new BlockPos(minX, minY, maxZ), // Left vertical
                new BlockPos(maxX, minY, minZ), new BlockPos(maxX, minY, maxZ), // Right vertical
                new BlockPos(minX, maxY, minZ), new BlockPos(minX, maxY, maxZ), // Left vertical top
                new BlockPos(maxX, maxY, minZ), new BlockPos(maxX, maxY, maxZ), // Right vertical top
        };

        // 执行粒子命令
        for (int i = 0; i < edges.length; i += 2) {
            BlockPos start = edges[i];
            BlockPos end = edges[i + 1];
            // 在每条棱上生成粒子效果
            spawnParticlesAlongEdge(source, start, end);
        }
    }

    // 在一条棱上生成粒子效果
    private static void spawnParticlesAlongEdge(CommandSourceStack source, BlockPos start, BlockPos end) {
        double x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
        double x2 = end.getX(), y2 = end.getY(), z2 = end.getZ();

        // 计算每个坐标之间的步长
        double step = 0.1; // 每步的间隔，调整粒子效果的密度

        // 循环沿棱的路径生成粒子
        for (double t = 0; t <= 1; t += step) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double dz = z2 - z1;
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            double z = z1 + dz * t;

            if(source.getPlayer() != null){
                source.getLevel().sendParticles(source.getPlayer(),ParticleTypes.FLAME,false, x, y, z, 0, dx, dy, dz, 0.0001);
            }
        }
    }

    private static int handleModifyCost(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, "shopName");
        String mapName = StringArgumentType.getString(context, "mapName");
        String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
        int cost = IntegerArgumentType.getInteger(context, "cost");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map instanceof ShopMap<?> shopMap) {
            Optional<FPSMShop<?>> shop = shopMap.getShop(shopName);
            if(shop.isPresent()){
                shop.get().setDefaultShopDataCost(shopType, slotNum, cost);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.cost.success", shopType, slotNum, cost), true);
                return 1;
            }else{
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
            return 0;
        }
    }

    private static int handleModifyItemWithoutValue(CommandContext<CommandSourceStack> context) {
        if(context.getSource().getEntity() instanceof Player player){
            String shopName = StringArgumentType.getString(context, "shopName");
            String mapName = StringArgumentType.getString(context, "mapName");
            String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
            BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
            if (map instanceof ShopMap<?> shopMap) {
                Optional<FPSMShop<?>> shop = shopMap.getShop(shopName);
                if(shop.isPresent()){
                    ItemStack itemStack = player.getMainHandItem().copy();
                    if (itemStack.getItem() instanceof IGun iGun) {
                        FPSMUtil.fixGunItem(itemStack, iGun);
                    }
                    shop.get().setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.item.success", shopType, slotNum, itemStack.getDisplayName()), true);
                    return 1;
                }else{
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                }
            } else {
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
                return 0;
            }
        }else{
            context.getSource().sendFailure(Component.translatable("commands.fpsm.only.player"));
            return 0;
        }
    }

    private static int handleModifyItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String shopName = StringArgumentType.getString(context, "shopName");
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map instanceof ShopMap<?> shopMap) {
            Optional<FPSMShop<?>> shop = shopMap.getShop(shopName);
            String shopType = StringArgumentType.getString(context, "shopType").toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
            ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(1, false);
            if(shop.isPresent()){
                if (itemStack.getItem() instanceof IGun iGun) {
                    FPSMUtil.fixGunItem(itemStack, iGun);
                }
                shop.get().setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.item.success", shopType, slotNum, itemStack.getDisplayName()), true);
                return 1;
            }else{
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
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
                        map.getMapTeams().getTeamByName(team).ifPresent(t-> startKitMap.addKits(t,itemStack));
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.add.success", itemStack.getDisplayName(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        map.getMapTeams().getTeamByName(team).ifPresent(startKitMap::clearTeamKits);
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
        int slotNum = IntegerArgumentType.getInteger(context, "shopSlot") - 1;
        int dummyAmmoAmount = IntegerArgumentType.getInteger(context, "dummyAmmoAmount");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map instanceof ShopMap<?> shopMap) {
            Optional<FPSMShop<?>> shop = shopMap.getShop(shopName);
            if(shop.isPresent()){
                ItemStack itemStack = shop.get().getDefaultShopDataMap().get(shopType).get(slotNum).process();
                if (itemStack.getItem() instanceof IGun iGun) {
                    FPSMUtil.setDummyAmmo(itemStack, iGun, dummyAmmoAmount);
                }
                shop.get().setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.shop.modify.gun.success", shopType, slotNum, itemStack.getDisplayName(), dummyAmmoAmount), true);
                return 1;
            }else{
                context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                return 0;
            }
        } else {
            context.getSource().sendFailure(Component.translatable("commands.fpsm.shop.not_available", mapName));
            return 0;
        }
    }

    private static int handleBombAreaAction(CommandContext<CommandSourceStack> context) {
        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "to");
        String mapName = StringArgumentType.getString(context, "mapName");
        BaseMap baseMap = FPSMCore.getInstance().getMapByName(mapName);
        if (baseMap instanceof BlastModeMap<?> map) {
            map.addBombArea(new AreaData(pos1, pos2));
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.bombarea.success"), true);
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
                    context.getSource().sendSuccess(() -> Component.literal("Debug Mode : " + debug), true);
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

    private static int handleSpecTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        String mapName = StringArgumentType.getString(context, "mapName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            BaseTeam team = map.getMapTeams().getSpectatorTeam();
            switch (action) {
                case "join":
                    for (ServerPlayer player : players) {
                        map.joinSpec(player);
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.team.join.success", player.getDisplayName(), team.getFixedName()), true);
                    }
                    break;
                case "leave":
                    if (team != null) {
                        for (ServerPlayer player : players) {
                            map.leave(player);
                            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.team.leave.success", player.getDisplayName()), true);
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
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        String mapName = StringArgumentType.getString(context, "mapName");
        String teamName = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map != null) {
            map.getMapTeams().getTeamByName(teamName).ifPresent(team->{
                switch (action) {
                    case "join":
                        if (team.getRemainingLimit() - players.size() >= 0) {
                            for (ServerPlayer player : players) {
                                map.join(teamName, player);
                                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.team.join.success", player.getDisplayName(), team.getFixedName()), true);
                            }
                        } else {
                            // 翻译文本
                            context.getSource().sendFailure(Component.translatable("commands.fpsm.team.join.failure", team));
                        }
                        break;
                    case "leave":
                        for (ServerPlayer player : players) {
                            map.leave(player);
                            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.team.leave.success", player.getDisplayName()), true);
                        }
                        break;
                    default:
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.invalidAction"));
                }
            });
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
                        map.getMapTeams().getTeamByName(team).ifPresent(t-> startKitMap.addKits(t, itemStack));
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.add.success", itemStack.getDisplayName(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        map.getMapTeams().getTeamByName(team).ifPresent(startKitMap::clearTeamKits);
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
                        map.getMapTeams().getTeamByName(team).ifPresent(t-> startKitMap.addKits(t, itemStack));
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.add.success", itemStack.getDisplayName(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        map.getMapTeams().getTeamByName(team).ifPresent(t->{
                            boolean flag = startKitMap.removeItem(t, itemStack);
                            if (flag) {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.clear.success", team), true);
                            } else {
                                context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.kits.clear.failed"));
                            }
                        });
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
            map.getMapTeams().getTeamByName(team).ifPresent(t->{
                List<ItemStack> itemStacks = startKitMap.getKits(t);
                for (ItemStack itemStack1 : itemStacks) {
                    context.getSource().sendSuccess(itemStack1::getDisplayName, true);
                }
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.kits.list.success", team, itemStacks.size()), true);
            });
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
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.spawn.add.success", team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clear":
                    if (map.getMapTeams().checkTeam(team)) {
                        map.getMapTeams().resetSpawnPoints(team);
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.spawn.clear.success", team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
                    break;
                case "clearall":
                    map.getMapTeams().resetAllSpawnPoints();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.spawn.clearall.success"), true);
                    break;
                case "set":
                    if (map.getMapTeams().checkTeam(team)) {
                        Vec2 from;
                        Vec2 to;
                        try {
                            from = Vec2Argument.getVec2(context, "from");
                            to = Vec2Argument.getVec2(context, "to");
                        } catch (IllegalArgumentException e) {
                            context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.spawn.set.missing_args"));
                            return 0;
                        }
                        int minX = (int) Math.floor(Math.min(from.x, to.x));
                        int maxX = (int) Math.floor(Math.max(from.x, to.x));
                        int minZ = (int) Math.floor(Math.min(from.y, to.y));
                        int maxZ = (int) Math.floor(Math.max(from.y, to.y));
                        int y = BlockPos.containing(context.getSource().getPosition()).getY();
                        int border = (int) from.distanceToSqr(to);
                        List<BlockPos> airBlocks = new ArrayList<>();
                        // 遍历区域并检查是否是空气方块
                        if (border >= 130) {
                            context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.spawn.set.over_flow"));
                            return 0;
                        }
                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                BlockState block = context.getSource().getLevel().getBlockState(pos);

                                if (block.isAir()) {
                                    SpawnPointData defaultData = getSpawnPointData(context);
                                    SpawnPointData data = new SpawnPointData(defaultData.getDimension(), pos, defaultData.getYaw(), defaultData.getPitch());
                                    map.getMapTeams().defineSpawnPoint(team, data);
                                    airBlocks.add(pos);
                                }
                            }
                        }
                        // 生成粒子效果沿着立方体的12条棱
                        generateCubeEdgesParticles(context.getSource(), minX, y, minZ, maxX + 1, y + 1, maxZ + 1);
                        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.spawn.set.success", airBlocks.size(), team), true);
                    } else {
                        context.getSource().sendFailure(Component.translatable("commands.fpsm.team.notFound"));
                    }
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