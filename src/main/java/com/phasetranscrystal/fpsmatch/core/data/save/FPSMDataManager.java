package com.phasetranscrystal.fpsmatch.core.data.save;

import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public class FPSMDataManager {
    private final Map<Class<? extends ISavedData<?>>,Pair<String,ISavedData<?>>> REGISTRY = new HashMap<>();
    private final ArrayList<Consumer<FPSMDataManager>> DATA = new ArrayList<>();
    private File levelData;
    private static FPSMDataManager INSTANCE;
    public static FPSMDataManager getInstance(){
        if(INSTANCE == null){
            INSTANCE = new FPSMDataManager();
        }
        return INSTANCE;
    }

    public FPSMDataManager() {
        RegisterFPSMSaveDataEvent event = new RegisterFPSMSaveDataEvent(this);
        MinecraftForge.EVENT_BUS.post(event);
    }

    public void setLevelData(String levelName) {
        levelName = fixName(levelName);
        this.levelData = new File(new File(FMLLoader.getGamePath().toFile(), "fpsmatch"), levelName);
        this.REGISTRY.values().forEach(pair -> pair.getSecond().getReader().accept(new File(levelData,pair.getFirst())));
    }


    public <T extends ISavedData<T>> void registerData(Class<T> clazz, String folderName, SaveHolder<T> iSavedData) {
        folderName = fixName(folderName);
        this.REGISTRY.put(clazz,Pair.of(folderName,iSavedData));
        this.DATA.add(iSavedData.writeHandler());
        File mapData = new File(levelData,folderName);
        if(!mapData.exists()){
            if(!mapData.mkdirs()) throw new RuntimeException("error : can't create "+folderName+" data folder.");
        }
    }

    public <T extends ISavedData<?>> void saveData(T data, String fileName) {
        fileName = fixName(fileName);
        Pair<String,ISavedData<?>> pair = REGISTRY.getOrDefault(data.getClass(),null);
        if(pair == null) throw new RuntimeException("error : "+data.getClass().getName()+" data is not registered.");
        ISavedData<T> iSavedData = (ISavedData<T>) pair.getSecond();
        iSavedData.getWriter(data,fileName).accept(new File(levelData,pair.getFirst()));
    }

    public void saveData(){
        if(checkOrCreateFile(levelData)) {
            this.DATA.forEach(consumer -> consumer.accept(this));
        }
    }


    public static boolean checkOrCreateFile(File file){
        if (!file.exists()) {
            return file.mkdirs();
        }
        return true;
    }

    public static String fixName(String fileName) {
        // 定义不能包含的特殊字符
        String[] specialChars = new String[]{"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};
        // 遍历特殊字符数组
        for (String charToReplace : specialChars) {
            // 替换特殊字符为空字符串
            fileName = fileName.replace(charToReplace, "");
        }
        // 打印或返回处理后的文件名
        return fileName;
    }
}