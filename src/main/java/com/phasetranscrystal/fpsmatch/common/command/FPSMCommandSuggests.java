package com.phasetranscrystal.fpsmatch.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class FPSMCommandSuggests {
    public static final String MAP_NAME_ARG = "map_name";
    public static final String GAME_TYPE_ARG = "game_type";
    public static final String SHOP_NAME_ARG = "shop_name";
    public static final String SHOP_TYPE_ARG = "shop_type";
    public static final String SHOP_SLOT_ARG = "shop_slot";
    public static final String TEAM_NAME_ARG = "team_name";
    public static final String ACTION_ARG = "action";
    public static final String TARGETS_ARG = "targets";
    
    public static final FPSMSuggestionProvider GAME_TYPES_SUGGESTION = new FPSMSuggestionProvider((c, b)-> FPSMCommandSuggests.getSuggestions(b, FPSMCore.getInstance().getGameTypes()));
    public static final FPSMSuggestionProvider MAP_NAMES_WITH_GAME_TYPE_SUGGESTION = new FPSMSuggestionProvider((c, b)-> FPSMCommandSuggests.getSuggestions(b, FPSMCore.getInstance().getMapNames(StringArgumentType.getString(c, GAME_TYPE_ARG))));
    public static final FPSMSuggestionProvider MAP_NAMES_WITH_IS_ENABLE_SHOP_SUGGESTION = new FPSMSuggestionProvider((c, b)-> FPSMCommandSuggests.getSuggestions(b, FPSMCore.getInstance().getEnableShopGames(StringArgumentType.getString(c, GAME_TYPE_ARG))));
    public static final FPSMSuggestionProvider TEAM_NAMES_SUGGESTION = new FPSMSuggestionProvider((c,b)-> {
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(StringArgumentType.getString(c, MAP_NAME_ARG));
        Suggestions suggestions = FPSMCommandSuggests.getSuggestions(b, new ArrayList<>());
        if (map.isPresent()){
            suggestions = FPSMCommandSuggests.getSuggestions(b, map.get().getMapTeams().getNormalTeamsName());
        }
        return suggestions;
    });

    public static final FPSMSuggestionProvider MAP_DEBUG_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommandSuggests.getSuggestions(b, List.of("start","reset","new_round","cleanup","switch")));
    public static final FPSMSuggestionProvider TEAM_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommandSuggests.getSuggestions(b, List.of("join","leave")));
    public static final FPSMSuggestionProvider SPAWNPOINTS_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommandSuggests.getSuggestions(b, List.of("add","clear","clear_all","set")));
    public static final FPSMSuggestionProvider SKITS_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommandSuggests.getSuggestions(b, List.of("add","clear","list")));

    public static final FPSMSuggestionProvider TEAM_CAPABILITIES_ARGS = new FPSMSuggestionProvider((c,b)-> {
        String mapName = StringArgumentType.getString(c, MAP_NAME_ARG);
        String teamName = StringArgumentType.getString(c, TEAM_NAME_ARG);
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(mapName);
        List<String> capabilities = new ArrayList<>();
        map.flatMap(baseMap -> baseMap.getMapTeams().getTeamByName(teamName)).ifPresent(team -> {
            team.getCapabilityMap().values().forEach(
                    cap->{
                        FPSMCapabilityManager.getRawFactory(cap.getClass()).ifPresent(
                                factory->{
                                    TeamCapability.Factory.Command command = factory.command();
                                    if(factory.command() != null){
                                        capabilities.add(command.getName());
                                    }
                                }
                        );
                    }
            );
        });

        return FPSMCommandSuggests.getSuggestions(b,capabilities);
    });

    public static final FPSMSuggestionProvider SHOP_ITEM_TYPES_SUGGESTION = new FPSMSuggestionProvider((c,b)-> {
        String mapName = StringArgumentType.getString(c, MAP_NAME_ARG);
        String shopName = StringArgumentType.getString(c, SHOP_NAME_ARG);
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(mapName);
        List<String> typeNames = new ArrayList<>();
        if(map.isPresent() && map.get() instanceof ShopMap<?> shopMap){
            shopMap.getShop(shopName).ifPresent(shop -> {
                for (Enum<?> t : shop.getEnums()){
                    typeNames.add(t.name().toLowerCase());
                }
            });
        }
        return FPSMCommandSuggests.getSuggestions(b,typeNames);
    });

    public static final FPSMSuggestionProvider SHOP_NAMES_SUGGESTION = new FPSMSuggestionProvider((c,b)-> {
        String mapName = StringArgumentType.getString(c, MAP_NAME_ARG);
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(mapName);
        List<String> names = new ArrayList<>();
        if (map.isPresent() && map.get() instanceof ShopMap<?> shopMap) {
            return FPSMCommandSuggests.getSuggestions(b,shopMap.getShopNames());
        }
        return FPSMCommandSuggests.getSuggestions(b,names);
    });

    public static final FPSMSuggestionProvider SHOP_SET_SLOT_ACTION_SUGGESTION = new FPSMSuggestionProvider((c,b)-> FPSMCommandSuggests.getSuggestions(b, List.of("1","2","3","4","5")));
    public static final FPSMSuggestionProvider SHOP_SLOT_ADD_LISTENER_MODULES_SUGGESTION = new FPSMSuggestionProvider((c,b)->
    {
        String mapName = StringArgumentType.getString(c, MAP_NAME_ARG);
        String shopName = StringArgumentType.getString(c, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(c, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(c,SHOP_SLOT_ARG) - 1;
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(mapName);
        if (map.isPresent() && map.get() instanceof ShopMap<?> shopMap) {
            Optional<FPSMShop<?>> optionalShop = shopMap.getShop(shopName);
            if (optionalShop.isPresent()) {
                List<String> stringList = FPSMCore.getInstance().getListenerModuleManager().getListenerModules();
                stringList.removeAll(optionalShop.get().getDefaultShopSlotListByType(shopType).get(slotNum).getListenerNames());
                return FPSMCommandSuggests.getSuggestions(b, stringList);
            }
        }
        return FPSMCommandSuggests.getSuggestions(b, new ArrayList<>());
    });

    public static final FPSMSuggestionProvider SHOP_SLOT_REMOVE_LISTENER_MODULES_SUGGESTION = new FPSMSuggestionProvider((c,b)->
    {
        String mapName = StringArgumentType.getString(c, MAP_NAME_ARG);
        String shopName = StringArgumentType.getString(c, SHOP_NAME_ARG);
        String shopType = StringArgumentType.getString(c, SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
        int slotNum = IntegerArgumentType.getInteger(c,SHOP_SLOT_ARG) - 1;
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(mapName);
        if (map.isPresent() && map.get() instanceof ShopMap<?> shopMap) {
            Optional<FPSMShop<?>> optionalShop = shopMap.getShop(shopName);
            if (optionalShop.isPresent()) {
                List<String> stringList = optionalShop.get().getDefaultShopSlotListByType(shopType).get(slotNum).getListenerNames();
                return getSuggestions(b, stringList);
            }
        }
        return getSuggestions(b, new ArrayList<>());});


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
