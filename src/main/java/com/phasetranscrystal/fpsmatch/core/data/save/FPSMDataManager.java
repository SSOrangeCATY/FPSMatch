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
    private final Map<Class<? extends ISavedData<?>>, Pair<String, ISavedData<?>>> REGISTRY = new HashMap<>();

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
     * 数据管理器的全局实例。
     */
    private static FPSMDataManager INSTANCE;

    /**
     * 获取 FPSMDataManager 的全局实例。
     * <p>
     * 如果实例尚未初始化，则会自动创建一个新实例。
     *
     * @return FPSMDataManager 的全局实例
     */
    public static FPSMDataManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FPSMDataManager();
        }
        return INSTANCE;
    }

    /**
     * 构造函数，初始化数据管理器。
     * <p>
     * 该方法会注册全局数据目录，并触发 {@link RegisterFPSMSaveDataEvent} 事件，允许其他模块注册数据保存逻辑。
     */
    public FPSMDataManager() {
        this.globalData = this.getGlobalData();
        RegisterFPSMSaveDataEvent event = new RegisterFPSMSaveDataEvent(this);
        MinecraftForge.EVENT_BUS.post(event);
    }

    /**
     * 设置当前层级的数据目录。
     * <p>
     * 如果层级数据目录不存在，则会尝试创建该目录。
     *
     * @param levelName 当前层级的名称
     */
    public void setLevelData(String levelName) {
        levelName = fixName(levelName);
        this.levelData = new File(new File(FMLLoader.getGamePath().toFile(), "fpsmatch"), levelName);
        if (!globalData.exists()) {
            if (!globalData.mkdirs()) throw new RuntimeException("error : can't create " + globalData + " folder.");
        }
        this.readData();
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

    /**
     * 注册数据保存逻辑。
     * <p>
     * 该方法将数据类型与其保存逻辑关联起来，并创建相应的数据目录。
     *
     * @param clazz 数据类的类型
     * @param folderName 数据目录的名称
     * @param iSavedData 数据保存逻辑
     * @param <T> 数据类的泛型类型
     */
    public <T extends ISavedData<T>> void registerData(Class<T> clazz, String folderName, SaveHolder<T> iSavedData) {
        folderName = fixName(folderName);
        this.REGISTRY.put(clazz, Pair.of(folderName, iSavedData));
        this.DATA.add(iSavedData.writeHandler());
        File mapData = new File(iSavedData.isGlobal() ? globalData : levelData, folderName);
        if (!mapData.exists()) {
            if (!mapData.mkdirs()) throw new RuntimeException("error : can't create " + mapData + " folder.");
        }
    }

    /**
     * 保存单个数据对象。
     * <p>
     * 该方法会根据数据类型找到对应的保存逻辑，并将数据写入文件。
     *
     * @param data 待保存的数据对象
     * @param fileName 文件名（不包含扩展名）
     * @param <T> 数据类的泛型类型
     */
    public <T extends ISavedData<?>> void saveData(T data, String fileName) {
        fileName = fixName(fileName);
        Pair<String, ISavedData<?>> pair = REGISTRY.getOrDefault(data.getClass(), null);
        if (pair == null) throw new RuntimeException("error : " + data.getClass().getName() + " data is not registered.");
        ISavedData<T> iSavedData = (ISavedData<T>) pair.getSecond();
        iSavedData.getWriter(data, fileName).accept(new File(iSavedData.isGlobal() ? globalData : levelData, pair.getFirst()));
    }

    /**
     * 保存所有注册的数据。
     * <p>
     * 该方法会调用所有注册的数据写入逻辑，将数据保存到对应的文件中。
     */
    public void saveData() {
        if (checkOrCreateFile(levelData)) {
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
    public static boolean checkOrCreateFile(File file) {
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