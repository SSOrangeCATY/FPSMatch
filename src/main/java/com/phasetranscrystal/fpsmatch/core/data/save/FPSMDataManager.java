package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.Config;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

public class FPSMDataManager {
    private final Map<Class<? extends ISavedData<?>>,Pair<String,ISavedData<?>>> REGISTRY = new HashMap<>();
    private final ArrayList<Consumer<FPSMDataManager>> DATA = new ArrayList<>();
    private File levelData;
    private final File globalData;
    private static FPSMDataManager INSTANCE;
    public static FPSMDataManager getInstance(){
        if(INSTANCE == null){
            INSTANCE = new FPSMDataManager();
        }
        return INSTANCE;
    }

    public FPSMDataManager() {
        this.globalData = this.getGlobalData();
        RegisterFPSMSaveDataEvent event = new RegisterFPSMSaveDataEvent(this);
        MinecraftForge.EVENT_BUS.post(event);
    }

    public void setLevelData(String levelName) {
        levelName = fixName(levelName);
        this.levelData = new File(new File(FMLLoader.getGamePath().toFile(), "fpsmatch"), levelName);
        if(!globalData.exists()){
            if(!globalData.mkdirs()) throw new RuntimeException("error : can't create "+ globalData +" folder.");
        }
        this.readData();
    }

    private File getGlobalData() {
        // 获取Minecraft游戏根目录
        File gameDir = FMLLoader.getGamePath().toFile();

        // 配置目录和文件路径
        File configDir = new File(gameDir, "fpsmatch");
        File configFile = new File(configDir, "config.json");
        File dataFile;

        try {
            // 如果配置文件不存在则初始化
            if (!configFile.exists()) {
                // 创建配置目录（如果不存在）
                if (!configDir.exists() && !configDir.mkdirs()) {
                    throw new RuntimeException("couldn't create file : " + configDir);
                }

                File defaultDataFile = new File(configDir, "global");

                // 创建默认配置内容
                Map<String, String> config = new HashMap<>();
                config.put("globalDataPath", defaultDataFile.getCanonicalPath());

                // 写入配置文件
                Files.write(configFile.toPath(), new Gson().toJson(config).getBytes());
                dataFile = defaultDataFile;
            }
            // 配置文件已存在则读取
            else {
                // 读取配置文件内容
                String jsonContent = new String(Files.readAllBytes(configFile.toPath()));

                // 解析JSON配置
                Map<String, String> config = new Gson().fromJson(
                        jsonContent,
                        new TypeToken<Map<String, String>>(){}.getType()
                );

                // 获取数据文件路径
                String dataPath = config.get("globalDataPath");
                if (dataPath == null || dataPath.isEmpty()) {
                    throw new RuntimeException("config file is invalid: globalDataPath is missing");
                }
                dataFile = new File(dataPath);
            }
        } catch (Exception e) {
            // 异常处理：打印错误并返回安全默认值
            e.printStackTrace();
            dataFile = new File(configDir, "global");
        }
        return dataFile;
    }

    public <T extends ISavedData<T>> void registerData(Class<T> clazz, String folderName, SaveHolder<T> iSavedData) {
        folderName = fixName(folderName);
        this.REGISTRY.put(clazz,Pair.of(folderName,iSavedData));
        this.DATA.add(iSavedData.writeHandler());
        File mapData = new File(iSavedData.isGlobal() ? globalData : levelData, folderName);
        if(!mapData.exists()){
            if(!mapData.mkdirs()) throw new RuntimeException("error : can't create "+ mapData +" folder.");
        }
    }

    public <T extends ISavedData<?>> void saveData(T data, String fileName) {
        fileName = fixName(fileName);
        Pair<String,ISavedData<?>> pair = REGISTRY.getOrDefault(data.getClass(),null);
        if(pair == null) throw new RuntimeException("error : "+data.getClass().getName()+" data is not registered.");
        ISavedData<T> iSavedData = (ISavedData<T>) pair.getSecond();
        iSavedData.getWriter(data,fileName).accept(new File(iSavedData.isGlobal() ? globalData : levelData, pair.getFirst()));
    }

    public void saveData(){
        if(checkOrCreateFile(levelData)) {
            this.DATA.forEach(consumer -> consumer.accept(this));
        }
    }

    public void readData(){
        this.REGISTRY.values().forEach(pair -> pair.getSecond().getReader()
                .accept(new File(pair.getSecond().isGlobal() ? this.globalData : this.levelData, pair.getFirst())));
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