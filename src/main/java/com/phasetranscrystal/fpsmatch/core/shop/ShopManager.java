package com.phasetranscrystal.fpsmatch.core.shop;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ShopManager {
    ShopManager(){}
    protected final Map<String, Codec<? extends ShopSlot>> SLOT_REGISTRY = new HashMap<>();

    public <T extends ShopSlot> void registerSlotCodec(@NotNull Class<T> type, @NotNull Codec<T> codec) {
        SLOT_REGISTRY.put(type.getName(), codec);
    }

    public <T extends ShopSlot> JsonElement getShopSlotJson(T shopSlot) {
        String type = shopSlot.getType();
        Codec<T> slotCodec = (Codec<T>) SLOT_REGISTRY.getOrDefault(type,null);
        if (slotCodec == null) {
            throw new RuntimeException("No codec registered for slot type: " + type);
        }
        return encodeShopSlotToJson(slotCodec, shopSlot);
    }

    public <T extends ShopSlot> T getShopSlotFromJson(JsonElement json) {
        String type = json.getAsJsonObject().get("Type").getAsString();
        Codec<T> slotCodec = (Codec<T>) SLOT_REGISTRY.getOrDefault(type,null);
        if (slotCodec == null) {
            throw new RuntimeException("No codec registered for slot type: " + type);
        }
        return decodeShopSlotFromJson(slotCodec, json);
    }

    protected  <T extends ShopSlot> JsonElement encodeShopSlotToJson(Codec<T> slotCodec, T shopSlot) {
        return slotCodec.encodeStart(JsonOps.INSTANCE, shopSlot).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    protected <T extends ShopSlot> T decodeShopSlotFromJson(Codec<T> slotCodec, JsonElement json) {
        return slotCodec.decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

}
