package dev.coloredlight.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.coloredlight.client.ColoredLightClient;
import dev.coloredlight.client.LightVolumeBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(ChunkSectionsToRender.class)
abstract class ChunkSectionsToRenderMixin {
    @Inject(method = "renderLayers", at = @At("HEAD"))
    private void coloredlight$bindVolume(CallbackInfo ci, @Local(argsOnly = true) RenderPass pass) {
        try {
            pass.setUniform(LightVolumeBuffer.NAME, LightVolumeBuffer.bind());
        } catch (RuntimeException e) {
            ColoredLightClient.fail(e);
        }
    }
}
