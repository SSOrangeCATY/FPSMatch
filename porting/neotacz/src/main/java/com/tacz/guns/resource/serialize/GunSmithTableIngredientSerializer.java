package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Type;

public class GunSmithTableIngredientSerializer implements JsonDeserializer<GunSmithTableIngredient> {
    private static final RegistryAccess.Frozen BUILTIN_REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Override
    public GunSmithTableIngredient deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            if (!jsonObject.has("item")) {
                throw new JsonSyntaxException("Expected " + jsonObject + " must has a item member");
            }
            Ingredient ingredient = Ingredient.CODEC.parse(
                    BUILTIN_REGISTRIES.createSerializationContext(JsonOps.INSTANCE),
                    jsonObject.get("item")
            ).getOrThrow(JsonParseException::new);
            int count = 1;
            if (jsonObject.has("count")) {
                count = Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1);
            }
            return new GunSmithTableIngredient(ingredient, count);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Pair because it's not an object");
        }
    }
}
