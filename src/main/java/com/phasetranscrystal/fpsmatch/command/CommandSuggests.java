package com.phasetranscrystal.fpsmatch.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class CommandSuggests {
    public static final FPSMSuggestionProvider GAME_TYPES_SUGGESTION = new FPSMSuggestionProvider((c, b)-> CommandSuggests.getSuggestions(b, FPSMCore.getInstance().getGameTypes()));
    public static final FPSMSuggestionProvider MAP_NAMES_SUGGESTION = new FPSMSuggestionProvider((c, b)-> CommandSuggests.getSuggestions(b, FPSMCore.getInstance().getMapNames()));
    public static final FPSMSuggestionProvider MAP_NAMES_WITH_GAME_TYPE_SUGGESTION = new FPSMSuggestionProvider((c, b)-> CommandSuggests.getSuggestions(b, FPSMCore.getInstance().getMapNames(StringArgumentType.getString(c, "gameType"))));
    public static final FPSMSuggestionProvider MAP_NAMES_WITH_IS_ENABLE_SHOP_SUGGESTION = new FPSMSuggestionProvider((c, b)-> CommandSuggests.getSuggestions(b, FPSMCore.getInstance().getEnableShopGames()));
    public static final FPSMSuggestionProvider TEAM_NAMES_SUGGESTION = new FPSMSuggestionProvider((c,b)-> {
        BaseMap map = FPSMCore.getInstance().getMapByName(StringArgumentType.getString(c, "mapName"));
        Suggestions suggestions = CommandSuggests.getSuggestions(b, new ArrayList<>());
        if (map != null){
            suggestions = CommandSuggests.getSuggestions(b, map.getMapTeams().getTeamsName());
        }
        return suggestions;
    });
    public static final FPSMSuggestionProvider MAP_DEBUG_SUGGESTION = new FPSMSuggestionProvider((c,b)-> CommandSuggests.getSuggestions(b, List.of("start","reset","newRound","cleanup","switch")));
    public static final FPSMSuggestionProvider TEAM_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> CommandSuggests.getSuggestions(b, List.of("join","leave")));
    public static final FPSMSuggestionProvider SPAWNPOINTS_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> CommandSuggests.getSuggestions(b, List.of("add","clear","clearall","set")));
    public static final FPSMSuggestionProvider SKITS_SUGGESTION = new FPSMSuggestionProvider((c,b)-> CommandSuggests.getSuggestions(b, List.of("add","clear","list")));

    public static final FPSMSuggestionProvider SHOP_ITEM_TYPES_SUGGESTION = new FPSMSuggestionProvider((c,b)-> {
        ItemType[] types = ItemType.values();
        List<String> typeNames = new ArrayList<>();
        for (ItemType t : types){
            typeNames.add(t.name().toLowerCase());
        }
        return CommandSuggests.getSuggestions(b,typeNames);
    });

    public static final FPSMSuggestionProvider SHOP_NAMES_SUGGESTION = new FPSMSuggestionProvider((c,b)-> {
        String mapName = StringArgumentType.getString(c, "mapName");
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        List<String> names = new ArrayList<>();
        if (map instanceof ShopMap<?> shopMap) {
            return CommandSuggests.getSuggestions(b,shopMap.getShopNames());
        }
        return CommandSuggests.getSuggestions(b,names);
    });

    public static final FPSMSuggestionProvider SHOP_SET_SLOT_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> CommandSuggests.getSuggestions(b, List.of("1","2","3","4","5")));
    public static final FPSMSuggestionProvider SHOP_SLOT_ADD_LISTENER_MODULES_SUGGESTION = new FPSMSuggestionProvider((c,b)->
    {
        String mapName = StringArgumentType.getString(c, "mapName");
        String shopName = StringArgumentType.getString(c, "shopName");
        ItemType shopType = ItemType.valueOf(StringArgumentType.getString(c, "shopType").toUpperCase(Locale.ROOT));
        int slotNum = IntegerArgumentType.getInteger(c,"shopSlot") - 1;
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map instanceof ShopMap<?> shopMap) {
            List<String> stringList = FPSMatch.listenerModuleManager.getListenerModules();
            stringList.removeAll(shopMap.getShop(shopName).getDefaultShopData().getShopSlotsByType(shopType).get(slotNum).getListenerNames());
            return CommandSuggests.getSuggestions(b, stringList);
        }
        return CommandSuggests.getSuggestions(b, new ArrayList<>());
    });

    public static final FPSMSuggestionProvider SHOP_SLOT_REMOVE_LISTENER_MODULES_SUGGESTION = new FPSMSuggestionProvider((c,b)->
    {
        String mapName = StringArgumentType.getString(c, "mapName");
        String shopName = StringArgumentType.getString(c, "shopName");
        ItemType shopType = ItemType.valueOf(StringArgumentType.getString(c, "shopType").toUpperCase(Locale.ROOT));
        int slotNum = IntegerArgumentType.getInteger(c,"shopSlot") - 1;
        BaseMap map = FPSMCore.getInstance().getMapByName(mapName);
        if (map instanceof ShopMap<?> shopMap) {
            List<String> stringList = shopMap.getShop(shopName).getDefaultShopData().getShopSlotsByType(shopType).get(slotNum).getListenerNames();
            return CommandSuggests.getSuggestions(b, stringList);
        }
        return CommandSuggests.getSuggestions(b, new ArrayList<>());});



    public record FPSMSuggestionProvider(BiFunction<CommandContext<CommandSourceStack>, SuggestionsBuilder, Suggestions> suggestions) implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
                return CompletableFuture.supplyAsync(() -> this.suggestions.apply(context, builder));
        }
    }
    @NotNull
    public static Suggestions getSuggestions(SuggestionsBuilder builder, List<String> suggests) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String suggest : suggests) {
            if (suggest.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(suggest);
            }
        }

        return builder.build();
    }
}
