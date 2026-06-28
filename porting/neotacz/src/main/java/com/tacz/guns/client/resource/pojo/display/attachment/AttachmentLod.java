package com.tacz.guns.client.resource.pojo.display.attachment;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public class AttachmentLod {
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
