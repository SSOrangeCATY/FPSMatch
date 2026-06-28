package com.tacz.guns.resource.pojo.data.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

public record LootTableInjection(List<Identifier> lootTables, LootTable lootTable) {
    public static LootTableInjection fromJson(Identifier fileId, JsonElement element) {
        JsonObject object = GsonHelper.convertToJsonObject(element, "loot injection");
        List<Identifier> lootTables = readLootTables(fileId, object);
        if (!object.has("pools")) {
            throw new JsonParseException("Loot injection " + fileId + " must define pools");
        }
        JsonObject lootTableJson = object.deepCopy();
        normalizeLegacySetNbt(lootTableJson);
        LootTable lootTable = LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, lootTableJson)
                .getOrThrow(message -> new JsonParseException("Invalid loot injection " + fileId + ": " + message));
        lootTable.setLootTableId(fileId);
        return new LootTableInjection(lootTables, lootTable);
    }

    private static List<Identifier> readLootTables(Identifier fileId, JsonObject object) {
        List<Identifier> lootTables = new ArrayList<>();
        if (object.has("loot_tables")) {
            for (JsonElement table : GsonHelper.getAsJsonArray(object, "loot_tables")) {
                lootTables.add(Identifier.parse(GsonHelper.convertToString(table, "loot table")));
            }
        } else if (object.has("loot_table")) {
            lootTables.add(Identifier.parse(GsonHelper.getAsString(object, "loot_table")));
        } else {
            throw new JsonParseException("Loot injection " + fileId + " must define loot_table or loot_tables");
        }
        return List.copyOf(lootTables);
    }

    private static void normalizeLegacySetNbt(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("function")) {
                String function = GsonHelper.getAsString(object, "function");
                if ("minecraft:set_nbt".equals(function) || "set_nbt".equals(function)) {
                    object.addProperty("function", "minecraft:set_custom_data");
                }
            }
            for (var entry : object.entrySet()) {
                normalizeLegacySetNbt(entry.getValue());
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement value : array) {
                normalizeLegacySetNbt(value);
            }
        }
    }

    public List<ItemStack> createStacks(LootContext context) {
        List<ItemStack> stacks = new ArrayList<>();
        lootTable.getRandomItemsRaw(context, stacks::add);
        return stacks;
    }
}
