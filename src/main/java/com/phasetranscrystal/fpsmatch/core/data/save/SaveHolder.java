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
public class SaveHolder<T> implements ISavePort<T> {
    private final Codec<T> codec;
    private final Consumer<T> readHandler;
    private final Consumer<FPSMDataManager> writeHandler;
    private final boolean isGlobal;
    @Nullable
    private final BiFunction<@Nullable T, T, T> mergeHandler;
    private final String fileType;

    public static class Builder<T> {
        private final Codec<T> codec;
        private Consumer<T> readHandler = data -> {};
        private Consumer<FPSMDataManager> writeHandler = manager -> {};
        private boolean isGlobal = false;
        private BiFunction<@Nullable T, T, T> mergeHandler = (oldData, newData) -> newData;
        private String fileType = "json";

        public Builder(Codec<T> codec) {
            this.codec = codec;
        }

        public Builder<T> withReadHandler(Consumer<T> readHandler) {
            this.readHandler = readHandler;
            return this;
        }

        public Builder<T> withWriteHandler(Consumer<FPSMDataManager> writeHandler) {
            this.writeHandler = writeHandler;
            return this;
        }

        public Builder<T> isGlobal(boolean isGlobal) {
            this.isGlobal = isGlobal;
            return this;
        }

        public Builder<T> withMergeHandler(BiFunction<@Nullable T, T, T> mergeHandler) {
            this.mergeHandler = mergeHandler;
            return this;
        }

        public Builder<T> withFileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public SaveHolder<T> build() {
            return new SaveHolder<>(codec, readHandler, writeHandler, isGlobal, mergeHandler, fileType);
        }
    }

    private SaveHolder(Codec<T> codec, Consumer<T> readHandler, Consumer<FPSMDataManager> writeHandler,
                       boolean isGlobal, @Nullable BiFunction<@Nullable T, T, T> mergeHandler, String fileType) {
        this.codec = codec;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.isGlobal = isGlobal;
        this.mergeHandler = mergeHandler;
        this.fileType = fileType;
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
    public T mergeHandler(@Nullable T oldData, T newData) {
        return mergeHandler == null ? ISavePort.super.mergeHandler(oldData, newData) : mergeHandler.apply(oldData, newData);
    }

    @Override
    public String getFileType() {
        return fileType;
    }
}