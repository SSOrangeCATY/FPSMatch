package com.phasetranscrystal.fpsmatch.core.data.save;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;

import java.util.function.Consumer;


/**
 * 用于包装数据类的数据处理层。<p>不包含数据，仅提供于数据处理。
 *
 * @param <T> 数据类
 * @see RegisterFPSMSaveDataEvent
 */
public record SaveHolder<T>(Codec<T> codec, Consumer<T> readerHandler,Consumer<FPSMDataManager> writerHandler) implements ISavedData<T> {
}
