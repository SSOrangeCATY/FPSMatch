package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public class GunLod {
    @SerializedName("model")
    private Identifier modelLocation;
    @SerializedName("texture")
    protected Identifier modelTexture;

    public Identifier getModelLocation() {
        return modelLocation;
    }

    public Identifier getModelTexture() {
        return modelTexture;
    }
}
