package com.tacz.guns.entity.sync.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataHolder {
    private static final String ENTRIES_TAG = "Entries";
    private static final String CLASS_KEY_TAG = "ClassKey";
    private static final String DATA_KEY_TAG = "DataKey";
    private static final String VALUE_TAG = "Value";

    public Map<SyncedDataKey<?, ?>, DataEntry<?, ?>> dataMap = new HashMap<>();
    private boolean dirty = false;

    @SuppressWarnings("unchecked")
    public <E extends Entity, T> boolean set(E entity, SyncedDataKey<?, ?> key, T value) {
        DataEntry<E, T> entry = (DataEntry<E, T>) this.dataMap.computeIfAbsent(key, DataEntry::new);
        if (!entry.getValue().equals(value)) {
            boolean dirty = !entity.level().isClientSide() && entry.getKey().syncMode() != SyncedDataKey.SyncMode.NONE;
            entry.setValue(value, dirty);
            this.dirty = dirty;
            return true;
        }
        return false;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <E extends Entity, T> T get(SyncedDataKey<E, T> key) {
        return (T) this.dataMap.computeIfAbsent(key, DataEntry::new).getValue();
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clean() {
        this.dirty = false;
        this.dataMap.forEach((key, entry) -> entry.clean());
    }

    public List<DataEntry<?, ?>> gatherDirty() {
        return this.dataMap.values().stream().filter(DataEntry::isDirty).filter(entry -> entry.getKey().syncMode() != SyncedDataKey.SyncMode.NONE).collect(Collectors.toList());
    }

    public List<DataEntry<?, ?>> gatherAll() {
        return this.dataMap.values().stream().filter(entry -> entry.getKey().syncMode() != SyncedDataKey.SyncMode.NONE).collect(Collectors.toList());
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        this.dataMap.forEach((key, entry) -> {
            if (key.save()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString(CLASS_KEY_TAG, key.classKey().id().toString());
                entryTag.putString(DATA_KEY_TAG, key.id().toString());
                entryTag.put(VALUE_TAG, entry.writeValue());
                entries.add(entryTag);
            }
        });
        tag.put(ENTRIES_TAG, entries);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        this.dataMap.clear();
        for (Tag rawEntry : tag.getListOrEmpty(ENTRIES_TAG)) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }
            Identifier classKey = entryTag.getString(CLASS_KEY_TAG).map(Identifier::tryParse).orElse(null);
            Identifier dataKey = entryTag.getString(DATA_KEY_TAG).map(Identifier::tryParse).orElse(null);
            Tag value = entryTag.get(VALUE_TAG);
            if (classKey == null || dataKey == null || value == null) {
                continue;
            }
            SyncedClassKey<?> syncedClassKey = SyncedEntityData.instance().getClassKey(classKey);
            if (syncedClassKey == null) {
                continue;
            }
            SyncedDataKey<?, ?> syncedDataKey = SyncedEntityData.instance().getKey(syncedClassKey, dataKey);
            if (syncedDataKey == null || !syncedDataKey.save()) {
                continue;
            }
            DataEntry<?, ?> entry = new DataEntry<>(syncedDataKey);
            entry.readValue(value);
            this.dataMap.put(syncedDataKey, entry);
        }
        this.clean();
    }
}
