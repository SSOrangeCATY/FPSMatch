package com.tacz.guns.compat.carryon;

import com.tacz.guns.GunMod;
import net.neoforged.fml.InterModComms;
import net.minecraft.core.registries.BuiltInRegistries;

public class BlackList {
    private static final String CARRY_ON_ID = "carryon";

    public static void addBlackList() {
        BuiltInRegistries.BLOCK.keySet().stream().filter(id -> id.getNamespace().equals(GunMod.MOD_ID))
                .forEach(id -> InterModComms.sendTo(CARRY_ON_ID, "blacklistBlock", id::toString));
    }
}
