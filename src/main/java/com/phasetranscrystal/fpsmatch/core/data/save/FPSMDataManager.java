package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/**
 * FPSMatch 数据管理器，用于管理和操作游戏数据的保存与加载。
 * <p>
 * 该类提供了一个全局实例，用于注册和管理不同类型的数据保存逻辑。
 * 支持全局数据和层级数据的存储，同时提供了数据的读取和写入功能。
 */
public class FPSMDataManager {
    /**
     * 数据注册表，用于存储已注册的数据类型及其对应的保存逻辑。
     */
    private final Map<Class<?>, Pair<String, ISavePort<?>>> REGISTRY = new HashMap<>();

    /**
     * 数据写入逻辑列表，用于在保存数据时调用。
     */
    private final ArrayList<Consumer<FPSMDataManager>> DATA = new ArrayList<>();

    /**
     * 当前层级的数据目录。
     */
    private File levelData;

    /**
     * 全局数据目录。
     */
    private final File globalData;

    /**
     * 构造函数，初始化数据管理器。
     * <p>
     * 该方法会注册全局数据目录，并触发 {@link RegisterFPSMSaveDataEvent} 事件，允许其他模块注册数据保存逻辑。
     */
    public FPSMDataManager(String levelName) {
        levelName = fixName(levelName);
        this.levelData = new File(new File(FMLLoader.getGamePath().toFile(), "fpsmatch"), levelName);
        this.globalData = this.getGlobalData();
        if (!globalData.exists()) {
            if (!globalData.mkdirs()) throw new RuntimeException("error : can't create " + globalData + " folder.");
        }
        this.readData();
        RegisterFPSMSaveDataEvent event = new RegisterFPSMSaveDataEvent(this);
        MinecraftForge.EVENT_BUS.post(event);
    }

    /**
     * 获取全局数据目录。
     * <p>
     * 该方法会根据配置文件获取全局数据目录的路径。
     * 如果配置文件不存在，则会创建默认的配置文件和数据目录。
     *
     * @return 全局数据目录
     */
    private File getGlobalData() {
        // 获取 Minecraft 游戏根目录
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

                // 解析 JSON 配置
                Map<String, String> config = new Gson().fromJson(
                        jsonContent,
                        new TypeToken<Map<String, String>>() {}.getType()
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

    public <T> void registerData(Class<T> clazz, String folderName, SaveHolder<T> saveHolder) {
        folderName = fixName(folderName);
        this.REGISTRY.put(clazz, Pair.of(folderName, saveHolder));
        this.DATA.add(saveHolder.writeHandler());
        File dataFolder = new File(saveHolder.isGlobal() ? globalData : levelData, folderName);
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) throw new RuntimeException("error : can't create " + dataFolder + " folder.");
        }
    }

    /**
     * 获取数据保存目录。
     * @param savedData 数据对象
     * */
    @Nullable
    public <T> File getSaveFolder(T savedData) {
        Pair<String, ISavePort<?>> pair = REGISTRY.getOrDefault(savedData.getClass(), null);
        if (pair == null) {
            FPSMatch.LOGGER.error("error : " + savedData.getClass().getName() + " data is not registered.");
            return null;
        }
        return new File(pair.getSecond().isGlobal() ? globalData : levelData, pair.getFirst());
    }

    /**
     * 保存单个数据对象
     * @param data 待保存的数据对象
     * @param fileName 文件名
     * @param overwrite 是否覆盖
     */
    public <T> void saveData(T data, String fileName, boolean overwrite) {
        fileName = fixName(fileName);
        Pair<String, ISavePort<?>> pair = REGISTRY.getOrDefault(data.getClass(), null);
        if (pair == null) throw new RuntimeException("error : " + data.getClass().getName() + " data is not registered.");
        @SuppressWarnings("unchecked")
        ISavePort<T> iSavedData = (ISavePort<T>) pair.getSecond();
        iSavedData.getWriter(data, fileName, overwrite).accept(new File(iSavedData.isGlobal() ? globalData : levelData, pair.getFirst()));
    }


    public <T> void saveData(T data, String fileName) {
        saveData(data, fileName, false);
    }

    /**
     * 读取特定文件名的数据。
     *
     * @param clazz 数据类型
     * @param fileName 文件名（不包含扩展名）
     * @return 读取到的数据，如果文件不存在或读取失败则返回null
     */
    @Nullable
    public <T extends ISavePort<?>> T readSpecificData(Class<T> clazz, String fileName) {
        fileName = fixName(fileName);
        Pair<String, ISavePort<?>> pair = REGISTRY.getOrDefault(clazz, null);
        if (pair == null) {
            throw new RuntimeException("error : " + clazz.getName() + " data is not registered.");
        }

        @SuppressWarnings("unchecked")
        ISavePort<T> iSavedData = (ISavePort<T>) pair.getSecond();
        File dataFolder = new File(iSavedData.isGlobal() ? globalData : levelData, pair.getFirst());
        return iSavedData.readSpecificFile(dataFolder, fileName);
    }

    /**
     * 创建新的数据文件并写入初始数据。
     *
     * @param clazz 数据类型
     * @param fileName 文件名（不包含扩展名）
     * @param initialData 初始数据
     * @return 是否创建成功
     */
    public <T> boolean createNewDataFile(Class<T> clazz, String fileName, T initialData) {
        fileName = fixName(fileName);
        Pair<String, ISavePort<?>> pair = REGISTRY.getOrDefault(clazz, null);
        if (pair == null) {
            throw new RuntimeException("error : " + clazz.getName() + " data is not registered.");
        }

        @SuppressWarnings("unchecked")
        ISavePort<T> iSavedData = (ISavePort<T>) pair.getSecond();
        File dataFolder = new File(iSavedData.isGlobal() ? globalData : levelData, pair.getFirst());
        return iSavedData.createNewDataFile(dataFolder, fileName, initialData);
    }


    /**
     * 保存所有注册的数据。
     * <p>
     * 该方法会调用所有注册的数据写入逻辑，将数据保存到对应的文件中。
     */
    public void saveData() {
        if (checkOrCreateFile(levelData) && checkOrCreateFile(globalData)) {
            this.DATA.forEach(consumer -> consumer.accept(this));
        }
    }

    /**
     * 读取所有注册的数据。
     * <p>
     * 该方法会遍历所有注册的数据目录，读取文件内容并调用对应的读取逻辑。
     */
    public void readData() {
        this.REGISTRY.values().forEach(pair -> pair.getSecond().getReader()
                .accept(new File(pair.getSecond().isGlobal() ? this.globalData : this.levelData, pair.getFirst())));
    }

    /**
     * 检查文件或目录是否存在，如果不存在则创建。
     *
     * @param file 文件或目录
     * @return 如果文件或目录存在或创建成功，返回 true；否则返回 false
     */
    public static boolean checkOrCreateFile(File file){
        if (!file.exists()) {
            return file.mkdirs();
        }
        return true;
    }

    /**
     * 修复文件名，移除其中的非法字符。
     *
     * @param fileName 文件名
     * @return 修复后的文件名
     */
    public static String fixName(String fileName) {
        // 定义不能包含的特殊字符
        String[] specialChars = new String[]{"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};
        // 遍历特殊字符数组
        for (String charToReplace : specialChars) {
            // 替换特殊字符为空字符串
            fileName = fileName.replace(charToReplace, "");
        }
        // 返回处理后的文件名
        return fileName;
    }
}