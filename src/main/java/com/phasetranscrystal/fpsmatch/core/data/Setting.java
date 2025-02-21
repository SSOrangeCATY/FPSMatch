package com.phasetranscrystal.fpsmatch.core.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

/**
 * 用于存储和管理配置项的通用类。
 * <p>
 * 该类通过指定的 Codec（编解码器）来序列化和反序列化配置值，支持将配置值存储为 JSON 格式，并从 JSON 中恢复。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>存储配置项的名称、默认值和当前值。</li>
 *     <li>提供配置值的获取和设置方法。</li>
 *     <li>将配置值编码为 JSON 元素。</li>
 *     <li>从 JSON 元素中解码配置值。</li>
 * </ul>
 *
 * @param <T> 配置值的类型，必须与指定的 Codec 兼容。
 */
public class Setting<T> {
    /**
     * 用于序列化和反序列化配置值的编解码器。
     */
    private final Codec<T> codec;

    /**
     * 配置项的名称，用于标识配置项。
     */
    private final String configName;

    /**
     * 当前配置值。
     */
    private T value;

    /**
     * 构造一个新的配置项实例。
     *
     * @param configName 配置项的名称。
     * @param codec      用于处理配置值的编解码器。
     * @param defaultValue 配置项的默认值。
     */
    public Setting(String configName, Codec<T> codec, T defaultValue) {
        this.configName = configName;
        this.codec = codec;
        this.value = defaultValue;
    }

    /**
     * 获取当前配置值。
     *
     * @return 当前配置值。
     */
    public T get() {
        return value;
    }

    /**
     * 设置新的配置值。
     *
     * @param value 新的配置值。
     * @return 设置后的配置值。
     */
    public T set(T value) {
        this.value = value;
        return value;
    }

    /**
     * 获取配置项的编解码器。
     *
     * @return 配置项的编解码器。
     */
    public Codec<T> codec() {
        return codec;
    }

    /**
     * 获取配置项的名称。
     *
     * @return 配置项的名称。
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * 将当前配置值编码为 JSON 元素。
     *
     * @return 配置值的 JSON 表示。
     * @throws RuntimeException 如果编码过程中发生错误。
     */
    public JsonElement toJson() {
        return this.codec().encodeStart(JsonOps.INSTANCE, this.value).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        });
    }

    /**
     * 从 JSON 元素中解码配置值。
     *
     * @param json 配置值的 JSON 表示。
     * @throws RuntimeException 如果解码过程中发生错误。
     */
    public void fromJson(JsonElement json) {
        this.value = this.codec().decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();
    }
}