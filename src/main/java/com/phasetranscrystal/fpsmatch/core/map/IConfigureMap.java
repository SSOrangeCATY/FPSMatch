package com.phasetranscrystal.fpsmatch.core.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.Setting;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.ISavePort;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;

/**
 * 地图配置接口，用于管理地图相关的配置项。
 * <p>
 * 该接口扩展了 {@link IMap}，并添加了配置管理的功能。它允许地图通过 {@link Setting} 存储和管理配置项，
 * 并支持将配置项保存到 JSON 文件中，以及从 JSON 文件中加载配置项。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>管理地图的配置项集合。</li>
 *     <li>将配置项序列化为 JSON 格式。</li>
 *     <li>从 JSON 格式反序列化配置项。</li>
 *     <li>加载和保存地图配置文件。</li>
 *     <li>提供快捷方法添加不同类型（如整型、浮点型、布尔型等）的配置项。</li>
 * </ul>
 *
 * @param <T> 地图的类型，必须继承自 {@link BaseMap}。
 */
public interface IConfigureMap<T extends BaseMap> extends IMap<T> {

    /**
     * 获取当前地图的所有配置项集合。
     *
     * @return 配置项集合。
     */
    Collection<Setting<?>> settings();

    /**
     * 向地图中添加一个新的配置项。
     *
     * @param setting 要添加的配置项。
     * @param <I>     配置项的值类型。
     * @return 添加的配置项。
     */
    <I> Setting<I> addSetting(Setting<I> setting);

    /**
     * 将所有配置项序列化为 JSON 格式。
     * <p>
     * 遍历所有配置项，并调用每个配置项的 {@link Setting#toJson()} 方法，
     * 将其值编码为 JSON 元素并添加到一个 JSON 对象中。
     *
     * @return 包含所有配置项的 JSON 对象。
     */
    default JsonElement configToJson() {
        JsonElement json = new JsonObject();
        for (Setting<?> setting : settings()) {
            json.getAsJsonObject().add(setting.getConfigName(), setting.toJson());
        }
        return json;
    }

    /**
     * 从 JSON 格式反序列化配置项。
     * <p>
     * 遍历 JSON 对象中的每个键值对，并根据配置项的名称查找对应的配置项。
     * 如果找到匹配的配置项，则调用其 {@link Setting#fromJson(JsonElement)} 方法进行反序列化。
     * 如果未找到匹配的配置项，则记录警告日志。
     *
     * @param json 包含配置项的 JSON 对象。
     */
    default void configFromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        for (Setting<?> setting : settings()) {
            if (jsonObject.has(setting.getConfigName())) {
                setting.fromJson(jsonObject.get(setting.getConfigName()));
            } else {
                FPSMatch.LOGGER.warn("Setting " + setting.getConfigName() + " not found in config file.");
            }
        }
    }

    /**
     * 获取当前地图的配置文件路径。
     * <p>
     * 如果地图实现了 {@link ISavePort} 接口，则根据地图名称生成配置文件路径。
     * 如果地图未实现该接口，则记录错误日志并返回 null。
     *
     * @return 配置文件路径，或 null（如果地图未实现 ISavedData 接口）。
     */
    default File getConfigFile() {
        File file = FPSMDataManager.getInstance().getSaveFolder(this.getMap());
        if(file == null){
            FPSMatch.LOGGER.error("Failed to get config file for map " + this.getMap().getMapName() + " because ：Map is not implement ISavedData interface.");
            return null;
        } else {
            return new File(file, this.getMap().getMapName() + ".cfg");
        }

    }

    /**
     * 加载地图配置文件。
     * <p>
     * 从配置文件路径读取 JSON 数据，并调用 {@link #configFromJson(JsonElement)} 方法反序列化配置项。
     * 如果配置文件不存在或读取失败，则记录错误日志。
     */
    default void loadConfig() {
        File dataFile = getConfigFile();
        if (dataFile == null) return;
        try {
            if (dataFile.exists()) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                FileReader reader = new FileReader(dataFile);
                this.configFromJson(gson.fromJson(reader, JsonElement.class));
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存地图配置文件。
     * <p>
     * 将所有配置项序列化为 JSON 格式，并写入到配置文件路径。
     * 如果配置文件不存在，则创建新文件。
     * 如果保存失败，则记录错误日志。
     */
    default void saveConfig() {
        File dataFile = getConfigFile();
        if (dataFile == null) return;
        try {
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(dataFile);
            gson.toJson(this.configToJson(), writer);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 添加一个整型配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<Integer> addSetting(String configName, int defaultValue) {
        return addSetting(new Setting<>(configName, Codec.INT, defaultValue));
    }

    /**
     * 添加一个长整型配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<Long> addSetting(String configName, long defaultValue) {
        return addSetting(new Setting<>(configName, Codec.LONG, defaultValue));
    }

    /**
     * 添加一个浮点型配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<Float> addSetting(String configName, float defaultValue) {
        return addSetting(new Setting<>(configName, Codec.FLOAT, defaultValue));
    }

    /**
     * 添加一个双精度浮点型配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<Double> addSetting(String configName, double defaultValue) {
        return addSetting(new Setting<>(configName, Codec.DOUBLE, defaultValue));
    }

    /**
     * 添加一个字节型配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<Byte> addSetting(String configName, byte defaultValue) {
        return addSetting(new Setting<>(configName, Codec.BYTE, defaultValue));
    }

    /**
     * 添加一个布尔型配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<Boolean> addSetting(String configName, boolean defaultValue) {
        return addSetting(new Setting<>(configName, Codec.BOOL, defaultValue));
    }

    /**
     * 添加一个字符串配置项。
     *
     * @param configName 配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    default Setting<String> addSetting(String configName, String defaultValue) {
        return addSetting(new Setting<>(configName, Codec.STRING, defaultValue));
    }
}