package com.tacz.guns.api.item.nbt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

public final class ItemStackNbtHelper {
    public static final int TAG_ANY_NUMERIC = 99;

    private ItemStackNbtHelper() {
    }

    public static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    public static void updateTag(ItemStack stack, Consumer<CompoundTag> consumer) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, consumer);
    }

    public static CompoundTag saveLegacyStackSubset(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        if (stack.isEmpty()) {
            return tag;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return tag;
        }
        tag.putString("id", itemId.toString());
        tag.putInt("Count", stack.getCount());
        CompoundTag customData = getTag(stack);
        if (!customData.isEmpty()) {
            tag.put("tag", customData);
        }
        return tag;
    }

    public static ItemStack parseLegacyStackSubset(CompoundTag tag) {
        if (!contains(tag, "id", Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }
        Identifier itemId = Identifier.tryParse(getString(tag, "id"));
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int count = contains(tag, "Count", TAG_ANY_NUMERIC) ? Math.max(0, getInt(tag, "Count")) : 1;
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);
        if (contains(tag, "tag", Tag.TAG_COMPOUND)) {
            setTag(stack, getCompoundOrEmpty(tag, "tag").copy());
        }
        return stack;
    }

    public static boolean contains(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        if (value == null) {
            return false;
        }
        return type == TAG_ANY_NUMERIC ? value instanceof NumericTag : value.getId() == type;
    }

    public static boolean contains(ItemStack stack, String key, int type) {
        return contains(getTag(stack), key, type);
    }

    public static int getInt(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }

    public static float getFloat(CompoundTag tag, String key) {
        return tag.getFloatOr(key, 0.0F);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBooleanOr(key, false);
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static CompoundTag getCompoundOrEmpty(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    public static int[] getIntArrayOrEmpty(CompoundTag tag, String key) {
        return tag.getIntArray(key).orElseGet(() -> new int[0]);
    }
}
