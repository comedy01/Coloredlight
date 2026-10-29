package dev.coloredlight.neoforge;

import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.gui.ColoredLightSettingsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ColoredLightClient.MOD_ID, dist = Dist.CLIENT)
public final class ColoredLightNeoForge {
    public ColoredLightNeoForge(IEventBus modBus, ModContainer container) {
        ColoredLightClient.init(FMLPaths.CONFIGDIR.get());

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new ColoredLightSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
