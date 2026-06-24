package com.phasetranscrystal.fpsmatch.compat.impl;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

public class FPSMImpl {
    public static final String TACZ = "tacz";

    public static boolean findTacz(){
        return isLoaded(TACZ);
    }

    private static boolean isLoaded(String modId) {
        return getModFileInfo(modId) != null;
    }

    private static IModFileInfo getModFileInfo(String modId) {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModFileById(modId);
        }

        FMLLoader loader = FMLLoader.getCurrentOrNull();
        LoadingModList loadingModList = loader == null ? null : loader.getLoadingModList();
        return loadingModList == null ? null : loadingModList.getModFileById(modId);
    }
}
