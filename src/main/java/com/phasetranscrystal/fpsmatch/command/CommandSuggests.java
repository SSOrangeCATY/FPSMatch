package com.phasetranscrystal.fpsmatch.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class CommandSuggests {
    public static final FPSMSuggestionProvider MAP_NAMES_SUGGESTION = new FPSMSuggestionProvider((c, b)-> FPSMCommand.getSuggestions(b, FPSMCore.getMapNames()));
    public static final FPSMSuggestionProvider MAP_NAMES_WITH_GAME_TYPE_SUGGESTION = new FPSMSuggestionProvider((c, b)-> FPSMCommand.getSuggestions(b, FPSMCore.getMapNames(StringArgumentType.getString(c, "gameType"))));
    public static final FPSMSuggestionProvider MAP_DEBUG_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommand.getSuggestions(b, List.of("start","reset","newround","cleanup","switch")));
    public static final FPSMSuggestionProvider TEAM_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommand.getSuggestions(b, List.of("join","leave")));
    public static final FPSMSuggestionProvider SPAWNPOINTS_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommand.getSuggestions(b, List.of("add","clear","clearall")));


    public record FPSMSuggestionProvider(
            BiFunction<CommandContext<CommandSourceStack>, SuggestionsBuilder, Suggestions> suggestions) implements SuggestionProvider<CommandSourceStack> {
        @Override
            public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
                return CompletableFuture.supplyAsync(() -> this.suggestions.apply(context, builder));
            }
        }
}
