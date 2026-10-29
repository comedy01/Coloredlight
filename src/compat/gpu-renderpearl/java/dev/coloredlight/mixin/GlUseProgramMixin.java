package dev.coloredlight.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.coloredlight.client.RawUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlStateManager.class, remap = false)
abstract class GlUseProgramMixin {
    @Inject(method = "_glUseProgram", at = @At("TAIL"), remap = false)
    private static void coloredlight$use(int program, CallbackInfo ci) {
        RawUniforms.onUse(program);
    }
}
