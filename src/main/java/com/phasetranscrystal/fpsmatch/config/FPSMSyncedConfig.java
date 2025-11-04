package com.phasetranscrystal.fpsmatch.config;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.PackedSetting;
import com.phasetranscrystal.fpsmatch.core.data.Setting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FPSMSyncedConfig {
    private static final Map<String, Setting<?>> REGISTRY = new HashMap<>();

    public static final Setting<Boolean> lock3PersonCamera = register(Setting.ofBoolean("lock3PersonCamera", false));

    public static final Setting<Boolean> blockSpecKeyInteraction = register(Setting.ofBoolean("blockSpecKeyInteraction", false));

    /**
     * 注册配置项
     */
    public static <T> Setting<T> register(Setting<T> setting) {
        REGISTRY.put(setting.getConfigName(), setting);
        return setting;
    }

    /**
     * 根据名称获取配置项
     */
    public static Optional<Setting<?>> getSetting(String name) {
        return Optional.ofNullable(REGISTRY.get(name));
    }

    /**
     * 获取所有已注册的配置项
     */
    public static Map<String, Setting<?>> getAllSettings() {
        return new HashMap<>(REGISTRY);
    }


    /**
     * 批量更新配置值
     */
    public static void updateSettings(Map<String, PackedSetting<?>> updates) {
        updates.forEach((name, packedSetting) -> {
            Setting<?> setting = REGISTRY.get(name);
            if (setting != null) {
                try {
                    updateSettingValue(setting, packedSetting.value());
                } catch (Exception e) {
                    FPSMatch.LOGGER.error("Failed to update setting {}: {}", name, e.getMessage());
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void updateSettingValue(Setting<T> setting, Object value) {
        setting.set((T) value);
    }
}
