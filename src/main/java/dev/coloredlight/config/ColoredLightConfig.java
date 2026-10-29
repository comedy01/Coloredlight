package dev.coloredlight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ColoredLightConfig {
    public static final String FILE_NAME = "coloredlight.json";

    private static final Logger LOGGER = LogManager.getLogger("coloredlight");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MIN_INTENSITY = 0;
    public static final int MAX_INTENSITY = 100;

    private boolean enabled = true;
    private int intensity = MAX_INTENSITY;
    private boolean stainedGlass = true;
    private int glassStrength = DEFAULT_GLASS_STRENGTH;

    public static final int MIN_GLASS_STRENGTH = 0;
    public static final int MAX_GLASS_STRENGTH = 300;
    public static final int DEFAULT_GLASS_STRENGTH = 100;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public float intensity() {
        return intensityPercent() / 100f;
    }

    public int intensityPercent() {
        return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
    }

    public void setIntensityPercent(int value) {
        intensity = value;
    }

    public boolean stainedGlass() {
        return stainedGlass;
    }

    public void setStainedGlass(boolean value) {
        stainedGlass = value;
    }

    public int glassStrengthPercent() {
        return Math.max(MIN_GLASS_STRENGTH, Math.min(MAX_GLASS_STRENGTH, glassStrength));
    }

    public void setGlassStrengthPercent(int value) {
        glassStrength = value;
    }

    public void resetToDefaults() {
        ColoredLightConfig fresh = new ColoredLightConfig();
        enabled = fresh.enabled;
        intensity = fresh.intensity;
        stainedGlass = fresh.stainedGlass;
        glassStrength = fresh.glassStrength;
    }

    public static ColoredLightConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            ColoredLightConfig fresh = new ColoredLightConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ColoredLightConfig loaded = GSON.fromJson(reader, ColoredLightConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            ColoredLightConfig fresh = new ColoredLightConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.write(temp, (GSON.toJson(this) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
