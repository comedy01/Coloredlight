package dev.coloredlight.selftest.forge;

import dev.coloredlight.selftest.ColoredLightSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("coloredlight_selftest")
public final class ColoredLightSelfTestForge {
    public ColoredLightSelfTestForge() {
        ColoredLightSelfTest test = new ColoredLightSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
