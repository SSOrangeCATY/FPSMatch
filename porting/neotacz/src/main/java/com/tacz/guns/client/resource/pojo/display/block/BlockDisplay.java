package com.tacz.guns.client.resource.pojo.display.block;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.resources.Identifier;

public class BlockDisplay implements IDisplay {
    @SerializedName("model")
    private Identifier modelLocation;
    @SerializedName("texture")
    private Identifier modelTexture;
    @SerializedName("transforms")
    private ItemTransforms transforms;

    public Identifier getModelLocation() {
        return modelLocation;
    }

    public Identifier getModelTexture() {
        return modelTexture;
    }

    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public void init() {
        if (modelTexture != null) {
            modelTexture = converter.idToFile(modelTexture);
        }
    }
}
