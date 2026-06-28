package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface BlockItemDataAccessor extends IBlock {
    String BLOCK_ID = "BlockId";

    @Override
    @Nonnull
    default Identifier getBlockId(ItemStack block) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(block);
        if (ItemStackNbtHelper.contains(nbt, BLOCK_ID, Tag.TAG_STRING)) {
            Identifier gunId = Identifier.tryParse(ItemStackNbtHelper.getString(nbt, BLOCK_ID));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_BLOCK_ID);
        }
        return DefaultAssets.EMPTY_BLOCK_ID;
    }

    @Override
    default void setBlockId(ItemStack block, @Nullable Identifier blockId) {
        ItemStackNbtHelper.updateTag(block, nbt -> nbt.putString(BLOCK_ID, blockId != null ? blockId.toString() : DefaultAssets.EMPTY_BLOCK_ID.toString()));
    }

}
