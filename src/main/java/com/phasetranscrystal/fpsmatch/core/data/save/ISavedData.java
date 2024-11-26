package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

public interface ISavedData<T> {
    String getName();
    Codec<T> getCodec();

    T getData();

    void readData(T data);

    default T decodeFromJson(JsonElement json) {
        return this.getCodec().decode(JsonOps.INSTANCE, json).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
    }

    default JsonElement encodeToJson(T data) {
        return this.getCodec().encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, e -> { throw new RuntimeException(e); });
    }

}
