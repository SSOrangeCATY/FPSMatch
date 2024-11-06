package com.phasetranscrystal.fpsmatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.tacz.guns.api.item.IGun;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class FPSMCommand {
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("fpsm")
                .then(Commands.literal("shop")
                        .then(Commands.argument("gameType", StringArgumentType.string())
                                .suggests(CommandSuggests.MAP_NAMES_WITH_IS_ENABLE_SHOP_SUGGESTION)
                                .then(Commands.argument("mapName", StringArgumentType.string())
                                        .suggests(CommandSuggests.MAP_NAMES_WITH_GAME_TYPE_SUGGESTION)
                                        .then(Commands.literal("modify")
                                                .then(Commands.argument("action",StringArgumentType.string())
                                                        .suggests(CommandSuggests.SHOP_ACTION_SUGGESTION)
                                                        .then(Commands.argument("shopType",StringArgumentType.string())
                                                                .suggests(CommandSuggests.SHOP_ITEM_TYPES_SUGGESTION)
                                                                .then(Commands.argument("shopSlot", IntegerArgumentType.integer(1,5))
                                                                        .suggests(CommandSuggests.SHOP_SET_SLOT_ACTION_SUGGESTION)
                                                                        .then(Commands.argument("cost", IntegerArgumentType.integer(1))
                                                                                .executes(this::handleModifyShop)))))))))
                .then(Commands.literal("map")
                        .then(Commands.literal("create")
                                .then(Commands.argument("gameType", StringArgumentType.string())
                                        .suggests(CommandSuggests.GAME_TYPES_SUGGESTION)
                                        .then(Commands.argument("mapName", StringArgumentType.string())
                                                .executes(this::handleCreateMapWithoutSpawnPoint))))
                        .then(Commands.literal("modify")
                                .then(Commands.argument("mapName", StringArgumentType.string())
                                .suggests(CommandSuggests.MAP_NAMES_SUGGESTION)
                                        .then(Commands.literal("debug")
                                                .then(Commands.argument("action", StringArgumentType.string())
                                                        .suggests(CommandSuggests.MAP_DEBUG_SUGGESTION)
                                                        .executes(this::handleDebugAction)))
                                        .then(Commands.literal("team")
                                                .then(Commands.argument("teamName", StringArgumentType.string())
                                                .suggests(CommandSuggests.TEAM_NAMES_SUGGESTION)
                                                        .then(Commands.literal("spawnpoints")
                                                                .then(Commands.argument("action", StringArgumentType.string())
                                                                        .suggests(CommandSuggests.SPAWNPOINTS_ACTION_SUGGESTION)
                                                                        .executes(this::handleSpawnAction)))
                                                        .then(Commands.argument("action", StringArgumentType.string())
                                                                .suggests(CommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                .executes(this::handleTeamAction)))))));
        dispatcher.register(literal);
    }
    private int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String type = StringArgumentType.getString(context, "gameType");
        Function3<ServerLevel,List<String>,String,BaseMap> game = FPSMCore.getPreBuildGame(type);
        if(game != null){
            BaseMap map = FPSMCore.registerMap(type, game.apply(context.getSource().getLevel(),new ArrayList<>(),mapName));
            if(map != null) {
                map.setShopData();
            }else return 0;
            context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.create.success", mapName), true);
            return 1;
        }else{
            return 0;
        }
    }

    private SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context){
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

    private int handleModifyShop(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String action = StringArgumentType.getString(context, "action");
        String shopType = StringArgumentType.getString(context, "shopType");
        int slotNum = IntegerArgumentType.getInteger(context,"shopSlot");
        int cost = IntegerArgumentType.getInteger(context,"cost");
        BaseMap map = FPSMCore.getMapByName(mapName);

        if (map != null) {
            switch (action) {
                case "set": {
                    if (context.getSource().getEntity() instanceof Player player){
                        ItemStack itemStack = player.getMainHandItem();
                        FPSMShop.putShopData(mapName,new ShopData.ShopSlot(slotNum - 1, ShopData.ItemType.valueOf(shopType.toUpperCase(Locale.ROOT)),itemStack,cost));
                    }
                }
                case "reset" : {
                }
                case "sync" :{
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        FPSMShop.syncShopData(mapName, player);
                    }
                }
            }
        }
        return 1;
    }

    private int handleDebugAction(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getMapByName(mapName);
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
                case "newround":
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

    private int handleTeamAction(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String team = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getMapByName(mapName);
        if (context.getSource().getEntity() instanceof Player player) {
            if (map != null) {
                switch (action) {
                    case "join":
                        if (map.getMapTeams().checkTeam(team)) {
                            map.getMapTeams().joinTeam(team, player);
                            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.team.join.success", player.getDisplayName(), team), true);
                        } else {
                            context.getSource().sendFailure(Component.translatable("commands.fpsm.team.join.failure", team));
                        }
                        break;
                    case "leave":
                        if (map.getMapTeams().checkTeam(team)) {
                            map.getMapTeams().leaveTeam(player);
                            context.getSource().sendSuccess(()-> Component.translatable("commands.fpsm.team.leave.success", player.getDisplayName()), true);
                        } else {
                            context.getSource().sendFailure(Component.translatable("commands.fpsm.team.leave.failure", team));
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
        } else {
            context.getSource().sendFailure(Component.literal("[FPSM] 执行失败,执行对象不是玩家！"));
            return 0;
        }
        return 1;
    }

    private int handleSpawnAction(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        String team = StringArgumentType.getString(context, "teamName");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getMapByName(mapName);

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