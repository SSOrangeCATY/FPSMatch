package com.tacz.guns.client.recipe;

import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ClientGunSmithRecipeRepository {
    private ClientGunSmithRecipeRepository() {
    }

    public enum SourceKind {
        SINGLEPLAYER_SERVER("singleplayer_server"),
        NETWORK_CACHE("network_cache"),
        COMMON_ASSETS("common_assets");

        private final String logName;

        SourceKind(String logName) {
            this.logName = logName;
        }

        public String logName() {
            return logName;
        }
    }

    public record Source(SourceKind kind, List<Map.Entry<Identifier, GunSmithTableRecipe>> recipes) {
        @Nullable
        public GunSmithTableRecipe get(Identifier recipeId) {
            for (Map.Entry<Identifier, GunSmithTableRecipe> entry : recipes) {
                if (entry.getKey().equals(recipeId)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        public Map<Identifier, GunSmithTableRecipe> asMap() {
            Map<Identifier, GunSmithTableRecipe> map = new LinkedHashMap<>();
            for (Map.Entry<Identifier, GunSmithTableRecipe> entry : recipes) {
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }
    }

    public static List<Source> getSources() {
        List<Source> sources = new ArrayList<>();
        IdentityHashMap<RecipeManager, Boolean> seenRecipeManagers = new IdentityHashMap<>();

        // The synchronized cache is already materialized for client display. Prefer it over raw
        // server managers so integrated clients cannot shadow good client data with early-init recipes.
        List<Map.Entry<Identifier, GunSmithTableRecipe>> networkRecipes = initializedEntries(
                SourceKind.NETWORK_CACHE,
                CommonNetworkCache.INSTANCE.getAllRecipes()
        );
        sources.add(new Source(SourceKind.NETWORK_CACHE, networkRecipes));

        // MC 26.1 ClientLevel only exposes RecipeAccess display/property data, not raw custom recipe holders.
        // Remote clients therefore use TACZ's network cache; integrated clients can still fall back to the server RecipeManager.
        RecipeManager serverRecipeManager = getSingleplayerServerRecipeManager();
        if (serverRecipeManager != null) {
            addRecipeManagerSource(sources, seenRecipeManagers, SourceKind.SINGLEPLAYER_SERVER, serverRecipeManager);
        }

        CommonAssetsManager assetsManager = CommonAssetsManager.getInstance();
        if (assetsManager != null && assetsManager.recipeManager != null) {
            addRecipeManagerSource(sources, seenRecipeManagers, SourceKind.COMMON_ASSETS, assetsManager.recipeManager);
        }

        return sources;
    }

    public static Optional<GunSmithTableRecipe> getRecipe(Identifier recipeId) {
        for (Source source : getSources()) {
            GunSmithTableRecipe recipe = source.get(recipeId);
            if (recipe != null && isDisplayable(recipe)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static Map<Identifier, GunSmithTableRecipe> getFirstAvailableRecipeMap() {
        Map<Identifier, GunSmithTableRecipe> bestRecipes = Map.of();
        for (Source source : getSources()) {
            Map<Identifier, GunSmithTableRecipe> displayableRecipes = new LinkedHashMap<>();
            for (Map.Entry<Identifier, GunSmithTableRecipe> entry : source.recipes()) {
                if (isDisplayable(entry.getValue())) {
                    displayableRecipes.put(entry.getKey(), entry.getValue());
                }
            }
            if (displayableRecipes.size() > bestRecipes.size()) {
                bestRecipes = displayableRecipes;
            }
        }
        return bestRecipes;
    }

    @Nullable
    private static RecipeManager getSingleplayerServerRecipeManager() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            return minecraft.getSingleplayerServer().getRecipeManager();
        }
        MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
        return currentServer != null ? currentServer.getRecipeManager() : null;
    }

    private static void addRecipeManagerSource(List<Source> sources,
                                               IdentityHashMap<RecipeManager, Boolean> seenRecipeManagers,
                                               SourceKind kind,
                                               RecipeManager recipeManager) {
        if (seenRecipeManagers.put(recipeManager, Boolean.TRUE) != null) {
            return;
        }
        sources.add(new Source(kind, getRecipesFromManager(kind, recipeManager)));
    }

    private static List<Map.Entry<Identifier, GunSmithTableRecipe>> getRecipesFromManager(SourceKind kind, RecipeManager recipeManager) {
        List<Map.Entry<Identifier, GunSmithTableRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() != ModRecipe.GUN_SMITH_TABLE_CRAFTING.get() || !(recipe instanceof GunSmithTableRecipe gunSmithTableRecipe)) {
                continue;
            }
            Identifier id = holder.id().identifier();
            if (initialize(kind, id, gunSmithTableRecipe)) {
                recipes.add(Map.entry(id, gunSmithTableRecipe));
            }
        }
        return recipes;
    }

    private static List<Map.Entry<Identifier, GunSmithTableRecipe>> initializedEntries(SourceKind kind,
                                                                                       Iterable<Map.Entry<Identifier, GunSmithTableRecipe>> entries) {
        List<Map.Entry<Identifier, GunSmithTableRecipe>> recipes = new ArrayList<>();
        for (Map.Entry<Identifier, GunSmithTableRecipe> entry : entries) {
            if (initialize(kind, entry.getKey(), entry.getValue())) {
                recipes.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        return recipes;
    }

    private static boolean initialize(SourceKind kind, Identifier id, GunSmithTableRecipe recipe) {
        try {
            recipe.init();
            return true;
        } catch (RuntimeException exception) {
            GunMod.LOGGER.warn("Failed to initialize gun smith table recipe {} from {}", id, kind.logName(), exception);
            return false;
        }
    }

    public static boolean isDisplayable(GunSmithTableRecipe recipe) {
        Identifier group = recipe.getResult().getGroup();
        return !recipe.getResult().getResult().isEmpty() && group != null && !TabConfig.TAB_EMPTY.equals(group);
    }
}
