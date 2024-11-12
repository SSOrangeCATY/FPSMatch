package com.phasetranscrystal.fpsmatch.core.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import net.minecraft.world.item.ItemStack;

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

    public static Map<ShopData.ItemType, List<ShopData.ShopSlot>> decodeShopDataMapFromJson(JsonElement json) {
        Map<String, List<ShopData.ShopSlot>> m = ITEM_TYPE_TO_SHOP_SLOT_LIST_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();

        Map<ShopData.ItemType, List<ShopData.ShopSlot>> data = new HashMap<>();
        m.forEach((t,l)->{
            data.put(ShopData.ItemType.valueOf(t),l);
        });

        return data;
    }

}
