package dev.coloredlight.forge;

import dev.coloredlight.client.gui.ColoredLightSettingsScreen;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.network.NetworkConstants;

final class ForgeClient {
    private ForgeClient() {
    }

    static void clientOnly(ModLoadingContext context) {
        context.registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
    }

    static void register(ModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory(
                        (client, parent) -> new ColoredLightSettingsScreen(parent, client.options)));
    }
}
