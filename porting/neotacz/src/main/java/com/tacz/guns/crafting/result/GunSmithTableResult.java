package com.tacz.guns.crafting.result;

import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableResult {
    public static final String GUN = "gun";
    public static final String AMMO = "ammo";
    public static final String ATTACHMENT = "attachment";
    public static final String CUSTOM = "custom";

    private ItemStack result = ItemStack.EMPTY;
    private Identifier group = null;

    @Nullable
    private RawGunTableResult raw = null;

    public GunSmithTableResult(ItemStack result, @Nullable Identifier group) {
        this.result = result;
        this.group = group==null ? TabConfig.TAB_EMPTY : group;
    }


    public GunSmithTableResult(@NotNull RawGunTableResult raw) {
        this.raw = raw;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw, @Nullable Identifier group) {
        this.raw = raw;
        this.group = group==null ? TabConfig.TAB_EMPTY : group;
    }

    public void init() {
        if (raw != null) {
            GunSmithTableResult result = RawGunTableResult.init(raw);
            if (result.getResult().isEmpty()) {
                // Common indexes can arrive after recipes on the client; keep raw so init can be retried.
                return;
            }
            this.result = result.getResult();
            if (group == null || group.equals(TabConfig.TAB_EMPTY)) {
                this.group = result.getGroup();
            }
            this.raw = null;
        }
    }

    public ItemStack getResult() {
        return result;
    }

    public Identifier getGroup() {
        return group;
    }
}
