package com.tacz.guns.resource.manager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GunSmithTableRecipeDataManager extends SimplePreparableReloadListener<GunSmithTableRecipeDataManager.PreparedData> implements INetworkCacheReloadListener {
    private static final String RECIPE_TYPE = GunMod.MOD_ID + ":gun_smith_table_crafting";
    private static final Set<String> WORKBENCH_RESULT_TYPES = Set.of(
            GunSmithTableResult.GUN,
            GunSmithTableResult.AMMO,
            GunSmithTableResult.ATTACHMENT
    );

    private final Marker marker = MarkerManager.getMarker("GunSmithTableRecipeData");
    private final FileToIdConverter fileToIdConverter = FileToIdConverter.json("recipes");
    private final FileToIdConverter itemTagFileToIdConverter = FileToIdConverter.json("tags/item");
    private final Map<Identifier, GunSmithTableRecipe> recipes = Maps.newLinkedHashMap();
    private Map<Identifier, String> networkCache = Map.of();

    @NotNull
    @Override
    protected PreparedData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new PreparedData(
                ResourceScanner.scanDirectory(resourceManager, fileToIdConverter, CommonAssetsManager.GSON),
                ResourceScanner.scanDirectoryAll(resourceManager, itemTagFileToIdConverter, CommonAssetsManager.GSON)
        );
    }

    @Override
    protected void apply(PreparedData data, ResourceManager resourceManager, ProfilerFiller profiler) {
        recipes.clear();
        ImmutableMap.Builder<Identifier, String> builder = ImmutableMap.builder();
        int typeMatched = 0;
        int skippedNonWorkbench = 0;
        ItemTagResolver tagResolver = new ItemTagResolver(data.itemTags());

        for (Map.Entry<Identifier, JsonElement> entry : data.recipes().entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!isGunSmithTableRecipe(element)) {
                continue;
            }
            typeMatched++;
            if (!isWorkbenchResult(element)) {
                skippedNonWorkbench++;
                continue;
            }
            try {
                JsonObject networkRecipe = normalizeNetworkRecipe(id, element, tagResolver);
                TableRecipe tableRecipe = CommonAssetsManager.GSON.fromJson(networkRecipe, TableRecipe.class);
                if (tableRecipe == null || tableRecipe.getResult() == null || tableRecipe.getMaterials() == null) {
                    throw new JsonParseException("Incomplete gun smith table recipe");
                }
                recipes.put(id, new GunSmithTableRecipe(id, tableRecipe));
                builder.put(id, networkRecipe.toString());
            } catch (JsonParseException | IllegalArgumentException exception) {
                GunMod.LOGGER.error(marker, "Failed to load gun smith table recipe {}", id, exception);
            }
        }

        networkCache = builder.build();
        GunMod.LOGGER.info(marker, "Loaded TACZ gun smith table recipe data: scanned={} typeMatched={} synced={} skippedNonWorkbench={}",
                data.recipes().size(), typeMatched, networkCache.size(), skippedNonWorkbench);
    }

    private static boolean isGunSmithTableRecipe(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        return RECIPE_TYPE.equals(getString(object, "type"));
    }

    private static boolean isWorkbenchResult(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        if (!object.has("result") || !object.get("result").isJsonObject()) {
            return false;
        }
        JsonObject result = object.getAsJsonObject("result");
        return WORKBENCH_RESULT_TYPES.contains(getString(result, "type"));
    }

    private static JsonObject normalizeNetworkRecipe(Identifier recipeId, JsonElement element, ItemTagResolver tagResolver) {
        JsonObject recipe = element.getAsJsonObject().deepCopy();
        if (!recipe.has("materials") || !recipe.get("materials").isJsonArray()) {
            return recipe;
        }
        JsonArray materials = recipe.getAsJsonArray("materials");
        for (JsonElement materialElement : materials) {
            if (!materialElement.isJsonObject()) {
                continue;
            }
            JsonObject material = materialElement.getAsJsonObject();
            material.add("item", normalizeIngredient(recipeId, material.get("item"), tagResolver));
        }
        return recipe;
    }

    private static JsonElement normalizeIngredient(Identifier recipeId, JsonElement ingredient, ItemTagResolver tagResolver) {
        if (ingredient == null || ingredient.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (ingredient.isJsonArray()) {
            JsonArray normalized = new JsonArray();
            for (JsonElement entry : ingredient.getAsJsonArray()) {
                JsonElement resolved = normalizeIngredient(recipeId, entry, tagResolver);
                if (resolved.isJsonArray()) {
                    resolved.getAsJsonArray().forEach(normalized::add);
                } else if (!resolved.isJsonNull()) {
                    normalized.add(resolved);
                }
            }
            if (normalized.isEmpty()) {
                throw new JsonParseException("Recipe " + recipeId + " has an empty ingredient list after tag expansion");
            }
            return normalized;
        }
        if (ingredient.isJsonPrimitive()) {
            String value = ingredient.getAsString();
            if (value.startsWith("#")) {
                return tagResolver.toIngredientJson(recipeId, Identifier.parse(value.substring(1)));
            }
            return ingredient.deepCopy();
        }
        if (!ingredient.isJsonObject()) {
            return ingredient.deepCopy();
        }
        JsonObject object = ingredient.getAsJsonObject();
        String tag = getString(object, "tag");
        if (!tag.isBlank()) {
            return tagResolver.toIngredientJson(recipeId, parseTagId(tag));
        }
        String item = getString(object, "item");
        if (!item.isBlank()) {
            return new JsonPrimitive(item);
        }
        return ingredient.deepCopy();
    }

    private static Identifier parseTagId(String tag) {
        String raw = tag.startsWith("#") ? tag.substring(1) : tag;
        return Identifier.parse(raw);
    }

    private static String getString(JsonObject object, String member) {
        try {
            if (!object.has(member) || object.get(member).isJsonNull()) {
                return "";
            }
            return object.get(member).getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    @Override
    public Map<Identifier, String> getNetworkCache() {
        return networkCache;
    }

    @Override
    public DataType getType() {
        return DataType.RECIPES;
    }

    public Map<Identifier, GunSmithTableRecipe> getRecipes() {
        return recipes;
    }

    public record PreparedData(Map<Identifier, JsonElement> recipes,
                               Map<Identifier, List<JsonElement>> itemTags) {
    }

    private static final class ItemTagResolver {
        private final Map<Identifier, List<JsonElement>> tagFiles;
        private final Map<Identifier, List<String>> resolvedTags = new HashMap<>();

        private ItemTagResolver(Map<Identifier, List<JsonElement>> tagFiles) {
            this.tagFiles = tagFiles;
        }

        private JsonElement toIngredientJson(Identifier recipeId, Identifier tagId) {
            List<String> items = resolveTag(tagId, new HashSet<>());
            if (items.isEmpty()) {
                throw new JsonParseException("Recipe " + recipeId + " resolved item tag " + tagId + " to no items");
            }
            if (items.size() == 1) {
                return new JsonPrimitive(items.get(0));
            }
            JsonArray array = new JsonArray();
            items.forEach(array::add);
            return array;
        }

        private List<String> resolveTag(Identifier tagId, Set<Identifier> resolving) {
            List<String> cached = resolvedTags.get(tagId);
            if (cached != null) {
                return cached;
            }
            List<JsonElement> files = tagFiles.get(tagId);
            if (files == null || files.isEmpty()) {
                throw new JsonParseException("Missing item tag " + tagId);
            }
            if (!resolving.add(tagId)) {
                throw new JsonParseException("Recursive item tag reference " + tagId);
            }
            try {
                LinkedHashSet<String> values = new LinkedHashSet<>();
                for (JsonElement file : files) {
                    if (file == null || !file.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = file.getAsJsonObject();
                    if (getBoolean(object, "replace")) {
                        values.clear();
                    }
                    applyEntries(values, object.get("values"), false, resolving);
                    applyEntries(values, object.get("remove"), true, resolving);
                }
                List<String> resolved = List.copyOf(values);
                resolvedTags.put(tagId, resolved);
                return resolved;
            } finally {
                resolving.remove(tagId);
            }
        }

        private void applyEntries(LinkedHashSet<String> values, JsonElement entries, boolean remove, Set<Identifier> resolving) {
            if (entries == null || entries.isJsonNull()) {
                return;
            }
            if (!entries.isJsonArray()) {
                applyEntry(values, entries, remove, resolving);
                return;
            }
            for (JsonElement entryElement : entries.getAsJsonArray()) {
                applyEntry(values, entryElement, remove, resolving);
            }
        }

        private void applyEntry(LinkedHashSet<String> values, JsonElement entryElement, boolean remove, Set<Identifier> resolving) {
            TagEntry entry = TagEntry.parse(entryElement);
            if (entry == null) {
                return;
            }
            if (entry.tag()) {
                try {
                    List<String> nested = resolveTag(entry.id(), resolving);
                    if (remove) {
                        values.removeAll(nested);
                    } else {
                        values.addAll(nested);
                    }
                } catch (JsonParseException exception) {
                    if (entry.required()) {
                        throw exception;
                    }
                }
                return;
            }
            if (!BuiltInRegistries.ITEM.containsKey(entry.id())) {
                if (entry.required()) {
                    throw new JsonParseException("Missing item " + entry.id());
                }
                return;
            }
            if (remove) {
                values.remove(entry.id().toString());
            } else {
                values.add(entry.id().toString());
            }
        }
    }

    private record TagEntry(Identifier id, boolean tag, boolean required) {
        private static TagEntry parse(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return null;
            }
            if (element.isJsonPrimitive()) {
                String raw = element.getAsString();
                boolean tag = raw.startsWith("#");
                return new TagEntry(Identifier.parse(tag ? raw.substring(1) : raw), tag, true);
            }
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            boolean required = !object.has("required") || object.get("required").getAsBoolean();
            String tagId = getString(object, "tag");
            if (!tagId.isBlank()) {
                return new TagEntry(parseTagId(tagId), true, required);
            }
            String id = getString(object, "id");
            if (id.isBlank()) {
                id = getString(object, "item");
            }
            if (id.isBlank()) {
                return null;
            }
            boolean tag = id.startsWith("#");
            return new TagEntry(Identifier.parse(tag ? id.substring(1) : id), tag, required);
        }
    }

    private static boolean getBoolean(JsonObject object, String member) {
        try {
            return object.has(member) && !object.get(member).isJsonNull() && object.get(member).getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
