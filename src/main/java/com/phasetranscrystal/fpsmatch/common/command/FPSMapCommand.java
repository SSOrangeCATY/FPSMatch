package com.phasetranscrystal.fpsmatch.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;



public class FPSMapCommand {
    // 构建map modify命令树
    public static LiteralArgumentBuilder<CommandSourceStack> init(Pair<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> builder) {
        return builder.getFirst()
                .then(Commands.literal("create")
                        .then(Commands.argument(FPSMCommandSuggests.GAME_TYPE_ARG, StringArgumentType.string())
                                .suggests(FPSMCommandSuggests.GAME_TYPES_SUGGESTION)
                                .then(Commands.argument(FPSMCommandSuggests.MAP_NAME_ARG, StringArgumentType.string())
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(FPSMapCommand::handleCreateMapWithoutSpawnPoint))))))
                .then(Commands.literal("modify")
                        .then(Commands.argument(FPSMCommandSuggests.GAME_TYPE_ARG, StringArgumentType.string())
                                .suggests(FPSMCommandSuggests.GAME_TYPES_SUGGESTION)
                                .then(Commands.argument(FPSMCommandSuggests.MAP_NAME_ARG, StringArgumentType.string())
                                        .suggests(FPSMCommandSuggests.MAP_NAMES_WITH_GAME_TYPE_SUGGESTION)
                                        // 调试操作
                                        .then(Commands.literal("debug")
                                                .then(Commands.argument(FPSMCommandSuggests.ACTION_ARG, StringArgumentType.string())
                                                        .suggests(FPSMCommandSuggests.MAP_DEBUG_SUGGESTION)
                                                        .executes(FPSMapCommand::handleDebugAction)))
                                        .then(buildMapCapabilityCommands(builder.getSecond()))
                                        // 团队相关修改
                                        .then(Commands.literal("team")
                                                .then(Commands.literal("join")
                                                        .executes(FPSMapCommand::handleJoinMapWithoutTarget)
                                                        .then(Commands.argument(FPSMCommandSuggests.TARGETS_ARG, EntityArgument.players())
                                                                .executes(FPSMapCommand::handleJoinMapWithTarget)))
                                                .then(Commands.literal("leave")
                                                        .executes(FPSMapCommand::handleLeaveMapWithoutTarget)
                                                        .then(Commands.argument(FPSMCommandSuggests.TARGETS_ARG, EntityArgument.players())
                                                                .executes(FPSMapCommand::handleLeaveMapWithTarget)))
                                                .then(Commands.literal("teams")
                                                        .then(Commands.argument(FPSMCommandSuggests.TEAM_NAME_ARG, StringArgumentType.string())
                                                                .suggests(FPSMCommandSuggests.TEAM_NAMES_SUGGESTION)
                                                                .then(buildTeamCapabilityCommands(builder.getSecond()))
                                                                .then(Commands.literal("players")
                                                                        .then(Commands.argument(FPSMCommandSuggests.TARGETS_ARG, EntityArgument.players())
                                                                                .then(Commands.argument(FPSMCommandSuggests.ACTION_ARG, StringArgumentType.string())
                                                                                        .suggests(FPSMCommandSuggests.TEAM_ACTION_SUGGESTION)
                                                                                        .executes(FPSMapCommand::handleTeamAction)
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMapCapabilityCommands(CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("capability");
        // 遍历所有注册的TeamCapability
        for (Class<? extends FPSMCapability<?>> capClass : FPSMCapabilityManager.getRegisteredCapabilities(FPSMCapabilityManager.CapabilityType.MAP)) {
            // 获取Capability的Factory
            FPSMCapabilityManager.getRawFactory(capClass).ifPresent(factory -> {
                // 获取Factory中定义的指令
                FPSMCapability.Factory.Command command = factory.command();
                if (command != null) {
                    root.then(command.builder(Commands.literal(command.getName()), context));
                }
            });
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTeamCapabilityCommands(CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("capability");
        // 遍历所有注册的TeamCapability
        for (Class<? extends FPSMCapability<?>> capClass : FPSMCapabilityManager.getRegisteredCapabilities(FPSMCapabilityManager.CapabilityType.TEAM)) {
            // 获取Capability的Factory
            FPSMCapabilityManager.getRawFactory(capClass).ifPresent(factory -> {
                // 获取Factory中定义的指令
                FPSMCapability.Factory.Command command = factory.command();
                if (command != null) {
                    // 调用指令的builder方法，挂载到当前节点
                    root.then(command.builder(Commands.literal(command.getName()), context));
                }
            });
        }
        return root;
    }

    private static int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
        String type = StringArgumentType.getString(context, FPSMCommandSuggests.GAME_TYPE_ARG);
        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "from");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "to");

        Function3<ServerLevel, String, AreaData, BaseMap> game = FPSMCore.getInstance().getPreBuildGame(type);
        if (game != null) {
            BaseMap newMap = game.apply(context.getSource().getLevel(), mapName, new AreaData(pos1, pos2));
            FPSMCore.getInstance().registerMap(type, newMap);
            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.create.success", mapName));
            return 1;
        }
        return 0;
    }

    private static int handleDebugAction(CommandContext<CommandSourceStack> context) {
        String action = StringArgumentType.getString(context, FPSMCommandSuggests.ACTION_ARG);

        return FPSMCommand.getMapByName(context)
                .map(map -> {
                    switch (action) {
                        case "start":
                            map.start();
                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.start.success", map.getMapName()));
                            break;
                        case "reset":
                            map.reset();
                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.reset.success", map.getMapName()));
                            break;
                        case "new_round":
                            map.startNewRound();
                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.newround.success", map.getMapName()));
                            break;
                        case "cleanup":
                            map.cleanupMap();
                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.debug.cleanup.success", map.getMapName()));
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
                    String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                    FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound", mapName));
                    return 0;
                });
    }

    // ------------------------------ 团队相关处理方法 ------------------------------
    private static int handleJoinMapWithoutTarget(CommandContext<CommandSourceStack> context) {
        return FPSMCommand.getMapByName(context)
                .map(map -> {
                    map.join(context.getSource().getPlayer());
                    return 1;
                })
                .orElse(0);
    }

    private static int handleJoinMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, FPSMCommandSuggests.TARGETS_ARG);
        return FPSMCommand.getMapByName(context)
                .map(map -> {
                    players.forEach(map::join);
                    return 1;
                })
                .orElse(0);
    }

    private static int handleLeaveMapWithoutTarget(CommandContext<CommandSourceStack> context) {
        return FPSMCommand.getMapByName(context)
                .map(map -> {
                    map.leave(context.getSource().getPlayer());
                    return 1;
                })
                .orElse(0);
    }

    private static int handleLeaveMapWithTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, FPSMCommandSuggests.TARGETS_ARG);
        return FPSMCommand.getMapByName(context)
                .map(map -> {
                    players.forEach(map::leave);
                    return 1;
                })
                .orElse(0);
    }

    private static int handleSpecTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, FPSMCommandSuggests.TARGETS_ARG);
        String action = StringArgumentType.getString(context, FPSMCommandSuggests.ACTION_ARG);

        return FPSMCommand.getMapByName(context)
                .map(map -> {
                    BaseTeam team = map.getMapTeams().getSpectatorTeam();
                    if (team == null) {
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.leave.failure", "spectator"));
                        return 0;
                    }

                    switch (action) {
                        case "join":
                            players.forEach(player -> {
                                map.join("spectator", player);
                                FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.join.success",
                                        player.getDisplayName(), team.getFixedName()));
                            });
                            break;
                        case "leave":
                            players.forEach(player -> {
                                map.leave(player);
                                FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.leave.success",
                                        player.getDisplayName()));
                            });
                            break;
                        default:
                            FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.invalidAction"));
                            return 0;
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                    return 0;
                });
    }

    private static int handleTeamAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, FPSMCommandSuggests.TARGETS_ARG);
        String teamName = StringArgumentType.getString(context, FPSMCommandSuggests.TEAM_NAME_ARG);
        String action = StringArgumentType.getString(context, FPSMCommandSuggests.ACTION_ARG);

        return FPSMCommand.getMapByName(context)
                .flatMap(map -> map.getMapTeams().getTeamByName(teamName)
                        .map(team -> {
                            switch (action) {
                                case "join":
                                    if (team.getRemainingLimit() - players.size() >= 0) {
                                        players.forEach(player -> {
                                            map.join(teamName, player);
                                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.join.success",
                                                    player.getDisplayName(), team.getFixedName()));
                                        });
                                    } else {
                                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.join.failure", team));
                                    }
                                    break;
                                case "leave":
                                    players.forEach(player -> {
                                        map.leave(player);
                                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.team.leave.success",
                                                player.getDisplayName()));
                                    });
                                    break;
                                default:
                                    FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.invalidAction"));
                                    return 0;
                            }
                            return 1;
                        }))
                .orElseGet(() -> {
                    FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.map.notFound"));
                    return 0;
                });
    }

}
