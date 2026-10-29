package dev.coloredlight.fabric;

import dev.coloredlight.client.ColoredLightClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ColoredLightFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ColoredLightClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
