package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.GunSmithTableSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber
public class ModRecipe {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, GunMod.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, GunMod.MOD_ID);

    public static DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> GUN_SMITH_TABLE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register(
            "gun_smith_table_crafting",
            () -> new RecipeSerializer<>(GunSmithTableSerializer.CODEC, GunSmithTableSerializer.STREAM_CODEC)
    );
    public static DeferredHolder<RecipeType<?>, RecipeType<GunSmithTableRecipe>> GUN_SMITH_TABLE_CRAFTING = RECIPE_TYPES.register("gun_smith_table_crafting", () -> new RecipeType<>() {
        @Override
        public String toString() {
            return GunMod.MOD_ID + ":gun_smith_table_crafting";
        }
    });

}
