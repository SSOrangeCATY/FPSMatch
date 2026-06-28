package com.tacz.guns.compat.kubejs.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.kubejs.util.GunSmithTableResultInfo;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentValue;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

public class GunSmithTableResultComponents {
    private static final ResourceKey<RecipeComponentType<?>> RESULT_INFO_TYPE = RecipeComponentType.key(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table_result_info"));
    private static final ResourceKey<RecipeComponentType<?>> MATERIALS_TYPE = RecipeComponentType.key(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table_materials"));
    private static final Codec<GunSmithTableResultInfo> RESULT_INFO_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> GunSmithTableResultInfo.createFromJson(toJsonObject(dynamic)),
            info -> toDynamic(info.toJson())
    );
    private static final Codec<List<JsonObject>> MATERIALS_CODEC = Codec.PASSTHROUGH.listOf().xmap(
            dynamics -> dynamics.stream().map(GunSmithTableResultComponents::toJsonObject).toList(),
            values -> values.stream().map(GunSmithTableResultComponents::toDynamic).toList()
    );

    public static final RecipeComponent<GunSmithTableResultInfo> RESULT_INFO = new RecipeComponent<>() {
        @Override
        public ResourceKey<RecipeComponentType<?>> type() {
            return RESULT_INFO_TYPE;
        }

        @Override
        public Codec<GunSmithTableResultInfo> codec() {
            return RESULT_INFO_CODEC;
        }

        @Override
        public TypeInfo typeInfo() {
            return TypeInfo.of(GunSmithTableResultInfo.class);
        }

        @Override
        public GunSmithTableResultInfo wrap(RecipeScriptContext context, Object from) {
            return GunSmithTableResultInfo.of(context.cx(), from);
        }

        @Override
        public void writeToJson(KubeRecipe recipe, RecipeComponentValue<GunSmithTableResultInfo> value, JsonObject json) {
            if (value.value != null) {
                json.add(value.key.name, value.value.toJson());
            }
        }

        @Override
        public void readFromJson(KubeRecipe recipe, RecipeComponentValue<GunSmithTableResultInfo> value, JsonObject json) {
            JsonElement element = json.get(value.key.name);
            if (element != null && element.isJsonObject()) {
                GunSmithTableResultInfo info = GunSmithTableResultInfo.createFromJson(element.getAsJsonObject());
                value.setValue(info);
                value.write();
            }
            if (recipe instanceof TimelessRecipeJS tRecipe) {
                tRecipe.setResultInfo(value.value);
            }
        }
    };

    public static final RecipeComponent<List<JsonObject>> MATERIALS = new RecipeComponent<>() {
        @Override
        public ResourceKey<RecipeComponentType<?>> type() {
            return MATERIALS_TYPE;
        }

        @Override
        public Codec<List<JsonObject>> codec() {
            return MATERIALS_CODEC;
        }

        @Override
        public TypeInfo typeInfo() {
            return TypeInfo.RAW_LIST;
        }

        @Override
        public List<JsonObject> wrap(RecipeScriptContext context, Object from) {
            List<?> list = ListJS.of(from);
            if (list == null) {
                return List.of(materialFromObject(context, from));
            }
            List<JsonObject> materials = new ArrayList<>(list.size());
            for (Object element : list) {
                materials.add(materialFromObject(context, element));
            }
            return materials;
        }

        @Override
        public void writeToJson(KubeRecipe recipe, RecipeComponentValue<List<JsonObject>> value, JsonObject json) {
            JsonArray array = new JsonArray();
            if (value.value != null) {
                value.value.forEach(array::add);
            }
            json.add(value.key.name, array);
        }

        @Override
        public void readFromJson(KubeRecipe recipe, RecipeComponentValue<List<JsonObject>> value, JsonObject json) {
            JsonElement element = json.get(value.key.name);
            if (element != null && element.isJsonArray()) {
                List<JsonObject> materials = new ArrayList<>();
                for (JsonElement material : element.getAsJsonArray()) {
                    if (material.isJsonObject()) {
                        materials.add(material.getAsJsonObject());
                    }
                }
                value.setValue(materials);
                value.write();
            }
        }
    };

    private static JsonObject materialFromObject(RecipeScriptContext context, Object from) {
        if (from instanceof JsonObject jsonObject && jsonObject.has("item")) {
            return materialFromJson(context, jsonObject);
        }
        if (from instanceof JsonElement jsonElement && jsonElement.isJsonObject() && jsonElement.getAsJsonObject().has("item")) {
            return materialFromJson(context, jsonElement.getAsJsonObject());
        }
        if (!(from instanceof CharSequence) && !(from instanceof Ingredient) && !(from instanceof SizedIngredient)) {
            try {
                JsonObject jsonObject = JsonUtils.objectOf(context.cx(), from);
                if (jsonObject.has("item")) {
                    return materialFromJson(context, jsonObject);
                }
            } catch (RuntimeException ignored) {
                // Fall through to KubeJS' own ingredient wrapper for plain ingredient-like inputs.
            }
        }
        return materialFromSized(context, SizedIngredientWrapper.wrap(context.cx(), from));
    }

    private static JsonObject materialFromJson(RecipeScriptContext context, JsonObject jsonObject) {
        int count = jsonObject.has("count") ? Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1) : 1;
        Ingredient ingredient = Ingredient.CODEC.parse(context.ops().json(), jsonObject.get("item")).getOrThrow();
        return materialFromIngredient(context, ingredient, count);
    }

    private static JsonObject materialFromSized(RecipeScriptContext context, SizedIngredient sizedIngredient) {
        return materialFromIngredient(context, sizedIngredient.ingredient(), sizedIngredient.count());
    }

    private static JsonObject materialFromIngredient(RecipeScriptContext context, Ingredient ingredient, int count) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("item", Ingredient.CODEC.encodeStart(context.ops().json(), ingredient).getOrThrow());
        if (count > 1) {
            jsonObject.addProperty("count", count);
        }
        return jsonObject;
    }

    private static JsonObject toJsonObject(Dynamic<?> dynamic) {
        JsonElement element = dynamic.convert(JsonOps.INSTANCE).getValue();
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static Dynamic<?> toDynamic(JsonObject jsonObject) {
        return new Dynamic<>(JsonOps.INSTANCE, jsonObject);
    }
}
