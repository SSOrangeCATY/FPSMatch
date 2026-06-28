package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public class MuzzleFlash {
    @SerializedName("texture")
    protected Identifier texture = null;

    @SerializedName("scale")
    private float scale = 1;

    public Identifier getTexture() {
        return texture;
    }

    public float getScale() {
        return scale;
    }
}
