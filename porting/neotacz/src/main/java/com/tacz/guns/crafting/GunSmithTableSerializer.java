package com.tacz.guns.crafting;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作台配方序列化器。
 *
 * <p>Minecraft 26.1 moved recipe serializers to codec records. TACZ keeps its
 * existing JSON format by converting the full recipe map through Gson here.</p>
 */
public final class GunSmithTableSerializer {
    public static final MapCodec<GunSmithTableRecipe> CODEC = new MapCodec<>() {
        @Override
        public <T> DataResult<GunSmithTableRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
            T map = ops.createMap(input.entries());
            JsonElement json = ops.convertTo(JsonOps.INSTANCE, map);
            TableRecipe tableRecipe = CommonAssetsManager.GSON.fromJson(json, TableRecipe.class);
            if (tableRecipe == null) {
                return DataResult.error(() -> "Empty TACZ gun smith table recipe");
            }
            return DataResult.success(new GunSmithTableRecipe(null, tableRecipe));
        }

        @Override
        public <T> RecordBuilder<T> encode(GunSmithTableRecipe input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of("materials", "result").map(ops::createString);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, GunSmithTableRecipe> STREAM_CODEC = StreamCodec.of(
            GunSmithTableSerializer::toNetwork,
            GunSmithTableSerializer::fromNetwork
    );

    private GunSmithTableSerializer() {
    }

    private static GunSmithTableRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readInt();
        List<GunSmithTableIngredient> ingredients = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ingredients.add(new GunSmithTableIngredient(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer), buffer.readInt()));
        }
        ItemStack resultItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        Identifier group = Identifier.STREAM_CODEC.decode(buffer);
        GunSmithTableResult result = new GunSmithTableResult(resultItem, group);
        return new GunSmithTableRecipe(null, result, ingredients);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, GunSmithTableRecipe recipe) {
        buffer.writeInt(recipe.getInputs().size());
        for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.getIngredient());
            buffer.writeInt(ingredient.getCount());
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.getResult().getResult());
        Identifier.STREAM_CODEC.encode(buffer, recipe.getResult().getGroup());
    }
}
