package com.tacz.guns.util;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class GunSmithTableBlockIds {
    private static final Identifier PHYSICAL_WORKBENCH_A_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "workbench_a");
    private static final Identifier PHYSICAL_WORKBENCH_B_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "workbench_b");
    private static final Identifier PHYSICAL_WORKBENCH_C_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "workbench_c");
    private static final Identifier LOGICAL_AMMO_WORKBENCH_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammo_workbench");
    private static final Identifier LOGICAL_ATTACHMENT_WORKBENCH_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "attachment_workbench");

    private GunSmithTableBlockIds() {
    }

    @Nullable
    public static Identifier normalize(@Nullable Identifier blockId) {
        if (blockId == null || DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
            return blockId;
        }
        if (PHYSICAL_WORKBENCH_A_ID.equals(blockId)) {
            return LOGICAL_AMMO_WORKBENCH_ID;
        }
        if (PHYSICAL_WORKBENCH_B_ID.equals(blockId)) {
            return DefaultAssets.DEFAULT_BLOCK_ID;
        }
        if (PHYSICAL_WORKBENCH_C_ID.equals(blockId)) {
            return LOGICAL_ATTACHMENT_WORKBENCH_ID;
        }
        return blockId;
    }
}
