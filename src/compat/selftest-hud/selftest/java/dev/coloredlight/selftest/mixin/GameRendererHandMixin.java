package dev.coloredlight.selftest.mixin;

import dev.coloredlight.selftest.ColoredLightSelfTest;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererHandMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void coloredlightSelftest$hideHand(CallbackInfo ci) {
        if (ColoredLightSelfTest.hideHud) {
            ci.cancel();
        }
    }
}
