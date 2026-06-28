package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface GunItemDataAccessor extends IGun {
    String GUN_ID_TAG = "GunId";
    String GUN_FIRE_MODE_TAG = "GunFireMode";
    String GUN_HAS_BULLET_IN_BARREL = "HasBulletInBarrel";
    String GUN_CURRENT_AMMO_COUNT_TAG = "GunCurrentAmmoCount";
    String GUN_ATTACHMENT_BASE = "Attachment";
    String GUN_EXP_TAG = "GunLevelExp";
    String GUN_DUMMY_AMMO = "DummyAmmo";
    String GUN_MAX_DUMMY_AMMO = "MaxDummyAmmo";
    String GUN_ATTACHMENT_LOCK = "AttachmentLock";
    String GUN_DISPLAY_ID_TAG = "GunDisplayId";
    String LASER_COLOR_TAG = "LaserColor";
    String GUN_OVERHEAT_TAG = "HeatAmount";
    String GUN_OVERHEAT_LOCK_TAG = "OverHeated";

    @Override
    default boolean useDummyAmmo(ItemStack gun) {
        return ItemStackNbtHelper.contains(gun, GUN_DUMMY_AMMO, Tag.TAG_INT);
    }

    @Override
    default int getDummyAmmoAmount(ItemStack gun) {
        return Math.max(0, ItemStackNbtHelper.getInt(ItemStackNbtHelper.getTag(gun), GUN_DUMMY_AMMO));
    }

    @Override
    default void setDummyAmmoAmount(ItemStack gun, int amount) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putInt(GUN_DUMMY_AMMO, Math.max(amount, 0)));
    }

    @Override
    default void addDummyAmmoAmount(ItemStack gun, int amount) {
        if (!useDummyAmmo(gun)) {
            return;
        }
        int maxDummyAmmo = Integer.MAX_VALUE;
        if (hasMaxDummyAmmo(gun)) {
            maxDummyAmmo = getMaxDummyAmmoAmount(gun);
        }
        amount = Math.min(getDummyAmmoAmount(gun) + amount, maxDummyAmmo);
        int finalAmount = amount;
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putInt(GUN_DUMMY_AMMO, Math.max(finalAmount, 0)));
    }

    @Override
    default boolean hasMaxDummyAmmo(ItemStack gun) {
        return ItemStackNbtHelper.contains(gun, GUN_MAX_DUMMY_AMMO, Tag.TAG_INT);
    }

    @Override
    default int getMaxDummyAmmoAmount(ItemStack gun) {
        return Math.max(0, ItemStackNbtHelper.getInt(ItemStackNbtHelper.getTag(gun), GUN_MAX_DUMMY_AMMO));
    }

    @Override
    default void setMaxDummyAmmoAmount(ItemStack gun, int amount) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putInt(GUN_MAX_DUMMY_AMMO, Math.max(amount, 0)));
    }

    @Override
    default boolean hasAttachmentLock(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_ATTACHMENT_LOCK, Tag.TAG_BYTE)) {
            return ItemStackNbtHelper.getBoolean(nbt, GUN_ATTACHMENT_LOCK);
        }
        return false;
    }

    @Override
    default void setAttachmentLock(ItemStack gun, boolean lock) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putBoolean(GUN_ATTACHMENT_LOCK, lock));
    }

    @Override
    @Nonnull
    default Identifier getGunId(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_ID_TAG, Tag.TAG_STRING)) {
            Identifier gunId = Identifier.tryParse(ItemStackNbtHelper.getString(nbt, GUN_ID_TAG));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_GUN_ID);
        }
        return DefaultAssets.EMPTY_GUN_ID;
    }

    @Override
    default void setGunId(ItemStack gun, @Nullable Identifier gunId) {
        if (gunId != null) {
            ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putString(GUN_ID_TAG, gunId.toString()));
        }
    }

    @Override
    @NotNull
    default Identifier getGunDisplayId(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_DISPLAY_ID_TAG, Tag.TAG_STRING)) {
            Identifier gunDisplayId = Identifier.tryParse(ItemStackNbtHelper.getString(nbt, GUN_DISPLAY_ID_TAG));
            return Objects.requireNonNullElse(gunDisplayId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID);
        }
        return DefaultAssets.DEFAULT_GUN_DISPLAY_ID;
    }

    @Override
    default void setGunDisplayId(ItemStack gun, Identifier displayId) {
        if (displayId != null) {
            ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putString(GUN_DISPLAY_ID_TAG, displayId.toString()));
        }
    }

    @Override
    default int getLevel(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_EXP_TAG, Tag.TAG_INT)) {
            return getLevel(ItemStackNbtHelper.getInt(nbt, GUN_EXP_TAG));
        }
        return 0;
    }

    @Override
    default int getExp(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_EXP_TAG, Tag.TAG_INT)) {
            return ItemStackNbtHelper.getInt(nbt, GUN_EXP_TAG);
        }
        return 0;
    }

    @Override
    default int getExpToNextLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level >= getMaxLevel()) {
            return 0;
        }
        int nextLevelExp = getExp(level + 1);
        return nextLevelExp - exp;
    }

    @Override
    default int getExpCurrentLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level <= 0) {
            return exp;
        } else {
            return exp - getExp(level - 1);
        }
    }

    @Override
    default FireMode getFireMode(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_FIRE_MODE_TAG, Tag.TAG_STRING)) {
            return FireMode.valueOf(ItemStackNbtHelper.getString(nbt, GUN_FIRE_MODE_TAG));
        }
        return FireMode.UNKNOWN;
    }

    @Override
    default void setFireMode(ItemStack gun, @Nullable FireMode fireMode) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putString(GUN_FIRE_MODE_TAG, fireMode != null ? fireMode.name() : FireMode.UNKNOWN.name()));
    }

    @Override
    default int getCurrentAmmoCount(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_CURRENT_AMMO_COUNT_TAG, Tag.TAG_INT)) {
            return ItemStackNbtHelper.getInt(nbt, GUN_CURRENT_AMMO_COUNT_TAG);
        }
        return 0;
    }

    @Override
    default void setCurrentAmmoCount(ItemStack gun, int ammoCount) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putInt(GUN_CURRENT_AMMO_COUNT_TAG, Math.max(ammoCount, 0)));
    }

    default boolean setGunIdIfAuthorized(ItemStack gun, @Nullable Identifier gunId, String requester) {
        if (!canMutateRuntimeField(gun, GUN_ID_TAG, requester)) {
            return false;
        }
        setGunId(gun, gunId);
        return true;
    }

    default boolean setGunDisplayIdIfAuthorized(ItemStack gun, @Nullable Identifier displayId, String requester) {
        if (!canMutateRuntimeField(gun, GUN_DISPLAY_ID_TAG, requester)) {
            return false;
        }
        setGunDisplayId(gun, displayId);
        return true;
    }

    default boolean setFireModeIfAuthorized(ItemStack gun, @Nullable FireMode fireMode, String requester) {
        if (!canMutateRuntimeField(gun, GUN_FIRE_MODE_TAG, requester)) {
            return false;
        }
        setFireMode(gun, fireMode);
        return true;
    }

    default boolean setCurrentAmmoCountIfAuthorized(ItemStack gun, int ammoCount, String requester) {
        if (!canMutateRuntimeField(gun, GUN_CURRENT_AMMO_COUNT_TAG, requester)) {
            return false;
        }
        setCurrentAmmoCount(gun, ammoCount);
        return true;
    }

    @Override
    default void reduceCurrentAmmoCount(ItemStack gun) {
        // 只在不使用背包直读的情况下减少 AmmoCount
        if (!useInventoryAmmo(gun)) {
            setCurrentAmmoCount(gun, getCurrentAmmoCount(gun) - 1);
        }
    }

    @Override
    @Nullable
    default CompoundTag getAttachmentTag(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return null;
        }
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        if (ItemStackNbtHelper.contains(nbt, key, Tag.TAG_COMPOUND)) {
            CompoundTag allItemStackTag = ItemStackNbtHelper.getCompoundOrEmpty(nbt, key);
            ItemStack attachment = ItemStackNbtHelper.parseLegacyStackSubset(allItemStackTag);
            if (!attachment.isEmpty()) {
                return ItemStackNbtHelper.getTag(attachment);
            }
            if (ItemStackNbtHelper.contains(allItemStackTag, "tag", Tag.TAG_COMPOUND)) {
                return ItemStackNbtHelper.getCompoundOrEmpty(allItemStackTag, "tag");
            }
        }
        return null;
    }

    @Override
    @NotNull
    default ItemStack getBuiltinAttachment(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index != null){
            var builtin = index.getGunData().getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                return AttachmentItemBuilder.create().setId(builtin.get(type)).build();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    default ItemStack getAttachment(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return ItemStack.EMPTY;
        }
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        if (ItemStackNbtHelper.contains(nbt, key, Tag.TAG_COMPOUND)) {
            return ItemStackNbtHelper.parseLegacyStackSubset(ItemStackNbtHelper.getCompoundOrEmpty(nbt, key));
        }
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    default Identifier getBuiltInAttachmentId(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index != null){
            var builtin = index.getGunData().getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                return builtin.get(type);
            }
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    @Nonnull
    default Identifier getAttachmentId(ItemStack gun, AttachmentType type) {
        CompoundTag attachmentTag = this.getAttachmentTag(gun, type);
        if (attachmentTag != null) {
            return AttachmentItemDataAccessor.getAttachmentIdFromTag(attachmentTag);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    default void installAttachment(@Nonnull ItemStack gun, @Nonnull ItemStack attachment) {
        if (!allowAttachment(gun, attachment)) {
            return;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) {
            return;
        }
        String key = GUN_ATTACHMENT_BASE + iAttachment.getType(attachment).name();
        CompoundTag attachmentTag = ItemStackNbtHelper.saveLegacyStackSubset(attachment);
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.put(key, attachmentTag));
    }

    @Override
    default void unloadAttachment(@Nonnull ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return;
        }
        String key = GUN_ATTACHMENT_BASE + type.name();
        CompoundTag attachmentTag = ItemStackNbtHelper.saveLegacyStackSubset(ItemStack.EMPTY);
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.put(key, attachmentTag));
    }

    @Override
    default float getAimingZoom(ItemStack gunItem) {
        float zoom = 1;
        Identifier scopeId = this.getAttachmentId(gunItem, AttachmentType.SCOPE);
        boolean builtin = false;
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
            builtin = true;
        }
        if (!DefaultAssets.isEmptyAttachmentId(scopeId)) {
            CompoundTag attachmentTag = this.getAttachmentTag(gunItem, AttachmentType.SCOPE);
            int zoomNumber = builtin ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentTag);
            float[] zooms = TimelessAPI.getClientAttachmentIndex(scopeId).map(ClientAttachmentIndex::getZoom).orElse(null);
            if (zooms != null) {
                zoom = zooms[zoomNumber % zooms.length];
            }
        } else {
            zoom = TimelessAPI.getGunDisplay(gunItem).map(GunDisplayInstance::getIronZoom).orElse(1f);
        }
        return zoom;
    }

    @Override
    default boolean hasBulletInBarrel(ItemStack gun) {
        CompoundTag nbt = ItemStackNbtHelper.getTag(gun);
        if (ItemStackNbtHelper.contains(nbt, GUN_HAS_BULLET_IN_BARREL, Tag.TAG_BYTE)) {
            return ItemStackNbtHelper.getBoolean(nbt, GUN_HAS_BULLET_IN_BARREL);
        }
        return false;
    }

    @Override
    default void setBulletInBarrel(ItemStack gun, boolean bulletInBarrel) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putBoolean(GUN_HAS_BULLET_IN_BARREL, bulletInBarrel));
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack gun) {
        return ItemStackNbtHelper.contains(gun, LASER_COLOR_TAG, Tag.TAG_INT);
    }

    @Override
    default int getLaserColor(ItemStack gun) {
        if (!hasCustomLaserColor(gun)) {
            return 0xFF0000;
        }
        return ItemStackNbtHelper.getInt(ItemStackNbtHelper.getTag(gun), LASER_COLOR_TAG);
    }

    @Override
    default void setLaserColor(ItemStack gun, int color) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putInt(LASER_COLOR_TAG, color));
    }

    /**
     * Heat Data
     */
    @Override
    default boolean hasHeatData(ItemStack gun) {
        return ItemStackNbtHelper.contains(gun, GUN_OVERHEAT_TAG, Tag.TAG_FLOAT);
    }

    @Override
    default boolean isOverheatLocked(ItemStack gun) {
        return ItemStackNbtHelper.getBoolean(ItemStackNbtHelper.getTag(gun), GUN_OVERHEAT_LOCK_TAG);
    }

    @Override
    default void setOverheatLocked(ItemStack gun, boolean locked) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putBoolean(GUN_OVERHEAT_LOCK_TAG, locked));
    }

    @Override
    default float getHeatAmount(ItemStack gun) {
        if (hasHeatData(gun)) {
            return ItemStackNbtHelper.getFloat(ItemStackNbtHelper.getTag(gun), GUN_OVERHEAT_TAG);
        }
        return 0f;
    }

    @Override
    default void setHeatAmount(ItemStack gun, float amount) {
        ItemStackNbtHelper.updateTag(gun, nbt -> nbt.putFloat(GUN_OVERHEAT_TAG, amount >= 0 ? amount : 0f));
    }

    @Override
    default float lerpRPM(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
                .map(index -> index.getGunData().getHeatData())
                .map(heatData -> {
                    float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                    return Mth.lerp(heatPercentage, heatData.getMinRpmMod(), heatData.getMaxRpmMod());
                }).orElse(1f);
    }

    @Override
    default float lerpInaccuracy(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
                .map(index -> index.getGunData().getHeatData())
                .map(heatData -> {
                    float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                    return Mth.lerp(heatPercentage, heatData.getMinInaccuracy(), heatData.getMaxInaccuracy());
                }).orElse(1f);
    }
}
