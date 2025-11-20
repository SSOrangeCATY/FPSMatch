package com.phasetranscrystal.fpsmatch.core.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class FPSMDataManager {
    private final Map<Class<?>, DataEntry<?>> registry = new HashMap<>();
    private final Path levelDataPath;
    private final Path globalDataPath;

    // 数据条目：存储文件夹名和SaveHolder
    private static class DataEntry<T> {
        String folderName;
        SaveHolder<T> holder;
        DataEntry(String folderName, SaveHolder<T> holder) { this.folderName = folderName; this.holder = holder; }
    }

    public FPSMDataManager(String levelName) {
        String fixedLevelName = PersistenceUtils.fixFileName(levelName);
        this.levelDataPath = Paths.get(FMLLoader.getGamePath().toString(), "fpsmatch", fixedLevelName);
        this.globalDataPath = ConfigManager.getGlobalDataPath();
        PersistenceUtils.ensureDirectoryExists(levelDataPath);
        PersistenceUtils.ensureDirectoryExists(globalDataPath);

        MinecraftForge.EVENT_BUS.post(new RegisterFPSMSaveDataEvent(this));
    }

    // 注册数据类型
    public <T> void registerData(Class<T> clazz, String folderName, SaveHolder<T> holder) {
        String fixedFolderName = PersistenceUtils.fixFileName(folderName);
        registry.put(clazz, new DataEntry<>(fixedFolderName, holder));
    }

    // 同步保存数据
    @SuppressWarnings("unchecked")
    public <T> void saveData(T data, String fileName, boolean overwrite) {
        DataEntry<T> entry = getEntry((Class<T>)data.getClass());
        Path dirPath = entry.holder.isGlobal() ? globalDataPath : levelDataPath;
        entry.holder.getWriter(data, fileName, overwrite).accept(dirPath.toFile());
    }

    // 异步保存数据
    public <T> CompletableFuture<Void> saveDataAsync(T data, String fileName, boolean overwrite) {
        return CompletableFuture.runAsync(() -> saveData(data, fileName, overwrite), Executors.newSingleThreadExecutor());
    }

    // 同步读取数据
    public <T> T readSpecificData(Class<T> clazz, String fileName) {
        DataEntry<T> entry = getEntry(clazz);
        Path dirPath = entry.holder.isGlobal() ? globalDataPath : levelDataPath;
        Path filePath = dirPath.resolve(PersistenceUtils.fixFileName(fileName) + "." + entry.holder.getFileType());
        try {
            String content = Files.readString(filePath);
            JsonElement element = new Gson().fromJson(content, JsonElement.class);
            return entry.holder.decodeFromJson(element);
        } catch (Exception e) {
            throw new DataPersistenceException("Failed to read data: " + clazz.getName(), e);
        }
    }

    // 异步读取数据
    public <T> CompletableFuture<T> readSpecificDataAsync(Class<T> clazz, String fileName) {
        return CompletableFuture.supplyAsync(() -> readSpecificData(clazz, fileName), Executors.newSingleThreadExecutor());
    }

    // 获取数据条目
    @SuppressWarnings("unchecked")
    private <T> DataEntry<T> getEntry(Class<T> clazz) {
        DataEntry<?> entry = registry.get(clazz);
        if (entry == null) {
            throw new DataPersistenceException("Data type not registered: " + clazz.getName());
        }
        return (DataEntry<T>) entry;
    }

    public void readAllData() {
        registry.values().forEach(entry -> {
            Path dirPath = entry.holder.isGlobal() ? globalDataPath : levelDataPath;
            entry.holder.getReader().accept(dirPath.toFile());
        });
    }

    public void saveAllData() {
        registry.values().forEach(entry -> {
            entry.holder.writeHandler().accept(this);
        });
    }

    public <T> File getSaveFolder(T savedData) {
        DataEntry<?> entry = getEntry(savedData.getClass());
        return new File(entry.holder.isGlobal() ? globalDataPath.toString() : levelDataPath.toString(), entry.folderName);
    }
}