package com.tacz.guns.item;

import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public enum GunTooltipPart {
    DESCRIPTION,
    AMMO_INFO,
    BASE_INFO,
    EXTRA_DAMAGE_INFO,
    UPGRADES_TIP,
    PACK_INFO;

    private final int mask = 1 << this.ordinal();

    public int getMask() {
        return this.mask;
    }

    public static int getHideFlags(ItemStack stack) {
        CompoundTag tag = ItemStackNbtHelper.getTag(stack);
        if (ItemStackNbtHelper.contains(tag, "HideFlags", ItemStackNbtHelper.TAG_ANY_NUMERIC)) {
            return ItemStackNbtHelper.getInt(tag, "HideFlags");
        }
        return 0;
    }

    public static void setHideFlags(ItemStack stack, int mask) {
        ItemStackNbtHelper.updateTag(stack, tag -> tag.putInt("HideFlags", mask));
    }
}
