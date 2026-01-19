package com.phasetranscrystal.fpsmatch.compat.impl;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

public class FPSMImpl {
    public static final String LRTACTICAL = "lrtactical";
    public static final String COUNTER_STRIKE_GRENADES = "csgrenades";
    public static final String MOHIST = "mohist";
    public static final String CLOTH_CONFIG = "cloth_config";
    public static final String TACZ = "tacz";
    public static final String TACZ_TWEAKS = "tacztweaks";

    public static boolean findLrtacticalMod(){
       return ModList.get().isLoaded(LRTACTICAL);
    }

    public static boolean findCounterStrikeGrenadesMod(){
        return ModList.get().isLoaded(COUNTER_STRIKE_GRENADES);
    }

    public static boolean findMohist(){
        return ModList.get().isLoaded(MOHIST);
    }

    public static boolean findClothConfig(){
        return ModList.get().isLoaded(CLOTH_CONFIG);
    }

    public static boolean findTaczTweaks(){
        return ModList.get().isLoaded(TACZ_TWEAKS);
    }

    public static boolean withVersion(String modId, String version) {
        IModFileInfo info = ModList.get().getModFileById(modId);
        try{
            return info != null && VersionRange.createFromVersionSpec(version).containsVersion(info.getMods().get(0).getVersion());
        }catch (InvalidVersionSpecificationException exception){
            return false;
        }
    }
}
