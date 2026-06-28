package com.tacz.guns.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public final class ItemStackData {
    private ItemStackData() {
    }

    public static CompoundTag copyCustomData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean hasCustomData(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty();
    }

    @Nullable
    public static CompoundTag getCompound(ItemStack stack, String key) {
        return copyCustomData(stack).getCompound(key).orElse(null);
    }

    public static void setCustomData(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
    }

    public static void updateCustomData(ItemStack stack, Consumer<CompoundTag> updater) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
    }
}
