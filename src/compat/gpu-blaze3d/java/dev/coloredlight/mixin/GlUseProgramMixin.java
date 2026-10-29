package dev.coloredlight.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.coloredlight.client.RawUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris binds its shader pack programs here; they take the raw uniforms. */
@Mixin(value = GlStateManager.class, remap = false)
abstract class GlUseProgramMixin {
    @Inject(method = "_glUseProgram", at = @At("TAIL"), remap = false)
    private static void coloredlight$use(int program, CallbackInfo ci) {
        RawUniforms.onUse(program);
    }
}
