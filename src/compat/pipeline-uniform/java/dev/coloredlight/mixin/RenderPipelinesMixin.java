package dev.coloredlight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.coloredlight.client.LightVolumeBuffer;
import dev.coloredlight.client.ShaderPatches;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds the light volume texel buffer to the snippet every terrain pipeline is built from. */
@Mixin(RenderPipelines.class)
abstract class RenderPipelinesMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withUniform(Ljava/lang/String;Lcom/mojang/blaze3d/shaders/UniformType;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"))
    private static RenderPipeline.Builder coloredlight$addVolume(RenderPipeline.Builder builder, String name, UniformType type,
                                                                 Operation<RenderPipeline.Builder> original) {
        RenderPipeline.Builder result = original.call(builder, name, type);
        if (!"ChunkSection".equals(name)) {
            return result;
        }
        ShaderPatches.volumeBinding = true;
        return result.withUniform(LightVolumeBuffer.NAME, UniformType.TEXEL_BUFFER, TextureFormat.RGBA8);
    }
}
