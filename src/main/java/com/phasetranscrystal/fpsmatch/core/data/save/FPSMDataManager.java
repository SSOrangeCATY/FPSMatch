package com.phasetranscrystal.fpsmatch.core.data.save;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.IMap;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FPSMDataManager {
    private final Map<Class<ISavedData<?>>, DataProvider<?, ISavedData<?>>> REGISTRY = new HashMap<>();
    public final File levelData;

    public FPSMDataManager(String levelName) {
        this.levelData = new File(new File(FMLLoader.getGamePath().toFile(), "fpsmatch"), levelName);
    }

    /**
     * 注册一个数据提供者。
     *
     * @param clazz      数据提供者的类类型。
     * @param dataProvider 数据提供者实例。
     */
    public void registerData(Class<ISavedData<?>> clazz, DataProvider<?, ISavedData<?>> dataProvider){
        this.REGISTRY.put(clazz, dataProvider);
    }

    /**
     * 根据类类型获取对应的数据提供者。
     *
     * @param clazz 数据提供者的类类型。
     * @return 对应的数据提供者，如果不存在则返回null。
     */
    @Nullable
    public DataProvider<?, ISavedData<?>> getDataProvider(Class<ISavedData<?>> clazz){
        return REGISTRY.getOrDefault(clazz, null);
    }

    public <M extends IMap<?>> void processDataProvider(M map){
        List<DataProvider<?,?>> dataProviderList = new ArrayList<>();
        this.REGISTRY.forEach((clazz,provider)->{
            if(clazz.isInstance(map)){
                dataProviderList.add(provider);
            }
        });
        dataProviderList.forEach((dataProvider -> {
            //TODO
        }));
    }
}