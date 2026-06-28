package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;

public class BlockIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private Identifier display;

    @SerializedName("data")
    private Identifier data;

    @SerializedName("id")
    private Identifier id = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table");

    @SerializedName("stack_size")
    private int stackSize;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    public String getName() {
        return name;
    }

    public Identifier getId() {
        return id;
    }

    public Identifier getDisplay() {
        return display;
    }

    public int getStackSize() {
        return stackSize;
    }

    public Identifier getData() {
        return data;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }
}
