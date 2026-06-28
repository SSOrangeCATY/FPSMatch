package com.tacz.guns.resource.serialize;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.util.ItemStackData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemStackJsonHelper {
    private static final Gson GSON = new Gson();

    private ItemStackJsonHelper() {
    }

    public static CompoundTag getNbt(JsonElement element) {
        try {
            if (element.isJsonObject()) {
                return TagParser.parseCompoundFully(GSON.toJson(element));
            }
            return TagParser.parseCompoundFully(GsonHelper.convertToString(element, "nbt"));
        } catch (CommandSyntaxException exception) {
            throw new JsonSyntaxException("Invalid item nbt: " + element, exception);
        }
    }

    public static ItemStack getItemStack(JsonObject object, boolean readNbt) {
        return getItemStack(object, readNbt, true);
    }

    public static ItemStack getItemStack(JsonObject object, boolean readNbt, boolean disallowAir) {
        Identifier itemId = Identifier.parse(GsonHelper.getAsString(object, "item"));
        Item item = BuiltInRegistries.ITEM.getOptional(itemId)
                .orElseThrow(() -> new JsonSyntaxException("Unknown item '" + itemId + "'"));
        if (disallowAir && item == Items.AIR) {
            throw new JsonParseException("Item must not be minecraft:air");
        }
        ItemStack stack = new ItemStack(item, GsonHelper.getAsInt(object, "count", 1));
        if (readNbt && object.has("nbt")) {
            ItemStackData.setCustomData(stack, getNbt(object.get("nbt")));
        }
        return stack;
    }
}
