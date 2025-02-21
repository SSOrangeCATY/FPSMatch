package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 用于定义可保存数据的接口。
 * <p>
 * 该接口提供了一种标准化的方式来序列化和反序列化数据，并支持从文件中读取和写入数据。
 * 它使用 {@link Codec} 来实现数据的编解码，并通过 Gson 进行 JSON 操作。
 * 实现该接口的类需要提供一个具体的 {@link Codec} 实例，用于定义数据的编解码逻辑。
 */
public interface ISavedData<T> {
    /**
     * 获取当前数据类型的编解码器。
     * <p>
     * 实现类需要提供一个具体的 {@link Codec} 实例，用于定义数据的编解码逻辑。
     *
     * @return 数据类型的编解码器
     */
    Codec<T> codec();

    /**
     * 获取读取数据后的处理逻辑。
     * <p>
     * 默认实现为空操作，具体逻辑需要在实现类中覆盖该方法或通过 {@link SaveHolder} 提供。
     *
     * @apiNote 在数据类中实现该方法无效。需要在 {@link SaveHolder} 中提供处理数据逻辑。
     * @return 一个 Consumer，用于处理解码后的数据
     */
    default Consumer<T> readHandler() {
        return (data) -> {};
    }

    /**
     * 从 JSON 元素中解码数据。
     * <p>
     * 使用 {@link Codec} 将 JSON 元素解码为指定的数据类型。
     *
     * @param json JSON 元素
     * @return 解码后的数据
     */
    default T decodeFromJson(JsonElement json) {
        return this.codec().decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        }).getFirst();
    }

    /**
     * 将数据编码为 JSON 元素。
     * <p>
     * 使用 {@link Codec} 将数据编码为 JSON 元素。
     *
     * @param data 待编码的数据
     * @return 编码后的 JSON 元素
     */
    default JsonElement encodeToJson(T data) {
        return this.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> {
            throw new RuntimeException(e);
        });
    }

    /**
     * 获取目录读取器。
     * <p>
     * <p>该方法返回一个 Consumer，用于遍历指定目录中的所有文件，并尝试从中读取数据。
     * <p>每个文件的内容将被解析为 JSON，然后通过 {@link #decodeFromJson(JsonElement)} 解码为数据对象。
     * <p>解码后的数据将通过 {@link #readHandler()} 进行处理。
     * <p>只读取json文件。
     * @return 目录读取器
     */
    default Consumer<File> getReader() {
        return (directory) -> {
            if (directory.exists() && directory.isDirectory()) {
                for (File file : Objects.requireNonNull(directory.listFiles())) {
                    if (file.isFile() && file.getName().endsWith("."+this.getFileType())) {
                        try {
                            FileReader reader = new FileReader(file);
                            JsonElement element = new Gson().fromJson(reader, JsonElement.class);
                            T data = this.decodeFromJson(element);
                            this.readHandler().accept(data);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                System.out.println("error : " + directory.getName() + " data folder is not a directory or doesn't exist.");
            }
        };
    }

    /**
     * 获取文件写入器。
     * <p>
     * 该方法返回一个 Consumer，用于将数据写入指定目录中的文件。
     * 如果目录不存在，则会尝试创建目录。
     * 如果文件不存在，则会尝试创建文件。
     * 数据将通过 {@link #encodeToJson(T)} 编码为 JSON，然后写入文件。
     * 如果文件已存在，则会尝试读取旧数据并与新数据合并，合并逻辑由 {@link #mergeHandler(T, T)} 定义。
     *
     * @param data 待写入的数据
     * @param fileName 文件名（不包含扩展名）
     * @return 文件写入器
     */
    default Consumer<File> getWriter(T data, String fileName) {
        return (directory) -> {
            if (!directory.exists()) {
                if (!directory.mkdirs()) throw new RuntimeException("error : can't create " + directory.getName() + " data folder.");
            }
            if (directory.isDirectory()) {
                File file = new File(directory, fileName + "." + this.getFileType());
                try {
                    if (!file.exists()) {
                        if (!file.createNewFile()) throw new RuntimeException("error : can't create " + fileName + " data file.");
                    }
                    FileReader reader = new FileReader(file);
                    JsonElement element = new Gson().fromJson(reader, JsonElement.class);
                    T old = this.decodeFromJson(element);
                    T merged = this.mergeHandler(old, data);
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String jsonStr = gson.toJson(this.encodeToJson(merged));
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(jsonStr);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("error : " + directory.getName() + " data folder is not a directory or doesn't exist.");
            }
        };
    }

    /**
     * 检查数据是否为全局数据。
     * <p>
     * 默认返回 false，表示数据不是全局数据。
     * 全局数据通常用于存储跨多个实例共享的信息。
     *
     * @return 如果是全局数据，返回 true；否则返回 false
     */
    default boolean isGlobal() {
        return false;
    }

    /**
     * 合并旧数据和新数据的逻辑。
     * <p>
     * 默认实现直接返回新数据，子类可以通过覆盖该方法实现自定义的合并逻辑。
     *
     * @param oldData 旧数据
     * @param newData 新数据
     * @return 合并后的数据
     */
    default T mergeHandler(T oldData, T newData) {
        return newData;
    }

    default String getFileType(){
        return "json";
    }
}