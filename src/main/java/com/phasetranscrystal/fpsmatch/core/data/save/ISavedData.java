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

public interface ISavedData<T> {
    Codec<T> codec();

    /**
     * @apiNote  在数据类中实现该方法无效。<p> 需要在 {@link SaveHolder} 中提供处理数据逻辑。
     * @return 一个 Consumer，用于处理解码后的数据。
     */
    default Consumer<T> readerHandler() {
        return (T) -> {};
    }
    default T decodeFromJson(JsonElement json) {
        return this.codec().decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

    default JsonElement encodeToJson(T data) {
        return this.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

    default Consumer<File> getReader() {
        return (directory) -> {
            if (directory.exists() && directory.isDirectory()) {
                for (File file : Objects.requireNonNull(directory.listFiles())) {
                    if (file.isFile()) {
                        try {
                            FileReader reader = new FileReader(file);
                            JsonElement element = new Gson().fromJson(reader,JsonElement.class);
                            T data = this.decodeFromJson(element);
                            this.readerHandler().accept(data);
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

    default Consumer<File> getWriter(T data,String fileName) {
        return (directory) -> {
            if(!directory.exists()){
                if(!directory.mkdirs()) throw new RuntimeException("error : can't create "+directory.getName()+" data folder.");
            }
            if (directory.isDirectory()) {
                File file = new File(directory,fileName+".json");
                try {
                    if(!file.exists()){
                        if(!file.createNewFile()) throw new RuntimeException("error : can't create "+fileName+" data file.");
                    }
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String jsonStr = gson.toJson(this.encodeToJson(data));
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(jsonStr);
                    } catch (IOException e) {
                        throw  new RuntimeException(e);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("error : " + directory.getName() + " data folder is not a directory or doesn't exist.");
            }
        };
    }
}
