package com.phasetranscrystal.fpsmatch.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.shop.functional.ChangeShopItemModule;
import com.phasetranscrystal.fpsmatch.core.event.FPSMReloadEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMCommandEvent;
import com.phasetranscrystal.fpsmatch.core.map.*;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
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
import net.minecraft.network.chat.MutableComponent;
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

import static com.phasetranscrystal.fpsmatch.common.command.FPSMCommandSuggests.*;

public class FPSMCommand {
    // 常量
    private static final double PARTICLE_STEP = 0.1;
    private static List<Component> HELPS;

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = buildCommandTree(event);

        RegisterFPSMCommandEvent registerFPSMCommandEvent = new RegisterFPSMCommandEvent(literal);
        MinecraftForge.EVENT_BUS.post(registerFPSMCommandEvent);
        HELPS = registerFPSMCommandEvent.getHelps();
        dispatcher.register(registerFPSMCommandEvent.get());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(RegisterCommandsEvent event) {
        return Commands.literal("fpsm").requires(permission -> permission.hasPermission(2))
                .then(Commands.literal("help").executes(FPSMCommand::handleHelp))
                .then(Commands.literal("save").executes(FPSMCommand::handleSave))
                .then(Commands.literal("sync").executes(FPSMCommand::handleSync))
                .then(Commands.literal("reload").executes(FPSMCommand::handleReLoad))
                .then(Commands.literal("debug").executes(FPSMCommand::handleDebug))
                .then(Commands.literal("listener_module")
                        .then(Commands.literal("add")
                                .then(Commands.literal("change_item_module")
                                        .then(Commands.argument("changed_cost", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("default_cost", IntegerArgumentType.integer(1))
                                                        .executes(FPSMCommand::handleChangeItemModule))))))
                .then(Commands.literal("shop")
                        .then(Commands.argument(GAME_TYPE_ARG, StringArgumentType.string())
                                .suggests(FPSMCommandSuggests.GAME_TYPES_SUGGESTION)
                                .then(Commands.argument(MAP_NAME_ARG, StringArgumentType.string())
                                        .suggests(FPSMCommandSuggests.MAP_NAMES_WITH_IS_ENABLE_SHOP_SUGGESTION)
                                        .then(Commands.literal("modify")
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument(SHOP_NAME_ARG, StringArgumentType.string())
                                                                .suggests(FPSMCommandSuggests.SHOP_NAMES_SUGGESTION)
                                                                .then(Commands.argument(SHOP_TYPE_ARG, StringArgumentType.string())
                                                                        .suggests(FPSMCommandSuggests.SHOP_ITEM_TYPES_SUGGESTION)
                                                                        .then(Commands.argument(SHOP_SLOT_ARG, IntegerArgumentType.integer(1, 5))
                                                                                .suggests(FPSMCommandSuggests.SHOP_SET_SLOT_ACTION_SUGGESTION)
                                                                                .then(Commands.literal("listener_module")
                                                                                        .then(Commands.literal("add")
                                                                                                .then(Commands.argument("listener_module", StringArgumentType.string())
                                                                                                        .suggests(FPSMCommandSuggests.SHOP_SLOT_ADD_LISTENER_MODULES_SUGGESTION)
                                                                                                        .executes(FPSMCommand::handleAddListenerModule)))
                                                                                        .then(Commands.literal("remove")
                                                                                                .then(Commands.argument("listener_module", StringArgumentType.string())
                                                                                                        .suggests(FPSMCommandSuggests.SHOP_SLOT_REMOVE_LISTENER_MODULES_SUGGESTION)
                                                                                                        .executes(FPSMCommand::handleRemoveListenerModule))))
                                                                                .then(Commands.literal("group_id")
                                                                                        .then(Commands.argument("group_id", IntegerArgumentType.integer(0))
                                                                                                .executes(FPSMCommand::handleModifyShopGroupID)))
                                                                                .then(Commands.literal("cost")
                                                                                        .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                                                                                                .executes(FPSMCommand::handleModifyCost)))
                                                                                .then(Commands.literal("item")
                                                                                        .executes(FPSMCommand::handleModifyItemWithoutValue)
                                                                                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                                                                                .executes(FPSMCommand::handleModifyItem)))
                                                                                .then(Commands.literal("dummy_ammo_amount")
                                                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                                                .executes(FPSMCommand::handleGunModifyGunAmmoAmount)))))))))))
                .then(Commands.literal("map")
                        .then(Commands.literal("create")
                                .then(Commands.argument(GAME_TYPE_ARG, StringArgumentType.string())
                                        .suggests(FPSMCommandSuggests.GAME_TYPES_SUGGESTION)
                                        .then(Commands.argument(MAP_NAME_ARG, StringArgumentType.string())
                                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                                .executes(FPSMCommand::handleCreateMapWithoutSpawnPoint))))))
                        .then(Commands.literal("modify")
                                .then(Commands.argument(GAME_TYPE_ARG, StringArgumentType.string())
                                        .suggests(FPSMCommandSuggests.GAME_TYPES_SUGGESTION)
                                        .then(Commands.argument(MAP_NAME_ARG, StringArgumentType.string())
                                                .suggests(FPSMCommandSuggests.MAP_NAMES_WITH_GAME_TYPE_SUGGESTION)
                                                .then(Commands.literal("match_end_teleport_point")
                                                        .then(Commands.argument("point", BlockPosArgument.blockPos())
                                                                .executes(FPSMCommand::handleModifyMatchEndTeleportPoint)))
                                                .then(Commands.literal("bomb_area")
                                                        .then(Commands.literal("add")
                                                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                                                .executes(FPSMCommand::handleBombAreaAction)))))
                                                .then(Commands.literal("debug")
                                                        .then(Commands.argument(ACTION_ARG, StringArgumentType.string())
                                                                .suggests(FPSMCommandSuggests.MAP_DEBUG_SUGGESTION)
                                                                .executes(FPSMCommand::handleDebugAction)))
                                                .then(Commands.literal("team")
                                                        .then(Commands.literal("join")
                                                                .executes(FPSMCommand::handleJoinMapWithoutTarget)
                                                                .then(Commands.argument(TARGETS_ARG, EntityArgument.players())
                                                                        .executes(FPSMCommand::handleJoinMapWithTarget)))
                                                        .then(Commands.literal("leave")
                                                                .executes(FPSMCommand::handleLeaveMapWithoutTarget)
                                                                .then(Commands.argument(TARGETS_ARG, EntityArgument.players())
                                                                        .executes(FPSMCommand::handleLeaveMapWithTarget)))
                                                        .then(Commands.literal("teams")
                                                                .then(Commands.literal("spectator")
                                                                        .then(Commands.literal("players")
                                                                                .then(Commands.argument(TARGETS_ARG, EntityArgument.players())
                                                                                        .then(Commands.argument(ACTION_ARG, StringArgumentType.string())
                                                                                                .suggests(FPSMCommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                                                .executes(FPSMCommand::handleSpecTeamAction)))))
                                                                .then(Commands.argument(TEAM_NAME_ARG, StringArgumentType.string())
                                                                        .suggests(FPSMCommandSuggests.TEAM_NAMES_SUGGESTION)
                                                                        .then(Commands.literal("kits")
                                                                                .then(Commands.argument(ACTION_ARG, StringArgumentType.string())
                                                                                        .suggests(FPSMCommandSuggests.SKITS_SUGGESTION)
                                                                                        .executes(FPSMCommand::handleKitsWithoutItemAction)
                                                                                        .then(Commands.literal("dummy_ammo_amount")
                                                                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                                                        .executes(FPSMCommand::handleKitsGunModifyGunAmmoAmount)))
                                                                                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                                                                                .executes(context -> handleKitsWithItemAction(context, 1))
                                                                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                                                        .executes(context -> handleKitsWithItemAction(context, IntegerArgumentType.getInteger(context, "amount")))))))
                                                                        .then(Commands.literal("spawnpoints")
                                                                                .then(Commands.argument(ACTION_ARG, StringArgumentType.string())
                                                                                        .suggests(FPSMCommandSuggests.SPAWNPOINTS_ACTION_SUGGESTION)
                                                                                        .then(Commands.argument("from", Vec2Argument.vec2())
                                                                                                .then(Commands.argument("to", Vec2Argument.vec2())
                                                                                                        .executes(FPSMCommand::handleSpawnAction)))
                                                                                        .executes(FPSMCommand::handleSpawnAction)))
                                                                        .then(Commands.literal("players")
                                                                                .then(Commands.argument(TARGETS_ARG, EntityArgument.players())
                                                                                        .then(Commands.argument(ACTION_ARG, StringArgumentType.string())
                                                                                                .suggests(FPSMCommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                                                .executes(FPSMCommand::handleTeamAction)))))))))));
    }

    private static int handleHelp(CommandContext<CommandSourceStack> context) {
        MutableComponent helpMessage = Component.translatable("commands.fpsm.help.header")
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.basic"))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.listener"))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.shop"))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.map"))
                .append(Component.literal("\n"));
        for (Component help : HELPS) {
            helpMessage.append(help).append(Component.literal("\n"));
        }
        helpMessage.append(Component.translatable("commands.fpsm.help.footer"));

        context.getSource().sendSuccess(() -> helpMessage, false);
        return 1;
    }

    // 辅助方法
    private static Optional<BaseMap> getMapByName(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
        return FPSMCore.getInstance().getMapByName(mapName);
    }

    private static Optional<FPSMShop<?>> getShop(CommandContext<CommandSourceStack> context, BaseMap map, String shopName) {
        if (map instanceof ShopMap<?> shopMap) {
            return shopMap.getShop(shopName);
        }
        return Optional.empty();
    }

    private static void sendSuccess(CommandSourceStack source, Component key) {
        source.sendSuccess(() -> key, true);
    }

    private static void sendFailure(CommandSourceStack source, Component key) {
        source.sendFailure(key);
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof Player;
    }

    private static Player getPlayerOrFail(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!isPlayer(context.getSource())) {
            sendFailure(context.getSource(), Component.translatable("commands.fpsm.only.player"));
        }
        return context.getSource().getPlayerOrException();
    }

    // 命令处理方法
    private static int handleSave(CommandContext<CommandSourceStack> context) {
        FPSMCore.getInstance().getFPSMDataManager().saveData();
        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.save.success"));
        return 1;
    }

    private static int handleSync(CommandContext<CommandSourceStack> context) {
        FPSMCore.getInstance().getAllMaps().values().stream()
                .flatMap(List::stream)
                .filter(ShopMap.class::isInstance)
                .map(ShopMap.class::cast)
                .forEach(ShopMap::clearAndSyncShopData);

        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.sync.success"));
        return 1;
    }

    private static int handleReLoad(CommandContext<CommandSourceStack> context) {
        MinecraftForge.EVENT_BUS.post(new FPSMReloadEvent(FPSMCore.getInstance()));
        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.reload.success"));
        return 1;
    }

    private static int handleDebug(CommandContext<CommandSourceStack> context) {
        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.success",FPSMatch.switchDebug()));
        return 1;
    }

    private static int handleChangeItemModule(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player player = getPlayerOrFail(context);
        int defaultCost = IntegerArgumentType.getInteger(context, "default_cost");
        int changedCost = IntegerArgumentType.getInteger(context, "changed_cost");

        ItemStack changedItem = player.getMainHandItem().copy();
        ItemStack defaultItem = player.getOffhandItem().copy();

        ChangeShopItemModule module = new ChangeShopItemModule(defaultItem, defaultCost, changedItem, changedCost);
        FPSMCore.getInstance().getListenerModuleManager().addListenerType(module);

        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.listener.add.success", module.getName()));
        return 1;
    }

    private static int handleAddListenerModule(CommandContext<CommandSourceStack> context) {
        String moduleName = StringArgumentType.getString(context, "listener_module");
        String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;

        LMManager manager = FPSMCore.getInstance().getListenerModuleManager();

        return getMapByName(context)
                .flatMap(map -> getShop(context, map, shopName))
                .map(shop -> {
                    ListenerModule module = manager.getListenerModule(moduleName);
                    shop.addDefaultShopDataListenerModule(shopType, slotNum, module);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.listener.add.success", moduleName));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                });
    }

    private static int handleRemoveListenerModule(CommandContext<CommandSourceStack> context) {
        String moduleName = StringArgumentType.getString(context, "listener_module");
        String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;

        return getMapByName(context)
                .flatMap(map -> getShop(context, map, shopName))
                .map(shop -> {
                    shop.removeDefaultShopDataListenerModule(shopType, slotNum, moduleName);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.slot.listener.remove.success", moduleName));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                });
    }

    private static int handleModifyShopGroupID(CommandContext<CommandSourceStack> context) {
        int group_id = IntegerArgumentType.getInteger(context, "group_id");
        String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;

        return getMapByName(context)
                .flatMap(map -> getShop(context, map, shopName))
                .map(shop -> {
                    shop.setDefaultShopDataGroupId(shopType, slotNum, group_id);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.group.success", shopType, slotNum, group_id));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                });
    }

    private static int handleModifyCost(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;
        int cost = IntegerArgumentType.getInteger(context, "cost");

        return getMapByName(context)
                .flatMap(map -> getShop(context, map, shopName))
                .map(shop -> {
                    shop.setDefaultShopDataCost(shopType, slotNum, cost);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.cost.success", shopType, slotNum, cost));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                });
    }

    private static int handleModifyItemWithoutValue(CommandContext<CommandSourceStack> context) {
        try {
            Player player = getPlayerOrFail(context);
            String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;

            return getMapByName(context)
                    .flatMap(map -> getShop(context, map, shopName))
                    .map(shop -> {
                        ItemStack itemStack = player.getMainHandItem().copy();
                        if (itemStack.getItem() instanceof IGun iGun) {
                            FPSMUtil.fixGunItem(itemStack, iGun);
                        }
                        shop.setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.item.success",
                                shopType, slotNum, itemStack.getDisplayName()));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                        sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }

    private static int handleModifyItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;

        ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(1, false);

        return getMapByName(context)
                .flatMap(map -> getShop(context, map, shopName))
                .map(shop -> {
                    if (itemStack.getItem() instanceof IGun iGun) {
                        FPSMUtil.fixGunItem(itemStack, iGun);
                    }
                    shop.setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.item.success",
                            shopType, slotNum, itemStack.getDisplayName()));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                });
    }

    private static int handleGunModifyGunAmmoAmount(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(context, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(context, SHOP_SLOT_ARG) - 1;
        int amount = IntegerArgumentType.getInteger(context, "amount");

        return getMapByName(context)
                .flatMap(map -> getShop(context, map, shopName))
                .map(shop -> {
                    ItemStack itemStack = shop.getDefaultShopDataItemStack(shopType, slotNum);
                    if (itemStack.getItem() instanceof IGun iGun) {
                        FPSMUtil.setDummyAmmo(itemStack, iGun, amount);
                    }
                    shop.setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.gun.success",
                            shopType, slotNum, itemStack.getDisplayName(), amount));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                    return 0;
                });
    }

    private static int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
        String type = StringArgumentType.getString(context, GAME_TYPE_ARG);
        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "to");

        Function3<ServerLevel, String, AreaData, BaseMap> game = FPSMCore.getInstance().getPreBuildGame(type);
        if (game != null) {
            BaseMap newMap = game.apply(context.getSource().getLevel(), mapName, new AreaData(pos1, pos2));
            FPSMCore.getInstance().registerMap(type, newMap);
            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.create.success", mapName));
            return 1;
        }
        return 0;
    }

    private static int handleModifyMatchEndTeleportPoint(CommandContext<CommandSourceStack> context) {
        BlockPos point = BlockPosArgument.getBlockPos(context, "point").above();

        return getMapByName(context)
                .filter(map -> map instanceof EndTeleportMap)
                .map(map -> {
                    SpawnPointData pointData = new SpawnPointData(
                            context.getSource().getLevel().dimension(),
                            point, 0f, 0f
                    );
                    ((EndTeleportMap<?>) map).setMatchEndTeleportPoint(pointData);
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.metp.success", pointData.toString()));
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.metp.not_available", mapName));
                    return 0;
                });
    }

    private static int handleBombAreaAction(CommandContext<CommandSourceStack> context) {
        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "to");

        return getMapByName(context)
                .filter(map -> map instanceof BlastModeMap)
                .map(map -> {
                    ((BlastModeMap<?>) map).addBombArea(new AreaData(pos1, pos2));
                    sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.bombarea.success"));
                    return 1;
                })
                .orElseGet(() -> {
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.bombarea.failed"));
                    return 0;
                });
    }

    private static int handleDebugAction(CommandContext<CommandSourceStack> context) {
        String action = StringArgumentType.getString(context, ACTION_ARG);

        return getMapByName(context)
                .map(map -> {
                    switch (action) {
                        case "start":
                            map.startGame();
                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.start.success", map.getMapName()));
                            break;
                        case "reset":
                            map.resetGame();
                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.reset.success", map.getMapName()));
                            break;
                        case "new_round":
                            map.startNewRound();
                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.newround.success", map.getMapName()));
                            break;
                        case "cleanup":
                            map.cleanupMap();
                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.cleanup.success", map.getMapName()));
                            break;
                        case "switch":
                            boolean debug = map.switchDebugMode();
                            context.getSource().sendSuccess(() -> Component.literal("Debug Mode : " + debug), true);
                            break;
                        default:
                            return 0;
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound", mapName));
                    return 0;
                });
    }

    private static int handleJoinMapWithoutTarget(CommandContext<CommandSourceStack> context) {
        return getMapByName(context)
                .map(map -> {
                    map.join(context.getSource().getPlayer());
                    return 1;
                })
                .orElse(0);
    }

    private static int handleJoinMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, TARGETS_ARG);
        return getMapByName(context)
                .map(map -> {
                    players.forEach(map::join);
                    return 1;
                })
                .orElse(0);
    }

    private static int handleLeaveMapWithoutTarget(CommandContext<CommandSourceStack> context) {
        return getMapByName(context)
                .map(map -> {
                    map.leave(context.getSource().getPlayer());
                    return 1;
                })
                .orElse(0);
    }

    private static int handleLeaveMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, TARGETS_ARG);
        return getMapByName(context)
                .map(map -> {
                    players.forEach(map::leave);
                    return 1;
                })
                .orElse(0);
    }

    private static int handleSpecTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, TARGETS_ARG);
        String action = StringArgumentType.getString(context, ACTION_ARG);

        return getMapByName(context)
                .map(map -> {
                    BaseTeam team = map.getMapTeams().getSpectatorTeam();
                    if (team == null) {
                        sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.leave.failure", "spectator"));
                        return 0;
                    }

                    switch (action) {
                        case "join":
                            players.forEach(player -> {
                                map.joinSpec(player);
                                sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.join.success",
                                        player.getDisplayName(), team.getFixedName()));
                            });
                            break;
                        case "leave":
                            players.forEach(player -> {
                                map.leave(player);
                                sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.leave.success",
                                        player.getDisplayName()));
                            });
                            break;
                        default:
                            sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.invalidAction"));
                            return 0;
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                    return 0;
                });
    }

    private static int handleTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, TARGETS_ARG);
        String teamName = StringArgumentType.getString(context, TEAM_NAME_ARG);
        String action = StringArgumentType.getString(context, ACTION_ARG);

        return getMapByName(context)
                .flatMap(map -> map.getMapTeams().getTeamByName(teamName)
                        .map(team -> {
                            switch (action) {
                                case "join":
                                    if (team.getRemainingLimit() - players.size() >= 0) {
                                        players.forEach(player -> {
                                            map.join(teamName, player);
                                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.join.success",
                                                    player.getDisplayName(), team.getFixedName()));
                                        });
                                    } else {
                                        sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.join.failure", team));
                                    }
                                    break;
                                case "leave":
                                    players.forEach(player -> {
                                        map.leave(player);
                                        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.leave.success",
                                                player.getDisplayName()));
                                    });
                                    break;
                                default:
                                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.invalidAction"));
                                    return 0;
                            }
                            return 1;
                        }))
                .orElseGet(() -> {
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                    return 0;
                });
    }

    private static int handleKitsWithoutItemAction(CommandContext<CommandSourceStack> context) {
        String teamName = StringArgumentType.getString(context, TEAM_NAME_ARG);
        String action = StringArgumentType.getString(context, ACTION_ARG);

        try {
            Player player = getPlayerOrFail(context);
            return getMapByName(context)
                    .filter(map -> map instanceof GiveStartKitsMap)
                    .map(map -> handleKitsAction(context, (GiveStartKitsMap<?>) map, teamName, action, player.getMainHandItem().copy()))
                    .orElseGet(() -> {
                        sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                        return 0;
                    });
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }

    private static int handleKitsGunModifyGunAmmoAmount(CommandContext<CommandSourceStack> context) {
        String teamName = StringArgumentType.getString(context, TEAM_NAME_ARG);
        String action = StringArgumentType.getString(context, ACTION_ARG);
        int amount = IntegerArgumentType.getInteger(context, "amount");

        try {
            Player player = getPlayerOrFail(context);
            return getMapByName(context)
                    .filter(map -> map instanceof GiveStartKitsMap)
                    .map(map -> {
                        ItemStack itemStack = player.getMainHandItem().copy();
                        if (itemStack.getItem() instanceof IGun iGun &&
                                TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).isPresent()) {

                            GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).get().getGunData();
                            iGun.useDummyAmmo(itemStack);
                            iGun.setMaxDummyAmmoAmount(itemStack, amount);
                            iGun.setDummyAmmoAmount(itemStack, amount);
                            iGun.setCurrentAmmoCount(itemStack, gunData.getAmmoAmount());
                        }
                        return handleKitsAction(context, (GiveStartKitsMap<?>) map, teamName, action, itemStack);
                    })
                    .orElseGet(() -> {
                        sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                        return 0;
                    });
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }

    private static int handleKitsWithItemAction(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        String teamName = StringArgumentType.getString(context, TEAM_NAME_ARG);
        String action = StringArgumentType.getString(context, ACTION_ARG);
        ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(count, false);

        return getMapByName(context)
                .filter(map -> map instanceof GiveStartKitsMap)
                .map(map -> handleKitsAction(context, (GiveStartKitsMap<?>) map, teamName, action, itemStack))
                .orElseGet(() -> {
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                    return 0;
                });
    }

    private static int handleKitsAction(CommandContext<CommandSourceStack> context, GiveStartKitsMap<?> startKitMap,
                                        String teamName, String action, ItemStack itemStack) {
        return startKitMap.getMap().getMapTeams().getTeamByName(teamName)
                .map(team -> {
                    switch (action) {
                        case "add":
                            startKitMap.addKits(team, itemStack);
                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.kits.add.success",
                                    itemStack.getDisplayName(), teamName));
                            break;
                        case "clear":
                            startKitMap.clearTeamKits(team);
                            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.kits.clear.success", teamName));
                            break;
                        case "list":
                            handleKitsListAction(context, teamName, startKitMap);
                            break;
                        default:
                            sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.kits.invalidAction"));
                            return 0;
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.notFound"));
                    return 0;
                });
    }

    private static void handleKitsListAction(CommandContext<CommandSourceStack> context, String teamName, GiveStartKitsMap<?> startKitMap) {
        startKitMap.getMap().getMapTeams().getTeamByName(teamName).ifPresent(team -> {
            List<ItemStack> itemStacks = startKitMap.getKits(team);
            itemStacks.forEach(itemStack ->
                    context.getSource().sendSuccess(itemStack::getDisplayName, true)
            );
            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.kits.list.success", teamName, itemStacks.size()));
        });
    }

    private static int handleSpawnAction(CommandContext<CommandSourceStack> context) {
        String teamName = StringArgumentType.getString(context, TEAM_NAME_ARG);
        String action = StringArgumentType.getString(context, ACTION_ARG);

        return getMapByName(context)
                .map(map -> switch (action) {
                    case "add" -> handleSpawnAdd(context, map, teamName);
                    case "clear" -> handleSpawnClear(context, map, teamName);
                    case "clear_all" -> handleSpawnClearAll(context, map);
                    case "set" -> handleSpawnSet(context, map, teamName);
                    default -> {
                        sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.invalidAction"));
                        yield 0;
                    }
                })
                .orElseGet(() -> {
                    sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                    return 0;
                });
    }

    private static int handleSpawnAdd(CommandContext<CommandSourceStack> context, BaseMap map, String teamName) {
        if (map.getMapTeams().checkTeam(teamName)) {
            map.getMapTeams().defineSpawnPoint(teamName, getSpawnPointData(context));
            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.add.success", teamName));
            return 1;
        } else {
            sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.notFound"));
            return 0;
        }
    }

    private static int handleSpawnClear(CommandContext<CommandSourceStack> context, BaseMap map, String teamName) {
        if (map.getMapTeams().checkTeam(teamName)) {
            map.getMapTeams().resetSpawnPoints(teamName);
            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.clear.success", teamName));
            return 1;
        } else {
            sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.notFound"));
            return 0;
        }
    }

    private static int handleSpawnClearAll(CommandContext<CommandSourceStack> context, BaseMap map) {
        map.getMapTeams().resetAllSpawnPoints();
        sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.clearall.success"));
        return 1;
    }

    private static int handleSpawnSet(CommandContext<CommandSourceStack> context, BaseMap map, String teamName) {
        if (!map.getMapTeams().checkTeam(teamName)) {
            sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.notFound"));
            return 0;
        }

        try {
            Vec2 from = Vec2Argument.getVec2(context, "from");
            Vec2 to = Vec2Argument.getVec2(context, "to");

            int minX = (int) Math.floor(Math.min(from.x, to.x));
            int maxX = (int) Math.floor(Math.max(from.x, to.x));
            int minZ = (int) Math.floor(Math.min(from.y, to.y));
            int maxZ = (int) Math.floor(Math.max(from.y, to.y));
            int y = BlockPos.containing(context.getSource().getPosition()).getY();

            double border = from.distanceToSqr(to);
            if (border >= 130) {
                sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.set.over_flow"));
                return 0;
            }

            List<BlockPos> airBlocks = new ArrayList<>();
            SpawnPointData defaultData = getSpawnPointData(context);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState block = context.getSource().getLevel().getBlockState(pos);

                    if (block.isAir()) {
                        SpawnPointData data = new SpawnPointData(
                                defaultData.getDimension(), pos,
                                defaultData.getYaw(), defaultData.getPitch()
                        );
                        map.getMapTeams().defineSpawnPoint(teamName, data);
                        airBlocks.add(pos);
                    }
                }
            }

            generateCubeEdgesParticles(context.getSource(), minX, y, minZ, maxX + 1, y + 1, maxZ + 1);
            sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.set.success", airBlocks.size(), teamName));
            return 1;

        } catch (IllegalArgumentException e) {
            sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.set.missing_args"));
            return 0;
        }
    }

    // 辅助方法
    private static SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context) {
        Entity entity = context.getSource().getEntity();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition()).above();

        if (entity != null) {
            return new SpawnPointData(
                    context.getSource().getLevel().dimension(),
                    pos, entity.getXRot(), entity.getYRot()
            );
        } else {
            return new SpawnPointData(
                    context.getSource().getLevel().dimension(),
                    pos, 0f, 0f
            );
        }
    }

    private static void generateCubeEdgesParticles(CommandSourceStack source, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos[][] edges = {
                {new BlockPos(minX, minY, minZ), new BlockPos(maxX, minY, minZ)},
                {new BlockPos(minX, minY, minZ), new BlockPos(minX, maxY, minZ)},
                {new BlockPos(maxX, minY, minZ), new BlockPos(maxX, maxY, minZ)},
                {new BlockPos(minX, maxY, minZ), new BlockPos(maxX, maxY, minZ)},
                {new BlockPos(minX, minY, maxZ), new BlockPos(maxX, minY, maxZ)},
                {new BlockPos(minX, minY, maxZ), new BlockPos(minX, maxY, maxZ)},
                {new BlockPos(maxX, minY, maxZ), new BlockPos(maxX, maxY, maxZ)},
                {new BlockPos(minX, maxY, maxZ), new BlockPos(maxX, maxY, maxZ)},
                {new BlockPos(minX, minY, minZ), new BlockPos(minX, minY, maxZ)},
                {new BlockPos(maxX, minY, minZ), new BlockPos(maxX, minY, maxZ)},
                {new BlockPos(minX, maxY, minZ), new BlockPos(minX, maxY, maxZ)},
                {new BlockPos(maxX, maxY, minZ), new BlockPos(maxX, maxY, maxZ)}
        };

        Arrays.stream(edges).forEach(edge ->
                spawnParticlesAlongEdge(source, edge[0], edge[1])
        );
    }

    private static void spawnParticlesAlongEdge(CommandSourceStack source, BlockPos start, BlockPos end) {
        double x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
        double x2 = end.getX(), y2 = end.getY(), z2 = end.getZ();

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        for (double t = 0; t <= 1; t += PARTICLE_STEP) {
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            double z = z1 + dz * t;

            if (source.getPlayer() != null) {
                source.getLevel().sendParticles(source.getPlayer(), ParticleTypes.FLAME, false,
                        x, y, z, 0, dx, dy, dz, 0.0001);
            }
        }
    }
}