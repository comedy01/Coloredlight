package dev.coloredlight.selftest.neoforge;

import dev.coloredlight.selftest.ColoredLightSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "coloredlight_selftest", dist = Dist.CLIENT)
public final class ColoredLightSelfTestNeoForge {
    public ColoredLightSelfTestNeoForge() {
        ColoredLightSelfTest test = new ColoredLightSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
