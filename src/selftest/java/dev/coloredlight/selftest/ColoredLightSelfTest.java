package dev.coloredlight.selftest;

import com.mojang.blaze3d.platform.NativeImage;
import org.apache.logging.log4j.LogManager;
import dev.coloredlight.client.Backend;
import dev.coloredlight.client.Compat;
import dev.coloredlight.client.RawUniforms;
import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.LightFrame;
import dev.coloredlight.client.LightTracker;
import dev.coloredlight.client.ShaderPatches;
import dev.coloredlight.client.SodiumHook;
import dev.coloredlight.client.gui.ColoredLightSettingsScreen;
import dev.coloredlight.config.ColoredLightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class ColoredLightSelfTest {
    private static final String WORLD = "coloredlight-selftest";
    private static final int TIMEOUT = 2400;

    private static volatile int frames;
    public static volatile boolean hideHud;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private final List<Double> samples = new CopyOnWriteArrayList<>();
    private final List<Double> ratios = new CopyOnWriteArrayList<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;
    private double ratioOn;
    private double ratioOff;
    private boolean shaders;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                ServerCompat.hideChat(mc);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    private static volatile java.io.PrintWriter filmLog;
    private static int filmFrame;
    private static File filmDir;

    private static long lastFrameNanos;
    private static long worstFrameNanos;
    private static final long[] FRAME_TIMES = new long[1 << 17];
    private static int recordedFrames;

    public static void frame() {
        frames++;
        long tick = System.nanoTime();
        if (lastFrameNanos != 0) {
            worstFrameNanos = Math.max(worstFrameNanos, tick - lastFrameNanos);
            if (recordedFrames < FRAME_TIMES.length) {
                FRAME_TIMES[recordedFrames++] = tick - lastFrameNanos;
            }
        }
        lastFrameNanos = tick;
        java.io.PrintWriter log = filmLog;
        if (log != null) {
            int number = filmFrame++;
            long now = System.nanoTime();
            File file = new File(filmDir, String.format(Locale.ROOT, "f%05d.raw", number));
            log.println(number + " " + now);
            Captures.grab(Screens.renderTarget(Minecraft.getInstance()), downscale(Screens.renderTarget(Minecraft.getInstance())) >= 2 ? 2 : 1, image -> {
                try {
                    int[] pixels = Captures.abgr(image);
                    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(pixels.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    bytes.asIntBuffer().put(pixels);
                    if (number == 0) {
                        Files.writeString(filmDir.toPath().resolve("size.txt"), image.getWidth() + " " + image.getHeight());
                    }
                    Files.write(file.toPath(), bytes.array());
                } catch (IOException e) {
                    log("could not write film frame " + file + ": " + e);
                } finally {
                    image.close();
                }
            });
        }
    }

    private static void startFilm(Minecraft mc, String name) {
        try {
            filmDir = new File(mc.gameDirectory, "film/" + name);
            if (filmDir.exists()) {
                try (Stream<Path> paths = Files.walk(filmDir.toPath())) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
            Files.createDirectories(filmDir.toPath());
            filmFrame = 0;
            filmLog = new java.io.PrintWriter(Files.newBufferedWriter(filmDir.toPath().resolve("times.txt")));
            log("filming " + filmDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void stopFilm() {
        java.io.PrintWriter log = filmLog;
        filmLog = null;
        if (log != null) {
            log.close();
            log("filmed " + filmFrame + " frames");
        }
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        ColoredLightConfig config = ColoredLightClient.config();
        config.resetToDefaults();
        ColoredLightClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        ColoredLightConfig config = ColoredLightClient.config();
        int floor = ServerCompat.FLOOR;
        boolean sodium = sodiumLoaded();

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> {
            shaders = Compat.shaderPackInUse();
            log("world loaded, sodium " + sodium + ", shader pack " + shaders);
        });
        then(40, () -> {
            config.resetToDefaults();
            hideHud = true;
            run(mc, "gamerule doDaylightCycle false");
            run(mc, "gamerule advance_time false");
            run(mc, "time set 18000");
            run(mc, "weather clear");
            run(mc, "fill -7 " + (floor - 1) + " -7 7 " + (floor + 5) + " 7 minecraft:stone hollow");
            run(mc, "tp @p 0.5 " + floor + " 5.5 180 40");
        });
        if (Boolean.getBoolean("coloredlight.gallery")) {
            galleryScene(mc);
            return;
        }
        if (Boolean.getBoolean("coloredlight.glassfilm")) {
            glassFilmScene(mc);
            return;
        }
        if (Boolean.getBoolean("coloredlight.film")) {
            filmScene(mc);
            return;
        }
        if (Boolean.getBoolean("coloredlight.perf")) {
            perfScene(mc);
            return;
        }
        if (Boolean.getBoolean("coloredlight.stress")) {
            stressScene(mc);
            return;
        }
        then(20, () -> {
            if (shaders) {
                check(ShaderPatches.applied(ShaderPatches.IRIS_TERRAIN), "the shader pack's terrain shader was not patched");
            } else if (sodium) {
                check(ShaderPatches.applied(ShaderPatches.SODIUM_TERRAIN), "Sodium's terrain shader was not patched");
                check(ShaderPatches.applied(ShaderPatches.SODIUM_TERRAIN + "-raw") || ShaderPatches.applied(ShaderPatches.SODIUM_GLOBALS),
                        "Sodium's terrain shader got no colored light data (no globals patch, no raw uniforms)");
            } else if (Backend.RAW_UNIFORMS) {
                check(ShaderPatches.applied(ShaderPatches.VANILLA_TERRAIN + "-raw"), "the vanilla terrain shader did not get raw uniforms");
            } else {
                check(ShaderPatches.applied(ShaderPatches.VANILLA_GLOBALS), "the vanilla globals were not patched");
                check(ShaderPatches.applied(ShaderPatches.VANILLA_TERRAIN), "the vanilla terrain shader was not patched");
            }
            check(LightTracker.lightCount() == 0, "lights found in an empty dark room");
            run(mc, "setblock 0 " + floor + " 0 minecraft:torch");
        });
        within(60, () -> LightTracker.lightCount() == 1, () -> {
            check(LightTracker.lightCount() == 1, "the torch was not found (" + LightTracker.lightCount() + " lights)");
            int rgb = LightTracker.colorAt(0, floor, 0);
            check(rgb != 0, "no light in the torch's cell");
            check((rgb >> 16 & 0xFF) > (rgb & 0xFF), "the torch light is not warm: " + Integer.toHexString(rgb));
        });
        then(40, () -> {
            check(LightFrame.count() == 1, "wrong number of lights sent to the shader: " + LightFrame.count());
            if (sodium && !shaders && !ShaderPatches.applied(ShaderPatches.SODIUM_TERRAIN + "-raw")) {
                check(SodiumHook.ran(), "the Sodium hook never ran");
            }
            if (shaders || (Backend.RAW_UNIFORMS || ShaderPatches.applied(ShaderPatches.SODIUM_TERRAIN + "-raw"))) {
                check(RawUniforms.uploads() > 0, "the colored light uniforms were never uploaded");
                log("raw uniform uploads so far: " + RawUniforms.uploads());
            }
            screenshot(mc, "torch-room");
        });

        sample(mc, "colored");
        then(1, () -> {
            ratioOn = mean(ratios);
            log(String.format(Locale.ROOT, "floor red/blue ratio with color: %.3f (%s)", ratioOn, describe()));
            config.setEnabled(false);
        });
        sample(mc, "uncolored");
        then(1, () -> {
            ratioOff = mean(ratios);
            log(String.format(Locale.ROOT, "floor red/blue ratio without color: %.3f (%s)", ratioOff, describe()));
            check(shaders || ratioOn > ratioOff * 1.05, String.format(Locale.ROOT, "the torch light is not colored (ratio %.3f vs %.3f)", ratioOn, ratioOff));
            check(LightFrame.count() == 0, "lights were still sent with Colored Light off");
            config.setEnabled(true);
        });

        if (irisLoaded()) {
            then(1, () -> setShaders(!shaders));
            then(120, () -> {
                boolean now = Compat.shaderPackInUse();
                log("toggled shaders mid-game, shader pack now " + now + ", lights sent " + LightFrame.count());
                check(now != shaders, "toggling Iris shaders did not take effect");
                check(LightFrame.count() == 1, "wrong lights sent after toggling shaders: " + LightFrame.count());
                screenshot(mc, "shaders-toggled");
            });
            sample(mc, "after toggling shaders");
            then(1, () -> {
                log(String.format(Locale.ROOT, "floor brightness after toggling shaders: %s", describe()));
                if (!Compat.shaderPackInUse()) {
                    check(mean(ratios) > ratioOff * 1.05, String.format(Locale.ROOT, "the light is not colored after turning shaders off (ratio %.3f)", mean(ratios)));
                }
                setShaders(shaders);
            });
            then(120, () -> {
                check(Compat.shaderPackInUse() == shaders, "toggling Iris shaders back did not take effect");
                check(LightFrame.count() == 1, "wrong lights sent after toggling shaders back: " + LightFrame.count());
            });
        }
        then(1, () -> run(mc, "setblock 0 " + floor + " 0 minecraft:air"));
        within(40, () -> LightTracker.lightCount() == 0, () -> {
            check(LightTracker.lightCount() == 0, "a broken torch is still lit");
            if (candlesExist()) {
                run(mc, "setblock -3 " + floor + " 0 minecraft:candle[lit=false]");
            }
            run(mc, "setblock 3 " + floor + " 0 minecraft:campfire[lit=true]");
            run(mc, "setblock 0 " + (floor + 3) + " -3 minecraft:lantern[hanging=true]");
        });
        within(40, () -> LightTracker.lightCount() == 2, () -> {
            check(LightTracker.lightCount() == 2, "expected the campfire and the lantern, found " + LightTracker.lightCount() + " lights");
            if (candlesExist()) {
                run(mc, "setblock -3 " + floor + " 0 minecraft:candle[lit=true,candles=3]");
            }
        });
        within(40, () -> LightTracker.lightCount() == (candlesExist() ? 3 : 2), () -> {
            check(LightTracker.lightCount() == (candlesExist() ? 3 : 2), "the lit candle was not found");
            screenshot(mc, "mixed-lights");
            run(mc, "setblock 2 " + floor + " 0 minecraft:red_stained_glass");
        });
        within(60, () -> hasGlassLight(), () -> {
            check(hasGlassLight(), "no light came through the red stained glass");
            screenshot(mc, "stained-glass");
            config.setStainedGlass(false);
        });
        within(40, () -> !hasGlassLight(), () -> {
            check(!hasGlassLight(), "light still came through the glass with Stained Glass Tint off");
            config.setStainedGlass(true);
            run(mc, "setblock 2 " + floor + " 0 minecraft:air");
        });

        then(20, () -> Screens.open(mc, new ColoredLightSettingsScreen(null, mc.options)));
        then(20, () -> {
            check(Screens.current(mc) instanceof ColoredLightSettingsScreen, "the settings screen did not open");
            screenshot(mc, "settings");
            AbstractSliderButton slider = slider(Screens.current(mc), "Color Intensity");
            check(slider.getMessage().getString().endsWith("100%"), "the intensity slider does not start at 100%: " + slider.getMessage().getString());
            Ui.click(Screens.current(mc), Ui.x(slider) + 2.0, Ui.y(slider) + slider.getHeight() / 2.0);
            check(config.intensityPercent() == 0, "the intensity slider did not reach 0%: " + config.intensityPercent());
            check(slider.getMessage().getString().endsWith(" 0%"), "the intensity slider label is not 0%: " + slider.getMessage().getString());
            Ui.click(Screens.current(mc), Ui.x(slider) + slider.getWidth() - 2.0, Ui.y(slider) + slider.getHeight() / 2.0);
            check(config.intensityPercent() == 100, "the intensity slider did not reach 100%: " + config.intensityPercent());
            check(slider.getMessage().getString().endsWith("100%"), "the intensity slider label is not 100%: " + slider.getMessage().getString());
            Ui.click(Screens.current(mc), Ui.x(slider) + 4.0 + (slider.getWidth() - 8.0) * 0.5, Ui.y(slider) + slider.getHeight() / 2.0);
            int middle = config.intensityPercent();
            check(middle >= 30 && middle <= 70, "the intensity slider is not near 50% in the middle: " + middle);
            log("intensity slider: 0%, 100%, then " + middle + "%");
        });
        then(5, () -> {
            Screens.open(mc, null);
            ColoredLightClient.saveConfig();
            check(read(ColoredLightClient.configPath()).contains("\"intensity\": " + config.intensityPercent()), "the intensity was not saved: " + read(ColoredLightClient.configPath()));
            config.resetToDefaults();
            Screens.open(mc, new ColoredLightSettingsScreen(null, mc.options));
        });
        then(5, () -> check(slider(Screens.current(mc), "Color Intensity").getMessage().getString().endsWith("100%"),
                "the intensity slider did not come back at 100% after a reset"));
        then(5, () -> {
            Screens.open(mc, null);
            hideHud = false;
        });
    }

    private void filmScene(Minecraft mc) {
        ColoredLightConfig config = ColoredLightClient.config();
        int f = ServerCompat.FLOOR;
        then(10, () -> {
            run(mc, "fill -16 " + (f - 1) + " -16 16 " + (f + 7) + " 16 minecraft:air");
            run(mc, "fill -14 " + (f - 1) + " -14 14 " + (f + 6) + " 14 minecraft:stone_bricks hollow");
            run(mc, "fill -13 " + (f - 1) + " -13 13 " + (f - 1) + " 13 minecraft:spruce_planks");
            run(mc, "fill -13 " + (f + 6) + " -13 13 " + (f + 6) + " 13 minecraft:dark_oak_planks");
            run(mc, "fill -13 " + f + " -13 13 " + f + " -13 minecraft:mossy_stone_bricks");
            run(mc, "setblock -6 " + (f + 2) + " -13 minecraft:wall_torch[facing=south]");
            run(mc, "setblock 6 " + (f + 2) + " -13 minecraft:wall_torch[facing=south]");
            run(mc, "setblock 0 " + f + " -6 minecraft:campfire[lit=true]");
            run(mc, "setblock 7 " + f + " -1 minecraft:barrel[facing=up]");
            run(mc, "setblock 7 " + (f + 1) + " -1 minecraft:candle[lit=true,candles=4]");
            run(mc, "setblock -7 " + f + " -2 minecraft:lantern");
            run(mc, "setblock -1 " + f + " -13 minecraft:bookshelf");
            run(mc, "setblock -1 " + (f + 1) + " -13 minecraft:bookshelf");
            run(mc, "setblock 1 " + f + " -13 minecraft:crafting_table");
            run(mc, "summon minecraft:villager 2.5 " + f + " -4.5 {NoAI:1b,Rotation:[20f,0f]}");
        });
        then(60, () -> {
            run(mc, "kill @e[type=minecraft:item]");
            run(mc, "tp @p 0.5 " + (f + 1.4) + " 4.5 180 16");
            Ui.fov(mc, 70);
            config.setEnabled(false);
        });
        then(40, () -> startFilm(mc, "vanilla"));
        seconds(4, ColoredLightSelfTest::stopFilm);
        then(5, () -> config.setEnabled(true));
        then(20, () -> startFilm(mc, "coloredlight"));
        seconds(10, ColoredLightSelfTest::stopFilm);
        then(5, () -> hideHud = false);
    }

    private void glassFilmScene(Minecraft mc) {
        int f = ServerCompat.FLOOR;
        then(1, () -> {
            ColoredLightClient.config().setIntensityPercent(ColoredLightConfig.MAX_INTENSITY);
            ColoredLightClient.config().setGlassStrengthPercent(ColoredLightConfig.MAX_GLASS_STRENGTH);
            Ui.gamma(mc, 0.0);
        });
        room(mc, -6, -8, 6, 4, "polished_deepslate", "white_concrete");
        then(5, () -> run(mc, "setblock 0 " + f + " -3 minecraft:torch"));
        then(60, () -> {
            run(mc, "kill @e[type=minecraft:item]");
            run(mc, "gamemode spectator");
            run(mc, "tp @p 0.5 " + (f + 2) + " 3.5 180 20");
            Ui.fov(mc, 70);
        });
        then(60, () -> startFilm(mc, "torch"));
        seconds(6, ColoredLightSelfTest::stopFilm);
        then(5, () -> {
            run(mc, "fill -1 " + f + " -4 1 " + (f + 1) + " -2 minecraft:blue_stained_glass");
            run(mc, "setblock 0 " + f + " -3 minecraft:torch");
        });
        then(80, () -> startFilm(mc, "blue-glass"));
        seconds(6, ColoredLightSelfTest::stopFilm);
        then(5, () -> hideHud = false);
    }

    private void galleryScene(Minecraft mc) {
        then(1, () -> {
            ColoredLightClient.config().setIntensityPercent(ColoredLightConfig.MAX_INTENSITY);
            ColoredLightClient.config().setGlassStrengthPercent(ColoredLightConfig.MAX_GLASS_STRENGTH);
            Ui.gamma(mc, 0.0);
        });
        int f = ServerCompat.FLOOR;
        String camera = "0.5 " + (f + 2) + " 2.5 180 15";
        for (String color : new String[] {"red", "blue", "lime", "purple", "orange", "cyan"}) {
            room(mc, -5, -7, 5, 2, "polished_deepslate", "white_concrete");
            then(5, () -> glassWindow(mc, color));
            shot(mc, color + "-glass", camera);
        }
        String[] glass = {"red", "orange", "lime", "cyan", "blue", "purple"};
        String[] lamps = {"redstone_torch", "ochre_froglight", "verdant_froglight", "soul_lantern", "sea_lantern", "pearlescent_froglight"};
        String wide = "0.5 " + (f + 4) + " 11.5 180 22";
        room(mc, -12, -7, 12, 12, "polished_deepslate", "white_concrete");
        then(5, () -> {
            bays(mc);
            for (int i = 0; i < 6; i++) {
                int x = -10 + i * 4;
                run(mc, "fill " + (x - 1) + " " + (f + 1) + " -5 " + (x + 1) + " " + (f + 2) + " -5 minecraft:" + glass[i] + "_stained_glass");
                run(mc, "fill " + (x - 1) + " " + (f + 1) + " -6 " + (x + 1) + " " + (f + 2) + " -6 minecraft:sea_lantern");
            }
        });
        shot(mc, "glass-rainbow", wide);
        room(mc, -12, -7, 12, 12, "polished_deepslate", "white_concrete");
        then(5, () -> {
            bays(mc);
            for (int i = 0; i < 6; i++) {
                run(mc, "setblock " + (-10 + i * 4) + " " + f + " -4 minecraft:" + lamps[i]);
            }
        });
        shot(mc, "lamp-rainbow", wide);
        then(5, () -> hideHud = false);
    }

    private void glassWindow(Minecraft mc, String color) {
        int f = ServerCompat.FLOOR;
        run(mc, "fill -5 " + f + " -5 5 " + (f + 4) + " -5 minecraft:polished_deepslate");
        run(mc, "setblock 0 " + (f + 1) + " -5 minecraft:" + color + "_stained_glass");
        run(mc, "setblock 0 " + (f + 1) + " -6 minecraft:sea_lantern");
    }

    private void bays(Minecraft mc) {
        int f = ServerCompat.FLOOR;
        run(mc, "fill -12 " + f + " -5 12 " + (f + 4) + " -5 minecraft:polished_deepslate");
        for (int x = -8; x <= 8; x += 4) {
            run(mc, "fill " + x + " " + f + " -4 " + x + " " + (f + 4) + " -4 minecraft:polished_deepslate");
        }
    }

    private void room(Minecraft mc, int x1, int z1, int x2, int z2, String wall, String floor) {
        int f = ServerCompat.FLOOR;
        then(5, () -> {
            run(mc, "fill -16 " + (f - 1) + " -16 16 " + (f + 7) + " 16 minecraft:air");
            run(mc, "fill " + (x1 - 1) + " " + (f - 1) + " " + (z1 - 1) + " " + (x2 + 1) + " " + (f + 5) + " " + (z2 + 1)
                    + " minecraft:" + wall + " hollow");
            run(mc, "fill " + x1 + " " + (f - 1) + " " + z1 + " " + x2 + " " + (f - 1) + " " + z2 + " minecraft:" + floor);
        });
    }

    private void shot(Minecraft mc, String name, String camera) {
        then(60, () -> {
            run(mc, "kill @e[type=minecraft:item]");
            run(mc, "gamemode spectator");
            run(mc, "tp @p " + camera);
            Ui.fov(mc, 70);
        });
        then(80, () -> {
            log(name + ": tracked " + LightTracker.lightCount() + ", sent " + LightFrame.count() + ", floor " + Integer.toHexString(LightTracker.colorAt(0, ServerCompat.FLOOR, -2)) + ", sky " + mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new net.minecraft.core.BlockPos(0, ServerCompat.FLOOR, -2)) + ", block " + mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new net.minecraft.core.BlockPos(0, ServerCompat.FLOOR, -2)) + ", active " + ColoredLightClient.active());
            screenshot(mc, name);
        });
    }

    private int perfFrames;
    private long perfStart;

    private void perfScene(Minecraft mc) {
        ColoredLightConfig config = ColoredLightClient.config();
        int f = ServerCompat.FLOOR;
        then(10, () -> {
            Ui.unlock(mc);
            run(mc, "fill -40 " + (f - 1) + " -40 40 " + (f + 8) + " 40 minecraft:stone_bricks hollow");
            run(mc, "fill -39 " + (f - 1) + " -39 39 " + (f - 1) + " 39 minecraft:spruce_planks");
            for (int x = -36; x <= 36; x += 6) {
                for (int z = -36; z <= 36; z += 6) {
                    run(mc, "setblock " + x + " " + f + " " + z + " minecraft:torch");
                }
            }
            run(mc, "tp @p 0.5 " + (f + 5) + " 30.5 180 25");
        });
        then(100, () -> log("lights tracked: " + LightTracker.lightCount()));
        for (boolean on : new boolean[]{false, true, false, true}) {
            then(20, () -> config.setEnabled(on));
            then(60, () -> {
                perfFrames = frames;
                perfStart = System.nanoTime();
            });
            seconds(4, () -> {
                double fps = (frames - perfFrames) / ((System.nanoTime() - perfStart) / 1.0E9);
                log(String.format(Locale.ROOT, "fps with Colored Light %s: %.1f (lights sent %d, worst frame %.1f ms)",
                        on ? "on" : "off", fps, LightFrame.count(), worstFrameNanos / 1e6));
            });
            then(0, () -> worstFrameNanos = 0);
        }
        then(0, () -> log(String.format(Locale.ROOT, "full compute: last %.1f ms, average %.1f ms",
                LightTracker.lastComputeMs(), LightTracker.averageComputeMs())));
        then(0, () -> {
            LightTracker.resetTimings();
            worstFrameNanos = 0;
        });
        for (int i = 0; i < 20; i++) {
            int x = -9 + i;
            String block = i % 2 == 0 ? "minecraft:torch" : "minecraft:air";
            then(4, () -> run(mc, "setblock " + x + " " + f + " 24 minecraft:torch"));
            then(4, () -> run(mc, "setblock " + x + " " + f + " 24 " + block));
        }
        then(20, () -> log(String.format(Locale.ROOT,
                "block change patches: %d, slowest %.2f ms, worst frame while placing %.1f ms",
                LightTracker.patches(), LightTracker.maxPatchMs(), worstFrameNanos / 1e6)));
        then(5, () -> hideHud = false);
    }

    private static final String[] STRESS_LIGHTS = {"torch", "soul_torch", "redstone_torch", "copper_torch",
            "lantern", "soul_lantern", "glowstone", "sea_lantern", "shroomlight", "end_rod", "jack_o_lantern"};
    private static final String[] STRESS_GLASS = {"red", "lime", "blue", "magenta", "yellow", "cyan", "orange", "purple"};

    private void stressScene(Minecraft mc) {
        ColoredLightConfig config = ColoredLightClient.config();
        int f = ServerCompat.FLOOR;
        then(10, () -> {
            Ui.unlock(mc);
            noAfkLimit(mc);
            run(mc, "time set midnight");
            run(mc, "tp @p 0.5 " + (f + 40) + " 0.5");
        });
        then(10, () -> {
            for (int z = -60; z <= 60; z += 30) {
                run(mc, "fill -60 " + (f - 1) + " " + z + " 60 " + (f - 1) + " " + Math.min(60, z + 29) + " minecraft:stone_bricks");
            }
        });
        then(5, () -> {
            int row = 0;
            for (int z = -60; z <= 60; z += 3, row++) {
                String light = STRESS_LIGHTS[row % STRESS_LIGHTS.length];
                run(mc, "fill -60 " + f + " " + z + " 60 " + f + " " + z + " minecraft:" + light);
                if (row % 2 == 1) {
                    String glass = STRESS_GLASS[(row / 2) % STRESS_GLASS.length];
                    run(mc, "fill -60 " + (f + 2) + " " + z + " 60 " + (f + 2) + " " + z + " minecraft:" + glass + "_stained_glass");
                }
            }
        });
        then(5, () -> {
            int i = 0;
            for (int x = -48; x <= 48; x += 24) {
                for (int z = -48; z <= 48; z += 24, i++) {
                    String light = i % 2 == 0 ? "glowstone" : "sea_lantern";
                    run(mc, "fill " + x + " " + f + " " + z + " " + x + " " + (f + 120) + " " + z + " minecraft:" + light);
                    run(mc, "fill " + (x + 1) + " " + f + " " + z + " " + (x + 1) + " " + (f + 120) + " " + z
                            + " minecraft:" + STRESS_GLASS[i % STRESS_GLASS.length] + "_stained_glass");
                }
            }
            run(mc, "tp @p 0.5 " + (f + 12) + " 50.5 180 30");
        });
        then(200, () -> log("stress: lights tracked " + LightTracker.lightCount()));
        for (int round = 0; round < 2; round++) {
            for (boolean on : new boolean[]{false, true}) {
                stressPhase(mc, config, on, "static", () -> { });
                stressPhase(mc, config, on, "flying", () -> {
                    long t = System.nanoTime() / 1_000_000L;
                    double a = (t % 8000L) / 8000.0 * Math.PI * 2;
                    double x = Math.cos(a) * 70, z = Math.sin(a) * 70;
                    double y = f + 8 + 40 * (0.5 + 0.5 * Math.sin(a * 2));
                    float yaw = (float) Math.toDegrees(a) + 180f;
                    run(mc, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f 20", x, y, z, yaw));
                });
                int[] churn = {0};
                stressPhase(mc, config, on, "churn", () -> {
                    int i = churn[0]++;
                    int z = -60 + 3 * (i % 41);
                    String block = (i / 41) % 2 == 0 ? "minecraft:air" : "minecraft:" + STRESS_LIGHTS[(i % 41) % STRESS_LIGHTS.length];
                    run(mc, "fill -60 " + f + " " + z + " 60 " + f + " " + z + " " + block);
                });
            }
        }
        then(0, () -> log(String.format(Locale.ROOT, "stress full compute: last %.1f ms, average %.1f ms",
                LightTracker.lastComputeMs(), LightTracker.averageComputeMs())));
        then(5, () -> hideHud = false);
    }

    private void stressPhase(Minecraft mc, ColoredLightConfig config, boolean on, String name, Runnable everyTick) {
        then(10, () -> {
            config.setEnabled(on);
            run(mc, "tp @p 0.5 " + (ServerCompat.FLOOR + 12) + " 50.5 180 30");
        });
        then(60, () -> {
            LightTracker.resetTimings();
            worstFrameNanos = 0;
            recordedFrames = 0;
            perfFrames = frames;
            perfStart = System.nanoTime();
        });
        for (int i = 0; i < 120; i++) {
            then(1, everyTick);
        }
        then(0, () -> {
            double seconds = (System.nanoTime() - perfStart) / 1.0E9;
            double fps = (frames - perfFrames) / seconds;
            int n = recordedFrames;
            long[] sorted = Arrays.copyOf(FRAME_TIMES, n);
            Arrays.sort(sorted);
            long p99 = n == 0 ? 0 : sorted[Math.min(n - 1, (int) (n * 0.99))];
            log(String.format(Locale.ROOT,
                    "stress %-6s %-3s: %.1f fps, 1%% low %.1f fps, worst frame %.1f ms, lights sent %d, patches %d (slowest %.2f ms)",
                    name, on ? "on" : "off", fps, p99 == 0 ? 0 : 1e9 / p99, worstFrameNanos / 1e6,
                    LightFrame.count(), LightTracker.patches(), LightTracker.maxPatchMs()));
        });
    }

    private long until;

    private void seconds(double length, Runnable action) {
        then(0, () -> until = System.nanoTime() + (long) (length * 1.0E9));
        when(() -> System.nanoTime() >= until, action);
    }

    private void sample(Minecraft mc, String label) {
        then(1, () -> {
            samples.clear();
            ratios.clear();
            log("sampling floor brightness, " + label);
        });
        for (int i = 0; i < 16; i++) {
            then(3, () -> {
                run(mc, "tp @p 0.5 " + ServerCompat.FLOOR + " 5.5 180 40");
                Captures.grab(Screens.renderTarget(mc), downscale(Screens.renderTarget(mc)), this::measure);
            });
        }
        within(40, () -> samples.size() >= 16, () -> check(samples.size() >= 12, "only " + samples.size() + " brightness samples arrived"));
    }

    private static int downscale(com.mojang.blaze3d.pipeline.RenderTarget target) {
        for (int factor : new int[]{4, 2}) {
            if (target.width % factor == 0 && target.height % factor == 0) {
                return factor;
            }
        }
        return 1;
    }

    private void measure(NativeImage image) {
        try {
            int x0 = image.getWidth() * 35 / 100;
            int x1 = image.getWidth() * 65 / 100;
            int y0 = image.getHeight() * 45 / 100;
            int y1 = image.getHeight() * 75 / 100;
            double sum = 0;
            double r = 0;
            double b = 0;
            int count = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int c = Captures.rgb(image, x, y);
                    sum += 0.299 * (c >> 16 & 0xFF) + 0.587 * (c >> 8 & 0xFF) + 0.114 * (c & 0xFF);
                    r += c >> 16 & 0xFF;
                    b += c & 0xFF;
                    count++;
                }
            }
            samples.add(sum / count);
            ratios.add((r / count) / (b / count + 1));
        } finally {
            image.close();
        }
    }

    private double range() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double value : samples) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return samples.isEmpty() ? 0 : max - min;
    }

    private String describe() {
        StringBuilder out = new StringBuilder();
        for (double value : samples) {
            out.append(String.format(Locale.ROOT, "%.1f ", value));
        }
        return out.append(String.format(Locale.ROOT, "(range %.2f)", range())).toString();
    }

    private static AbstractSliderButton slider(GuiEventListener node, String caption) {
        AbstractSliderButton found = findSlider(node, caption);
        check(found != null, "no slider " + caption);
        return found;
    }

    private static AbstractSliderButton findSlider(GuiEventListener node, String caption) {
        if (node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption)) {
            return slider;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractSliderButton found = findSlider(child, caption);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean candlesExist() {
        try {
            Class.forName("net.minecraft.world.level.block.CandleBlock", false, ColoredLightSelfTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean hasGlassLight() {
        return LightTracker.hasTintedCell();
    }

    private static double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }

    private static boolean irisLoaded() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ColoredLightSelfTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static void setShaders(boolean enabled) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not toggle Iris shaders", e);
        }
    }

    private static boolean sodiumLoaded() {
        for (String name : new String[]{"net.caffeinemc.mods.sodium.client.gl.shader.GlProgram", "me.jellysquid.mods.sodium.client.gl.shader.GlProgram",
                "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager"}) {
            try {
                Class.forName(name, false, ColoredLightSelfTest.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                continue;
            }
        }
        return false;
    }

    static void noAfkLimit(Minecraft mc) {
        try {
            Object option = mc.options.getClass().getMethod("inactivityFpsLimit").invoke(mc.options);
            Class<?> limit = Class.forName("net.minecraft.client.InactivityFpsLimit");
            option.getClass().getMethod("set", Object.class).invoke(option, limit.getField("MINIMIZED").get(null));
        } catch (ReflectiveOperationException e) {
        }
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> ServerCompat.runCommand(server, command));
    }

    private void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        ServerCompat.screenshot(mc, Screens.renderTarget(mc));
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "fl-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        LogManager.getLogger("coloredlight-selftest").info("[coloredlight-selftest] " + message);
    }
}
