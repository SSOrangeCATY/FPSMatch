package com.phasetranscrystal.fpsmatch.impl;

import net.minecraftforge.fml.ModList;

public class FPSMImpl {
    public static boolean findEquipmentMod(){
       return ModList.get().isLoaded("lrtactical");
    }

    public static boolean findCounterStrikeGrenadesMod(){
        return ModList.get().isLoaded("csgrenades");
    }

    public static boolean findMohist(){
        return ModList.get().isLoaded("mohist");
    }
}
