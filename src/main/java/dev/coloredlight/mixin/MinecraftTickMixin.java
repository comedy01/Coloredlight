package dev.coloredlight.mixin;

import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.LightTracker;
import dev.coloredlight.client.RawUniforms;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftTickMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void coloredlight$frame(boolean advanceGameTime, CallbackInfo ci) {
        RawUniforms.newFrame();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void coloredlight$tick(CallbackInfo ci) {
        try {
            LightTracker.tick((Minecraft) (Object) this);
        } catch (RuntimeException e) {
            ColoredLightClient.fail(e);
        }
    }
}
