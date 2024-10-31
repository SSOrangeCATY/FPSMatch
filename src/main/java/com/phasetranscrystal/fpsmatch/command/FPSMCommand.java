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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FPSMCommand {
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("fpsm")
                .then(Commands.literal("create")
                        .then(Commands.argument("mapname", StringArgumentType.string())
                                .executes(this::handleCreateMapWithoutSpawnPoint)))
                .then(Commands.literal("modify")
                        .then(Commands.argument("mapname", StringArgumentType.string())
                                .suggests(new MapNameSuggestionProvider())
                                .then(Commands.argument("team", StringArgumentType.string())
                                        .suggests(new TeamsSuggestionProvider())
                                        .then(Commands.literal("spawnpoints")
                                                .then(Commands.argument("action", StringArgumentType.string())
                                                        .suggests(new ActionSuggestionProvider())
                                                        .executes(this::handleAction))))));
        dispatcher.register(literal);
    }
    private int handleCreateMapWithoutSpawnPoint(CommandContext<CommandSourceStack> context) {
        int flag = 1;
        String mapName = StringArgumentType.getString(context, "mapname");
        FPSMCore.registerMap(mapName, new CSGameMap(context.getSource().getLevel(),getSpawnPointData(context)));
        return flag;
    }

    private SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context){
        SpawnPointData data;
        Entity entity = context.getSource().getEntity();
        if(entity!=null){
            data = new SpawnPointData(context.getSource().getLevel().dimension(), entity.getOnPos(),entity.getXRot(),false,false);
        }else{
            Vec3 vec3 = context.getSource().getPosition();
            BlockPos pos = new BlockPos((int) vec3.x, (int) vec3.y, (int) vec3.z);
            data = new SpawnPointData(context.getSource().getLevel().dimension(),pos,0f,false,false);
        }
        return data;
    }

    private int handleAction(CommandContext<CommandSourceStack> context) {
        int flag = 1;
        String mapname = StringArgumentType.getString(context, "mapname");
        String team = StringArgumentType.getString(context, "team");
        String action = StringArgumentType.getString(context, "action");
        BaseMap map = FPSMCore.getMapByName(mapname);
        if (map != null) {
            switch (action) {
                case "add":
                    // 处理设置团队出生点的逻辑
                    if(map.getMapTeams().checkTeam(team)) map.getMapTeams().defineSpawnPoint(team,getSpawnPointData(context));
                    break;
                case "clear":
                    // 处理清除团队出生点的逻辑
                    if(map.getMapTeams().checkTeam(team)) map.getMapTeams().resetSpawnPoints(team);
                    break;
                case "clearall":
                    // 处理清除所有团队出生点的逻辑
                    map.getMapTeams().resetAllSpawnPoints();
                    break;
                default:
                    // 无效的参数
                    flag = 0;
                    break;
            }

        }else{
            flag = 0;
        }

        return flag;
    }


    private static class TeamsSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return CompletableFuture.supplyAsync(() -> {
                BaseMap map = FPSMCore.getMapByName(StringArgumentType.getString(context, "mapname"));
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


    private static class ActionSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return CompletableFuture.supplyAsync(() -> {
                List<String> suggests = List.of("add","clear","clearall");
                for (String mapName : suggests) {
                    if (builder.getRemaining().isEmpty() || mapName.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                        builder.suggest(mapName);
                    }
                }
                return new Suggestions(new StringRange(builder.getStart(), builder.build().getList().size()), builder.build().getList());
            });
        }
    }
}