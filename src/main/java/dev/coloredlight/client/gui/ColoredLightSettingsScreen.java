package dev.coloredlight.client.gui;

import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.Texts;
import dev.coloredlight.config.ColoredLightConfig;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ColoredLightSettingsScreen extends ColoredLightOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    public ColoredLightSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Texts.translatable("coloredlight.options.title"));
    }

    @Override
    protected void addOptions() {
        ColoredLightConfig config = ColoredLightClient.config();

        addRow(
                toggle("coloredlight.options.enabled", config::enabled, config::setEnabled),
                slider("coloredlight.options.intensity", ColoredLightConfig.MIN_INTENSITY,
                        ColoredLightConfig.MAX_INTENSITY, 5, config.intensityPercent(), config::setIntensityPercent));
        addRow(
                toggle("coloredlight.options.stained_glass", config::stainedGlass, config::setStainedGlass),
                slider("coloredlight.options.glass_strength", ColoredLightConfig.MIN_GLASS_STRENGTH,
                        ColoredLightConfig.MAX_GLASS_STRENGTH, 10, config.glassStrengthPercent(),
                        config::setGlassStrengthPercent));
        addRow(resetButton(config), null);
    }

    @Override
    public void removed() {
        super.removed();
        ColoredLightClient.saveConfig();
    }

    private AbstractWidget resetButton(ColoredLightConfig config) {
        return tooltip(button(Texts.translatable("coloredlight.options.reset"), WIDTH, button -> {
            config.resetToDefaults();
            ScreenOpener.open(minecraft, new ColoredLightSettingsScreen(lastScreen, options));
        }), Texts.translatable("coloredlight.options.reset.tooltip"));
    }

    private AbstractWidget slider(String key, int min, int max, int step, int initial, Consumer<Integer> onChange) {
        return tooltip(new PercentSlider(key, min, max, step, initial, onChange), Texts.translatable(key + ".tooltip"));
    }

    private AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return tooltip(button(toggleLabel(key, getter.getAsBoolean()), WIDTH, button -> {
            setter.accept(!getter.getAsBoolean());
            button.setMessage(toggleLabel(key, getter.getAsBoolean()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component percentText(int value) {
        return Texts.literal(value + "%");
    }

    private static Component valueLabel(String key, Component value) {
        return Texts.translatable("options.generic_value", Texts.translatable(key), value);
    }

    private static Component toggleLabel(String key, boolean on) {
        return valueLabel(key, Texts.translatable(on ? "options.on" : "options.off"));
    }

    private static final class PercentSlider extends AbstractSliderButton {
        private final String key;
        private final int min;
        private final int max;
        private final int step;
        private final Consumer<Integer> onChange;

        PercentSlider(String key, int min, int max, int step, int initial, Consumer<Integer> onChange) {
            super(0, 0, WIDTH, HEIGHT, Texts.empty(), (initial - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            updateMessage();
        }

        private int percent() {
            int raw = min + (int) Math.round(value * (max - min));
            return Math.max(min, Math.min(max, Math.round(raw / (float) step) * step));
        }

        @Override
        protected void updateMessage() {
            setMessage(valueLabel(key, percentText(percent())));
        }

        @Override
        protected void applyValue() {
            int percent = percent();
            value = (percent - min) / (double) (max - min);
            onChange.accept(percent);
        }
    }
}
