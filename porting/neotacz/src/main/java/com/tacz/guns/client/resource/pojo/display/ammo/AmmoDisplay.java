package com.tacz.guns.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;

public class AmmoDisplay implements IDisplay {
    @SerializedName("model")
    private Identifier modelLocation;

    @SerializedName("texture")
    private Identifier modelTexture;

    @Nullable
    @SerializedName("slot")
    private Identifier slotTextureLocation;

    @Nullable
    @SerializedName("entity")
    private AmmoEntityDisplay ammoEntity;

    @Nullable
    @SerializedName("shell")
    private ShellDisplay shellDisplay;

    @Nullable
    @SerializedName("particle")
    private AmmoParticle particle;

    @SerializedName("tracer_color")
    private String tracerColor = "0xFFFFFF";

    @Nullable
    @SerializedName("transform")
    private AmmoTransform transform;

    public Identifier getModelLocation() {
        return modelLocation;
    }

    public Identifier getModelTexture() {
        return modelTexture;
    }

    @Nullable
    public Identifier getSlotTextureLocation() {
        return slotTextureLocation;
    }

    @Nullable
    public AmmoEntityDisplay getAmmoEntity() {
        return ammoEntity;
    }

    @Nullable
    public ShellDisplay getShellDisplay() {
        return shellDisplay;
    }

    @Nullable
    public AmmoParticle getParticle() {
        return particle;
    }

    public String getTracerColor() {
        return tracerColor;
    }

    @Nullable
    public AmmoTransform getTransform() {
        return transform;
    }

    @Override
    public void init() {
        if (modelTexture != null) {
            modelTexture = converter.idToFile(modelTexture);
        }
        if (slotTextureLocation != null) {
            slotTextureLocation = converter.idToFile(slotTextureLocation);
        }
        if (ammoEntity != null && ammoEntity.modelTexture != null) {
            ammoEntity.modelTexture = converter.idToFile(ammoEntity.modelTexture);
        }
        if (shellDisplay != null&& shellDisplay.modelTexture != null) {
            shellDisplay.modelTexture = converter.idToFile(shellDisplay.modelTexture);
        }
    }
}
