package com.phasetranscrystal.fpsmatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.cs.CSGameMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FPSMCommand {
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("fpsm")
                .then(Commands.literal("create")
                        .then(Commands.argument("mapName", StringArgumentType.string())
                                .executes(this::handleCreateMapWithoutSpawnPoint)))
                .then(Commands.literal("map")
                        .then(Commands.argument("mapName", StringArgumentType.string())
                                .suggests(new MapNameSuggestionProvider())
                                .then(Commands.literal("team")
                                        .then(Commands.argument("teamName", StringArgumentType.string())
                                                .suggests(new TeamsSuggestionProvider())
                                                .then(Commands.argument("action", StringArgumentType.string())
                                                        .suggests(new ActionTeamSuggestionProvider())
                                                        .executes(this::handleTeamAction))))))
                .then(Commands.literal("modify")
                        .then(Commands.argument("mapName", StringArgumentType.string())
                                .suggests(new MapNameSuggestionProvider())
                                .then(Commands.argument("team", StringArgumentType.string())
                                        .suggests(new TeamsSuggestionProvider())
                                        .then(Commands.literal("spawnpoints")
                                                .then(Commands.argument("action", StringArgumentType.string())
                                                        .suggests(new ActionSpawnSuggestionProvider())
                                                        .executes(this::handleSpawnAction))))));
        dispatcher.register(literal);
    }
    private int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, "mapName");
        FPSMCore.registerMap(mapName, new CSGameMap(context.getSource().getLevel(), getSpawnPointData(context)));
        context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.create.success", mapName), true);
        return 1;
    }

    private SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context){
        SpawnPointData data;
        Entity entity = context.getSource().getEntity();
        if(entity!=null){
            data = new SpawnPointData(context.getSource().getLevel().dimension(), entity.getOnPos(),entity.getXRot(),entity.getYRot());
        }else{
            Vec3 vec3 = context.getSource().getPosition();
            BlockPos pos = new BlockPos((int) vec3.x, (int) vec3.y, (int) vec3.z);
            data = new SpawnPointData(context.getSource().getLevel().dimension(),pos,0f,0f);
        }
        return data;
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
                    case "start":
                        ((CSGameMap) map).startGame();
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
        String team = StringArgumentType.getString(context, "team");
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


    private static class TeamsSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return CompletableFuture.supplyAsync(() -> {
                BaseMap map = FPSMCore.getMapByName(StringArgumentType.getString(context, "mapName"));
                Suggestions suggestions = new Suggestions(new StringRange(builder.getStart(), builder.build().getList().size()), builder.build().getList());
                if (map != null){
                    for (String teamsName : map.getMapTeams().getTeamsName()) {
                        if (builder.getRemaining().isEmpty() || teamsName.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                            builder.suggest(teamsName);
                        }
                    }
                    suggestions = new Suggestions(new StringRange(builder.getStart(), builder.build().getList().size()), builder.build().getList());
                }
                return suggestions;
            });
        }
    }


    private static class MapNameSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return CompletableFuture.supplyAsync(() -> {

                for (String mapName : FPSMCore.getMapNames()) {
                    if (builder.getRemaining().isEmpty() || mapName.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                        builder.suggest(mapName);
                    }
                }
                return new Suggestions(new StringRange(builder.getStart(), builder.build().getList().size()), builder.build().getList());
            });
        }
    }
    private static class ActionTeamSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return CompletableFuture.supplyAsync(() -> {
                List<String> suggests = List.of("join","leave");
                return FPSMCommand.getSuggestions(builder, suggests);
            });
        }
    }
    private static class ActionSpawnSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return CompletableFuture.supplyAsync(() -> {
                List<String> suggests = List.of("add","clear","clearall");
                return FPSMCommand.getSuggestions(builder, suggests);
            });
        }
    }


    @NotNull
    public static Suggestions getSuggestions(SuggestionsBuilder builder, List<String> suggests) {
        for (String mapName : suggests) {
            if (builder.getRemaining().isEmpty() || mapName.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mapName);
            }
        }
        return new Suggestions(new StringRange(builder.getStart(), builder.build().getList().size()), builder.build().getList());
    }
}