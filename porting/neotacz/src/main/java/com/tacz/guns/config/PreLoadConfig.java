package com.tacz.guns.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

public class PreLoadConfig {
    private static final Logger LOGGER = LogManager.getLogger("tacz");
    private static final String KEY = "gunpack.DefaultPackDebug";
    private static final boolean DEFAULT_VALUE = false;
    public static final PreloadBooleanValue override = new PreloadBooleanValue();

    private static boolean loaded;
    private static boolean value = DEFAULT_VALUE;
    private static Path configPath;

    public static synchronized void load(Path configBasePath) {
        Path resolvedConfigPath = configBasePath.resolve("tacz-pre.toml");
        if (loaded && resolvedConfigPath.equals(configPath)) return;
        try (CommentedFileConfig config = CommentedFileConfig.builder(resolvedConfigPath).sync().autosave().build()) {
            config.load();
            if (!config.contains(KEY)) {
                config.set(KEY, DEFAULT_VALUE);
                config.save();
            }
            value = config.getOrElse(KEY, DEFAULT_VALUE);
            configPath = resolvedConfigPath;
            loaded = true;
        }
    }

    private static synchronized boolean getOverride() {
        return value;
    }

    private static synchronized void setOverride(boolean newValue) {
        value = newValue;
        if (configPath == null) return;
        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().autosave().build()) {
            config.load();
            config.set(KEY, newValue);
            config.save();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to save pre-load config {}", configPath, e);
        }
    }

    public static final class PreloadBooleanValue implements BooleanSupplier {
        private PreloadBooleanValue() {
        }

        public Boolean get() {
            return getOverride();
        }

        @Override
        public boolean getAsBoolean() {
            return getOverride();
        }

        public void set(boolean value) {
            setOverride(value);
        }

        public void set(Boolean value) {
            setOverride(Boolean.TRUE.equals(value));
        }
    }
}
