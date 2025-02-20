package com.phasetranscrystal.fpsmatch.core.data.save;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Consumer;


/**
 * 用于包装数据类的数据处理层。<p>不包含数据，仅提供于数据处理。
 *
 * @param <T> 数据类
 * @see RegisterFPSMSaveDataEvent
 */
public class SaveHolder<T> implements ISavedData<T> {
    Codec<T> codec;
    Consumer<T> readHandler;
    Consumer<FPSMDataManager> writeHandler;
    @Nullable BiFunction<T, T, T> margeHandler;
    boolean isGlobal;

    public SaveHolder(Codec<T> codec, Consumer<T> readHandler, Consumer<FPSMDataManager> writeHandler){
        this.codec = codec;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.isGlobal = false;
    }

    public SaveHolder(Codec<T> codec, Consumer<T> readHandler, Consumer<FPSMDataManager> writeHandler, boolean isGlobal){
        this.codec = codec;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.isGlobal = isGlobal;
    }

    public SaveHolder(Codec<T> codec, Consumer<T> readHandler, Consumer<FPSMDataManager> writeHandler, @Nullable BiFunction<T, T, T> margeHandler){
        this.codec = codec;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.margeHandler = margeHandler;
        this.isGlobal = false;
    }

    public SaveHolder(Codec<T> codec, Consumer<T> readHandler, Consumer<FPSMDataManager> writeHandler, @Nullable BiFunction<T, T, T> margeHandler, boolean isGlobal){
        this.codec = codec;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.margeHandler = margeHandler;
        this.isGlobal = isGlobal;
    }

    @Override
    public Codec<T> codec() {
        return codec;
    }

    @Override
    public Consumer<T> readHandler() {
        return readHandler;
    }

    public Consumer<FPSMDataManager> writeHandler() {
        return writeHandler;
    }

    @Override
    public boolean isGlobal() {
        return isGlobal;
    }

    @Override
    public T mergeHandler(T oldData, T newData) {
        return margeHandler == null ? ISavedData.super.mergeHandler(oldData, newData) : margeHandler.apply(oldData, newData);
    }
}
