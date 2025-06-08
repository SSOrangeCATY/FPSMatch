package com.phasetranscrystal.fpsmatch.core.codec;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FPSMCodec {

    public static JsonElement encodeShopSlotToJson(ShopSlot shopSlot) {
        return ShopSlot.CODEC.encodeStart(JsonOps.INSTANCE, shopSlot).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        });
    }

    public static ShopSlot decodeShopSlotFromJson(JsonElement json) {
        return ShopSlot.CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();
    }

    public static final UnboundedMapCodec<String, List<ShopSlot>> ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC = new UnboundedMapCodec<>(
            Codec.STRING,
            ShopSlot.CODEC.listOf()
    );

    public static JsonElement encodeShopDataMapToJson(Map<ItemType, ImmutableList<ShopSlot>> itemTypeListMap) {
        Map<String, List<ShopSlot>> data = new HashMap<>();
        itemTypeListMap.forEach((t,l)->data.put(t.name(),l.stream().toList()));

        return ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        });
    }

    public static Map<ItemType, ArrayList<ShopSlot>> decodeShopDataMapFromJson(JsonElement json) {
        Map<String, List<ShopSlot>> m = ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();

        Map<ItemType, ArrayList<ShopSlot>> data = new HashMap<>();
        m.forEach((t,l)->{
            ArrayList<ShopSlot> list = new ArrayList<>(l);
            data.put(ItemType.valueOf(t),list);
        });

        return data;
    }


    public static final Codec<SpawnPointData> SPAWN_POINT_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("Dimension").forGetter(spawnPointData -> spawnPointData.getDimension().location().toString()),
            BlockPos.CODEC.optionalFieldOf("Position", BlockPos.of(0L)).forGetter(SpawnPointData::getPosition),
            Codec.FLOAT.fieldOf("Yaw").forGetter(SpawnPointData::getYaw),
            Codec.FLOAT.fieldOf("Pitch").forGetter(SpawnPointData::getPitch)
    ).apply(instance, (dimensionStr, position, yaw, pitch) -> {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionStr));
        return new SpawnPointData(dimension, position, yaw, pitch);
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

    public static JsonElement encodeMapSpawnPointDataToJson(Map<String, List<SpawnPointData>> data) {
        return SPAWN_POINT_DATA_MAP_LIST_CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static Map<String, List<SpawnPointData>> decodeMapSpawnPointDataFromJson(JsonElement json) {
        return SPAWN_POINT_DATA_MAP_LIST_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

    public static final Codec<AreaData> AREA_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("Position1", BlockPos.of(0L)).forGetter(AreaData::pos1),
            BlockPos.CODEC.optionalFieldOf("Position2", BlockPos.of(0L)).forGetter(AreaData::pos2)
    ).apply(instance, AreaData::new));

    public static JsonElement encodeAreaDataToJson(AreaData areaData) {
        return AREA_DATA_CODEC.encodeStart(JsonOps.INSTANCE, areaData).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static AreaData decodeAreaDataFromJson(JsonElement json) {
        return AREA_DATA_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

    public static final Codec<List<AreaData>> List_AREA_DATA_CODEC = AREA_DATA_CODEC.listOf();

    public static JsonElement encodeAreaDataListToJson(List<AreaData> areaDataList) {
        return List_AREA_DATA_CODEC.encodeStart(JsonOps.INSTANCE, areaDataList).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static List<AreaData> decodeAreaDataListFromJson(JsonElement json) {
        return List_AREA_DATA_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

    public static JsonElement encodeLevelResourceKeyToJson(ResourceKey<Level> resourceKey) {
        return ResourceLocation.CODEC.encodeStart(JsonOps.INSTANCE, resourceKey.location()).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static ResourceKey<Level> decodeLevelResourceKeyFromJson(JsonElement json) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst());
    }

    public static final UnboundedMapCodec<String, List<ItemStack>> TEAM_ITEMS_KITS_CODEC = new UnboundedMapCodec<>(
            Codec.STRING,
            ItemStack.CODEC.listOf()
    );

    public static JsonElement encodeTeamKitsToJson(Map<String, List<ItemStack>> data) {
        return TEAM_ITEMS_KITS_CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    public static Map<String, ArrayList<ItemStack>> decodeTeamKitsFromJson(JsonElement json) {
        Map<String, List<ItemStack>> map = TEAM_ITEMS_KITS_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
        Map<String, ArrayList<ItemStack>> data = new HashMap<>();
        map.forEach((t,l)->{
            ArrayList<ItemStack> list = new ArrayList<>(l);
            data.put(t,list);
        });
        return data;
    }

}
