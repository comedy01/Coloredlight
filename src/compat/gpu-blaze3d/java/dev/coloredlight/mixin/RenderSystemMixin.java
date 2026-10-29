package dev.coloredlight.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.LightVolumeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
abstract class RenderSystemMixin {
    @Inject(method = "bindDefaultUniforms", at = @At("TAIL"))
    private static void coloredlight$bindVolume(RenderPass pass, CallbackInfo ci) {
        try {
            pass.setUniform(LightVolumeBuffer.NAME, LightVolumeBuffer.bind());
        } catch (RuntimeException e) {
            ColoredLightClient.fail(e);
        }
    }
}
