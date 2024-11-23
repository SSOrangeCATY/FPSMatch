package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.BaseMap;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class DataProvider<T, I extends ISavedData<T>> {
    Class<I> clazz;
    Codec<T> codec;
    File file;

    public DataProvider(Class<I> clazz, Codec<T> codec, File file){
        this.clazz = clazz;
        this.codec = codec;
        this.file = file;
    }

    public Class<I> getClazz(){
        return clazz;
    }

    public Codec<T> getCodec() {
        return codec;
    }

    public File getFile() {
        return file;
    }

    public <M extends BaseMap> T decode(M map){
        if(map.getClass().isInstance(this.getClazz())){
            return this.decodeFromJson(this.getJson(this.getFile()));
        }else{
            return null;
        }
    }

    public <M extends BaseMap> void save(M map) {
        if (this.getClazz().isInstance(map)) {
            JsonElement json = encode(((I) map).getData());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(json);
            try (FileWriter writer = new FileWriter(this.getFile())) {
                writer.write(jsonStr);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public JsonElement encode(T data){
        return this.encodeToJson(data);
    }

    private JsonElement getJson(File file) {
        try (FileReader fileReader = new FileReader(file)) {
            return new Gson().fromJson(fileReader, JsonElement.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    protected T decodeFromJson(JsonElement json) {
        return this.getCodec().decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

    protected JsonElement encodeToJson(T data) {
        return this.getCodec().encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

}
