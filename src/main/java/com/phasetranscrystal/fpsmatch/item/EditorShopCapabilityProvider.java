package com.phasetranscrystal.fpsmatch.item;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorShopCapabilityProvider implements ICapabilityProvider {
    public static final int ROWS = 5;
    public static final int COLS = 5;
    private ItemStackHandler itemStackHandler = new ItemStackHandler(ROWS * COLS);
    private final LazyOptional<ItemStackHandler> lazyOptional = LazyOptional.of(() -> itemStackHandler);
    private final ItemStack shopEditToolStack;

    public EditorShopCapabilityProvider(ItemStack shopEditToolStack) {
        this.shopEditToolStack = shopEditToolStack;
        CompoundTag tag = shopEditToolStack.getTag();
        if (tag != null && tag.contains("ShopItems")) {
            itemStackHandler.deserializeNBT(tag.getCompound("ShopItems"));
        }
    }

//direction 为 方块访问提供面参数，物品默认为null
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
        return capability == ForgeCapabilities.ITEM_HANDLER ? lazyOptional.cast() : LazyOptional.empty();
    }


    public void save() {
        CompoundTag tag = shopEditToolStack.getOrCreateTag();
        tag.put("ShopItems", itemStackHandler.serializeNBT());
    }
}
