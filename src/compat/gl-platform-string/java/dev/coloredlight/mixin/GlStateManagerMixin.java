package dev.coloredlight.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.coloredlight.client.RawUniforms;
import dev.coloredlight.client.ShaderPatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlStateManager.class, remap = false)
abstract class GlStateManagerMixin {
    @ModifyVariable(method = "glShaderSource", at = @At("HEAD"), argsOnly = true, remap = false)
    private static String coloredlight$source(String source) {
        return ShaderPatches.patch("vanilla", source);
    }

    @Inject(method = "_glUseProgram", at = @At("TAIL"), remap = false)
    private static void coloredlight$use(int program, CallbackInfo ci) {
        RawUniforms.onUse(program);
    }
}
