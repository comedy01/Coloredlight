package dev.coloredlight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.coloredlight.client.LightVolumeBuffer;
import dev.coloredlight.client.ShaderPatches;
import net.minecraft.client.renderer.BindGroupLayouts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BindGroupLayouts.class)
abstract class BindGroupLayoutsMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/pipeline/BindGroupLayout$Builder;withUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/pipeline/UniformType;)Lcom/mojang/renderpearl/api/pipeline/BindGroupLayout$Builder;"))
    private static BindGroupLayout.Builder coloredlight$addVolume(BindGroupLayout.Builder builder, String name, UniformType type,
                                                                  Operation<BindGroupLayout.Builder> original) {
        BindGroupLayout.Builder result = original.call(builder, name, type);
        if (!"TerrainUniform".equals(name)) {
            return result;
        }
        ShaderPatches.volumeBinding = true;
        return result.withUniform(LightVolumeBuffer.NAME, UniformType.TEXEL_BUFFER, GpuFormat.RGBA8_UNORM);
    }
}
