package com.phasetranscrystal.fpsmatch.core.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Path CONFIG_DIR = Paths.get(FMLLoader.getGamePath().toString(), "fpsmatch");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new Gson();
    private static Map<String, String> config = new HashMap<>();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            PersistenceUtils.ensureDirectoryExists(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                String content = Files.readString(CONFIG_FILE);
                config = GSON.fromJson(content, new TypeToken<Map<String, String>>() {}.getType());
            } else {
                // 默认配置
                config.put("globalDataPath", CONFIG_DIR.resolve("global").toString());
                saveConfig();
            }
        } catch (Exception e) {
            throw new DataPersistenceException("Failed to load config", e);
        }
    }

    private static void saveConfig() {
        try {
            String content = GSON.toJson(config);
            Files.writeString(CONFIG_FILE, content);
        } catch (Exception e) {
            throw new DataPersistenceException("Failed to save config", e);
        }
    }

    public static Path getGlobalDataPath() {
        String pathStr = config.get("globalDataPath");
        if (pathStr == null) {
            throw new DataPersistenceException("globalDataPath not found in config");
        }
        return Paths.get(pathStr);
    }
}