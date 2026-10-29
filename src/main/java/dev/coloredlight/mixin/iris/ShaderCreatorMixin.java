package dev.coloredlight.mixin.iris;

import dev.coloredlight.client.ShaderPatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Iris compiles the gbuffers programs (terrain and the rest) here rather than through GlShader. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
abstract class ShaderCreatorMixin {
    @ModifyVariable(method = "createShader", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 0)
    private static String coloredlight$source(String source) {
        return ShaderPatches.patch("iris-gbuffers", source);
    }
}
