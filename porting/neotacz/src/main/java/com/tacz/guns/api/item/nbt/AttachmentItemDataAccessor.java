package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface AttachmentItemDataAccessor extends IAttachment {
    String ATTACHMENT_ID_TAG = "AttachmentId";
    String SKIN_ID_TAG = "Skin";
    String ZOOM_NUMBER_TAG = "ZoomNumber";
    String LASER_COLOR_TAG = "LaserColor";

    // 仅检查给定的 CompoundTag 是否具有配件 ID ，不校验其是否存在
    static boolean isAttachmentLike(CompoundTag tag) {
        return ItemStackNbtHelper.contains(tag, ATTACHMENT_ID_TAG, Tag.TAG_STRING);
    }

    @Nonnull
    static Identifier getAttachmentIdFromTag(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        if (isAttachmentLike(nbt)) {
            Identifier attachmentId = Identifier.tryParse(ItemStackNbtHelper.getString(nbt, ATTACHMENT_ID_TAG));
            return Objects.requireNonNullElse(attachmentId, DefaultAssets.EMPTY_ATTACHMENT_ID);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    static int getZoomNumberFromTag(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return 0;
        }
        if (ItemStackNbtHelper.contains(nbt, ZOOM_NUMBER_TAG, Tag.TAG_INT)) {
            return ItemStackNbtHelper.getInt(nbt, ZOOM_NUMBER_TAG);
        }
        return 0;
    }

    static void setZoomNumberToTag(CompoundTag nbt, int zoomNumber) {
        nbt.putInt(ZOOM_NUMBER_TAG, zoomNumber);
    }

    @Override
    @Nonnull
    default Identifier getAttachmentId(ItemStack attachmentStack) {
        return getAttachmentIdFromTag(ItemStackNbtHelper.getTag(attachmentStack));
    }

    @Override
    default void setAttachmentId(ItemStack attachmentStack, @Nullable Identifier attachmentId) {
        if (attachmentId != null) {
            ItemStackNbtHelper.updateTag(attachmentStack, nbt -> nbt.putString(ATTACHMENT_ID_TAG, attachmentId.toString()));
        }
    }

    @Override
    @Nullable
    default Identifier getSkinId(ItemStack attachmentStack) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(attachmentStack);
        if (ItemStackNbtHelper.contains(nbt, SKIN_ID_TAG, Tag.TAG_STRING)) {
            return Identifier.tryParse(ItemStackNbtHelper.getString(nbt, SKIN_ID_TAG));
        }
        return null;
    }

    @Override
    default void setSkinId(ItemStack attachmentStack, @Nullable Identifier skinId) {
        ItemStackNbtHelper.updateTag(attachmentStack, nbt -> {
            if (skinId != null) {
                nbt.putString(SKIN_ID_TAG, skinId.toString());
            } else {
                nbt.remove(SKIN_ID_TAG);
            }
        });
    }

    @Override
    default int getZoomNumber(ItemStack attachmentStack) {
        return getZoomNumberFromTag(ItemStackNbtHelper.getTag(attachmentStack));
    }

    @Override
    default void setZoomNumber(ItemStack attachmentStack, int zoomNumber) {
        ItemStackNbtHelper.updateTag(attachmentStack, nbt -> setZoomNumberToTag(nbt, zoomNumber));
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack attachmentStack) {
        return ItemStackNbtHelper.contains(attachmentStack, LASER_COLOR_TAG, Tag.TAG_INT);
    }

    @Override
    default int getLaserColor(ItemStack attachmentStack) {
        if (!hasCustomLaserColor(attachmentStack)) {
            return 0xFF0000;
        }
        return ItemStackNbtHelper.getInt(ItemStackNbtHelper.getTag(attachmentStack), LASER_COLOR_TAG);
    }

    @Override
    default void setLaserColor(ItemStack attachmentStack, int color) {
        ItemStackNbtHelper.updateTag(attachmentStack, nbt -> nbt.putInt(LASER_COLOR_TAG, color));
    }
}
