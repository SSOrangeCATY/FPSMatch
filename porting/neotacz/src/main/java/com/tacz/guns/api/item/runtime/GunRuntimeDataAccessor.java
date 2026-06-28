package com.tacz.guns.api.item.runtime;

import com.tacz.guns.api.item.ammo.GunAmmoSlot;
import com.tacz.guns.api.item.ammo.GunAmmoSlots;
import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface GunRuntimeDataAccessor {
    String TACZ_RUNTIME_TAG = "TacRuntime";
    String AMMO_SLOTS_TAG = "AmmoSlots";
    String ACTIVE_AMMO_SLOT_TAG = "ActiveAmmoSlot";
    String SLOT_ID_TAG = "SlotId";
    String AMMO_ID_TAG = "AmmoId";
    String AMMO_POOL_ID_TAG = "AmmoPoolId";
    String RUNTIME_ITEM_ID_TAG = "RuntimeItemId";
    String AUTHORITY_ID_TAG = "AuthorityId";
    String PROTECTED_FIELDS_TAG = "ProtectedFields";

    default GunAmmoSlots getAmmoSlots(ItemStack gun, Identifier fallbackAmmoId) {
        return readAmmoSlots(getRuntimeTag(gun), fallbackAmmoId);
    }

    default void setAmmoSlots(ItemStack gun, GunAmmoSlots slots) {
        ItemStackNbtHelper.updateTag(gun, root -> {
            CompoundTag runtime = getRuntimeTag(root);
            runtime.merge(writeAmmoSlots(slots));
            root.put(TACZ_RUNTIME_TAG, runtime);
        });
    }

    default String getActiveAmmoSlotId(ItemStack gun, Identifier fallbackAmmoId) {
        return getAmmoSlots(gun, fallbackAmmoId).activeSlotId();
    }

    default void setActiveAmmoSlotId(ItemStack gun, Identifier fallbackAmmoId, String slotId) {
        setAmmoSlots(gun, getAmmoSlots(gun, fallbackAmmoId).withActiveSlot(slotId));
    }

    default String getRuntimeItemId(ItemStack gun) {
        return ItemStackNbtHelper.getString(getRuntimeTag(ItemStackNbtHelper.getTag(gun)), RUNTIME_ITEM_ID_TAG);
    }

    default void setRuntimeItemId(ItemStack gun, String runtimeItemId) {
        ItemStackNbtHelper.updateTag(gun, root -> {
            CompoundTag runtime = getRuntimeTag(root);
            runtime.putString(RUNTIME_ITEM_ID_TAG, runtimeItemId == null ? "" : runtimeItemId);
            root.put(TACZ_RUNTIME_TAG, runtime);
        });
    }

    default void setRuntimeAuthority(ItemStack gun, GunRuntimeAuthority authority) {
        GunRuntimeAuthority normalized = authority == null ? new GunRuntimeAuthority("", Set.of()) : authority;
        ItemStackNbtHelper.updateTag(gun, root -> {
            CompoundTag runtime = getRuntimeTag(root);
            runtime.putString(AUTHORITY_ID_TAG, normalized.authorityId());
            ListTag fields = new ListTag();
            for (String field : normalized.protectedFields()) {
                fields.add(StringTag.valueOf(field));
            }
            runtime.put(PROTECTED_FIELDS_TAG, fields);
            root.put(TACZ_RUNTIME_TAG, runtime);
        });
    }

    default GunRuntimeAuthority getRuntimeAuthority(ItemStack gun) {
        CompoundTag runtime = getRuntimeTag(gun);
        Set<String> fields = new HashSet<>();
        if (ItemStackNbtHelper.contains(runtime, PROTECTED_FIELDS_TAG, Tag.TAG_LIST)) {
            for (Tag tag : runtime.getListOrEmpty(PROTECTED_FIELDS_TAG)) {
                tag.asString().ifPresent(fields::add);
            }
        }
        return new GunRuntimeAuthority(ItemStackNbtHelper.getString(runtime, AUTHORITY_ID_TAG), fields);
    }

    default boolean canMutateRuntimeField(ItemStack gun, String field, String requester) {
        return getRuntimeAuthority(gun).canMutate(field, requester);
    }

    default boolean setAmmoSlotsIfAuthorized(ItemStack gun, GunAmmoSlots slots, String requester) {
        if (!canMutateRuntimeField(gun, AMMO_SLOTS_TAG, requester)) {
            return false;
        }
        setAmmoSlots(gun, slots);
        return true;
    }

    default boolean setActiveAmmoSlotIfAuthorized(ItemStack gun, Identifier fallbackAmmoId, String slotId, String requester) {
        if (!canMutateRuntimeField(gun, AMMO_SLOTS_TAG, requester)) {
            return false;
        }
        setActiveAmmoSlotId(gun, fallbackAmmoId, slotId);
        return true;
    }

    static CompoundTag getRuntimeTag(CompoundTag root) {
        if (ItemStackNbtHelper.contains(root, TACZ_RUNTIME_TAG, Tag.TAG_COMPOUND)) {
            return ItemStackNbtHelper.getCompoundOrEmpty(root, TACZ_RUNTIME_TAG).copy();
        }
        return new CompoundTag();
    }

    static CompoundTag getRuntimeTag(ItemStack gun) {
        return getRuntimeTag(ItemStackNbtHelper.getTag(gun));
    }

    static CompoundTag writeAmmoSlots(GunAmmoSlots slots) {
        CompoundTag runtime = new CompoundTag();
        ListTag list = new ListTag();
        for (GunAmmoSlot slot : slots.slots()) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putString(SLOT_ID_TAG, slot.slotId());
            slotTag.putString(AMMO_ID_TAG, slot.ammoId().toString());
            slotTag.putString(AMMO_POOL_ID_TAG, slot.ammoPoolId());
            list.add(slotTag);
        }
        runtime.put(AMMO_SLOTS_TAG, list);
        runtime.putString(ACTIVE_AMMO_SLOT_TAG, slots.activeSlotId());
        return runtime;
    }

    static GunAmmoSlots readAmmoSlots(CompoundTag runtime, Identifier fallbackAmmoId) {
        if (!ItemStackNbtHelper.contains(runtime, AMMO_SLOTS_TAG, Tag.TAG_LIST)) {
            return GunAmmoSlots.single(fallbackAmmoId);
        }
        List<GunAmmoSlot> slots = new ArrayList<>();
        for (Tag rawSlot : runtime.getListOrEmpty(AMMO_SLOTS_TAG)) {
            if (!(rawSlot instanceof CompoundTag slotTag)) {
                continue;
            }
            Identifier ammoId = slotTag.getString(AMMO_ID_TAG).map(Identifier::tryParse).orElse(null);
            if (ammoId != null) {
                slots.add(new GunAmmoSlot(
                        slotTag.getString(SLOT_ID_TAG).orElse("main"),
                        ammoId,
                        slotTag.getString(AMMO_POOL_ID_TAG).orElse("")
                ));
            }
        }
        if (slots.isEmpty()) {
            return GunAmmoSlots.single(fallbackAmmoId);
        }
        return new GunAmmoSlots(slots, runtime.getString(ACTIVE_AMMO_SLOT_TAG).orElse(""));
    }
}
