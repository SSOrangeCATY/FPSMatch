package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;

public class GunIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    @SerializedName("display")
    private Identifier display;

    @SerializedName("data")
    private Identifier data;

    @SerializedName("type")
    private String type;

    @SerializedName("item_type")
    private String itemType = "modern_kinetic";

    @SerializedName("sort")
    private int sort;

    public String getName() {
        return name;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    public Identifier getDisplay() {
        return display;
    }

    public Identifier getData() {
        return data;
    }

    public String getType() {
        return type;
    }

    public String getItemType() {
        return itemType;
    }

    public int getSort() {
        return sort;
    }
}
