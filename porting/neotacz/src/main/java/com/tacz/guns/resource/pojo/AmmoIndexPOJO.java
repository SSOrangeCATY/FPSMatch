package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;

public class AmmoIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private Identifier display;

    @SerializedName("stack_size")
    private int stackSize;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    @SerializedName("sort")
    private int sort;

    public String getName() {
        return name;
    }

    public Identifier getDisplay() {
        return display;
    }

    public int getStackSize() {
        return stackSize;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    public int getSort() {
        return sort;
    }
}
