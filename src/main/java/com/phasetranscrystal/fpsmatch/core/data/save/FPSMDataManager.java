package com.phasetranscrystal.fpsmatch.core.data.save;

import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;

public class FPSMDataManager {
    public final File fpsmDir;
    public final File levelData;

    public FPSMDataManager(String levelName) {
        this.fpsmDir = new File(FMLLoader.getGamePath().toFile(), "fpsmatch");
        this.levelData = new File(fpsmDir, levelName);
    }


}
