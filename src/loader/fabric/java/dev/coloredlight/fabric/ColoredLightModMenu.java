package dev.coloredlight.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.coloredlight.client.gui.ColoredLightSettingsScreen;
import net.minecraft.client.Minecraft;

public final class ColoredLightModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ColoredLightSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
