package dev.coloredlight.client;

import dev.coloredlight.config.ColoredLightConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public final class ColoredLightClient {
    public static final String MOD_ID = "coloredlight";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static ColoredLightConfig config = new ColoredLightConfig();
    private static Path configPath;
    private static boolean failed;

    private ColoredLightClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(ColoredLightConfig.FILE_NAME);
        config = ColoredLightConfig.load(configPath);
    }

    public static ColoredLightConfig config() {
        return config;
    }

    public static Path configPath() {
        return configPath;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }

    public static boolean active() {
        return !failed && config.enabled();
    }

    public static void fail(Throwable e) {
        if (!failed) {
            failed = true;
            LOGGER.error("Colored Light hit an error and turned itself off for this session", e);
        }
    }
}
