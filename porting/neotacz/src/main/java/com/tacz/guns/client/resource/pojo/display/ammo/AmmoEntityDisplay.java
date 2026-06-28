package com.tacz.guns.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public class AmmoEntityDisplay {
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
