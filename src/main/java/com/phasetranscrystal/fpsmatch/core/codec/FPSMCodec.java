package com.phasetranscrystal.fpsmatch.core.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FPSMCodec {
    public static final Codec<ShopData.ShopSlot> SHOP_SLOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("Index").forGetter(ShopData.ShopSlot::index),
            Codec.STRING.fieldOf("Type").forGetter(ShopData.ShopSlot::typeStr),
            ItemStack.CODEC.fieldOf("ItemStack").forGetter(ShopData.ShopSlot::itemStack),
            Codec.INT.fieldOf("DefaultCost").forGetter(ShopData.ShopSlot::defaultCost)
    ).apply(instance, (index, type, itemStack, defaultCost) -> new ShopData.ShopSlot(index, ShopData.ItemType.valueOf(type), itemStack, defaultCost)));

    public static JsonElement encodeShopSlotToJson(ShopData.ShopSlot shopSlot) {
        return SHOP_SLOT_CODEC.encodeStart(JsonOps.INSTANCE, shopSlot).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        });
    }

    public static ShopData.ShopSlot decodeShopSlotFromJson(JsonElement json) {
        return SHOP_SLOT_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();
    }

    public static final UnboundedMapCodec<String, List<ShopData.ShopSlot>> ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC = new UnboundedMapCodec<>(
            Codec.STRING,
            SHOP_SLOT_CODEC.listOf()
    );

    public static JsonElement encodeShopDataMapToJson(Map<ShopData.ItemType, List<ShopData.ShopSlot>> itemTypeListMap) {
        Map<String, List<ShopData.ShopSlot>> data = new HashMap<>();
        itemTypeListMap.forEach((t,l)->{
            data.put(t.name(),l);
        });

        return ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        });

    }

    public static Map<ShopData.ItemType, ArrayList<ShopData.ShopSlot>> decodeShopDataMapFromJson(JsonElement json) {
        Map<String, List<ShopData.ShopSlot>> m = ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();

        Map<ShopData.ItemType, ArrayList<ShopData.ShopSlot>> data = new HashMap<>();
        m.forEach((t,l)->{
            ArrayList<ShopData.ShopSlot> list = new ArrayList<>(l);
            data.put(ShopData.ItemType.valueOf(t),list);
        });

        return data;
    }


    public static final Codec<SpawnPointData> SPAWN_POINT_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("Dimension").forGetter(spawnPointData -> spawnPointData.getDimension().location().toString()),
            Codec.LONG.optionalFieldOf("Position", 0L).forGetter(spawnPointData -> spawnPointData.getPosition() != null ? spawnPointData.getPosition().asLong() : 0L),
            Codec.FLOAT.fieldOf("Yaw").forGetter(SpawnPointData::getYaw),
            Codec.FLOAT.fieldOf("Pitch").forGetter(SpawnPointData::getPitch)
    ).apply(instance, (dimensionStr, position, yaw, pitch) -> {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionStr));
        BlockPos pos = position == 0L ? null : BlockPos.of(position);
        return new SpawnPointData(dimension, pos, yaw, pitch);
    }));

    public static JsonElement encodeSpawnPointDataToJson(SpawnPointData spawnPointData) {
        return SPAWN_POINT_DATA_CODEC.encodeStart(JsonOps.INSTANCE, spawnPointData).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static SpawnPointData decodeSpawnPointDataFromJson(JsonElement json) {
        return SPAWN_POINT_DATA_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }
    public static final UnboundedMapCodec<String, List<SpawnPointData>> SPAWN_POINT_DATA_MAP_LIST_CODEC = new UnboundedMapCodec<>(
            Codec.STRING,
            SPAWN_POINT_DATA_CODEC.listOf()
    );

    public static final UnboundedMapCodec<ResourceLocation, Map<String, List<SpawnPointData>>> MAP_SPAWN_POINT_CODEC = new UnboundedMapCodec<>(
            ResourceLocation.CODEC,
            SPAWN_POINT_DATA_MAP_LIST_CODEC
    );

    public static JsonElement encodeMapSpawnPointDataToJson(Map<ResourceLocation, Map<String, List<SpawnPointData>>> data) {
        return MAP_SPAWN_POINT_CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static Map<ResourceLocation, Map<String, List<SpawnPointData>>> decodeMapSpawnPointDataFromJson(JsonElement json) {
        return MAP_SPAWN_POINT_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }
}
