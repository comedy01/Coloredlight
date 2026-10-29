package dev.coloredlight.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.FirelightCompat;
import dev.coloredlight.client.LightFrame;
import dev.coloredlight.client.LightVolumeBuffer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlobalSettingsUniform.class, priority = 1100)
abstract class GlobalSettingsUniformMixin {
    @Shadow
    @Final
    private GpuBuffer buffer;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
            index = 2)
    private long coloredlight$size(long size) {
        return Math.max(size, coloredlight$offset() + LightFrame.BYTES);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void coloredlight$update(CallbackInfo ci, @Local(argsOnly = true) Vec3 cameraPos) {
        try {
            LightFrame.update(cameraPos);
            LightVolumeBuffer.upload();
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buffer.slice(coloredlight$offset(), LightFrame.BYTES), LightFrame.bytes());
        } catch (RuntimeException e) {
            ColoredLightClient.fail(e);
        }
    }

    private static int coloredlight$offset() {
        return ((GlobalSettingsUniform.UBO_SIZE + 15) & ~15) + FirelightCompat.bytes();
    }
}
