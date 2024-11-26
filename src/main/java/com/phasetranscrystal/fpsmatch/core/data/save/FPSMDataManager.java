package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.codec.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.map.IMap;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FPSMDataManager {
    private final Map<String, ISavedData<?>> REGISTRY = new HashMap<>();
    public final File levelData;

    public FPSMDataManager(String levelName) {
        this.levelData = new File(new File(FMLLoader.getGamePath().toFile(), "fpsmatch"), levelName);
    }

    public void registerData(String name,ISavedData<?> dataProvider){
        this.REGISTRY.put(name, dataProvider);
    }

}