package dev.coloredlight.mixin.iris;

import dev.coloredlight.client.ShaderPatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Iris compiles shader pack programs through its own GlShader, which calls GL directly and skips the GlStateManager hook. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.shader.GlShader", remap = false)
abstract class GlShaderMixin {
    @ModifyVariable(method = "createShader", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 0)
    private static String coloredlight$source(String source) {
        return ShaderPatches.patch("iris", source);
    }
}
