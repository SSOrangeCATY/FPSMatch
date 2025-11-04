package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 用于网络传输的配置项包装类
 */
public record PackedSetting<T>(String name, T value) {

    /**
     * 创建通用的编解码器
     */
    public static <T> Codec<PackedSetting<T>> codec(Codec<T> valueCodec) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(PackedSetting::name),
                valueCodec.fieldOf("value").forGetter(PackedSetting::value)
        ).apply(instance, PackedSetting::new));
    }

    /**
     * 从 Setting 创建 PackedSetting
     */
    public static <T> PackedSetting<T> fromSetting(Setting<T> setting) {
        return new PackedSetting<>(setting.getConfigName(), setting.get());
    }
}