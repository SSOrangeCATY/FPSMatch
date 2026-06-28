package com.tacz.guns.api.util;

import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * 一个简单的NBT包装，用于在Lua中访问NBT数据。<br/>
 * 暂时只支持基本数据类型的读写，不支持数组等复杂数据类型。
 */
@SuppressWarnings("unused")
public final class LuaNbtAccessor {
    private final CompoundTag nbt;
    private final Runnable onChanged;

    public LuaNbtAccessor(CompoundTag nbt) {
        this(nbt, () -> {
        });
    }

    private LuaNbtAccessor(CompoundTag nbt, Runnable onChanged) {
        this.nbt = Objects.requireNonNull(nbt);
        this.onChanged = Objects.requireNonNull(onChanged);
    }

    public static LuaNbtAccessor from(ItemStack stack) {
        CompoundTag tag = ItemStackNbtHelper.getTag(stack);
        return new LuaNbtAccessor(tag, () -> ItemStackNbtHelper.setTag(stack, tag));
    }

    public static LuaNbtAccessor from(CompoundTag nbt) {
        return new LuaNbtAccessor(nbt);
    }

    private void markChanged() {
        onChanged.run();
    }

    public boolean contains(String key) {
        return nbt.contains(key);
    }

    public boolean contains(String key, int type) {
        return ItemStackNbtHelper.contains(nbt, key, type);
    }

    public LuaNbtAccessor newCompoundTag() {
        return new LuaNbtAccessor(new CompoundTag());
    }

    public int getInt(String key) {
        return nbt.getIntOr(key, 0);
    }

    public double getDouble(String key) {
        return nbt.getDoubleOr(key, 0.0D);
    }

    public float getFloat(String key) {
        return nbt.getFloatOr(key, 0.0F);
    }

    public long getLong(String key) {
        return nbt.getLongOr(key, 0L);
    }

    public String getString(String key) {
        return nbt.getStringOr(key, "");
    }

    public boolean getBoolean(CompoundTag nbt, String key) {
        return nbt.getBooleanOr(key, false);
    }

    public LuaNbtAccessor getCompound(String key) {
        if (!ItemStackNbtHelper.contains(nbt, key, Tag.TAG_COMPOUND)) {
            return null;
        }
        return new LuaNbtAccessor(nbt.getCompoundOrEmpty(key), this::markChanged);
    }

    public void putInt(String key, int value) {
        nbt.putInt(key, value);
        markChanged();
    }

    public void putDouble(String key, double value) {
        nbt.putDouble(key, value);
        markChanged();
    }

    public void putFloat(String key, float value) {
        nbt.putFloat(key, value);
        markChanged();
    }

    public void putLong(String key, long value) {
        nbt.putLong(key, value);
        markChanged();
    }

    public void putString(String key, String value) {
        nbt.putString(key, value);
        markChanged();
    }

    public void putBoolean(String key, boolean value) {
        nbt.putBoolean(key, value);
        markChanged();
    }

    /**
     * 向当前的NbtCompound中添加一个新的Compound
     *
     * @param key   键
     * @param value 在脚本中请使用{@link LuaNbtAccessor#newCompoundTag()}创建一个新的LuaNbtAccessor对象
     */
    public void putCompound(String key, LuaNbtAccessor value) {
        if (value != null) {
            nbt.put(key, value.nbt());
            markChanged();
        }
    }

    @ApiStatus.Internal
    public CompoundTag nbt() {
        return nbt;
    }
}
