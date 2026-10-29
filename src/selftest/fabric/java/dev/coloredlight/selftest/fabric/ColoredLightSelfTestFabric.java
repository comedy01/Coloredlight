package dev.coloredlight.selftest.fabric;

import dev.coloredlight.selftest.ColoredLightSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ColoredLightSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ColoredLightSelfTest test = new ColoredLightSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
