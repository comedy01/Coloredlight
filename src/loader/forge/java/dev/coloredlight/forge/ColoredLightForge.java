package dev.coloredlight.forge;

import dev.coloredlight.client.ColoredLightClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(ColoredLightClient.MOD_ID)
public final class ColoredLightForge {
    public ColoredLightForge() {
        ModLoadingContext context = ModLoadingContext.get();
        ForgeClient.clientOnly(context);
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ColoredLightClient.init(FMLPaths.CONFIGDIR.get());
        ForgeClient.register(context);
    }
}
