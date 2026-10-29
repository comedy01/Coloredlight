package dev.coloredlight.selftest.mixin;

import dev.coloredlight.selftest.ColoredLightSelfTest;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
abstract class HudHideMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void coloredlightSelftest$hide(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (ColoredLightSelfTest.hideHud) {
            ci.cancel();
        }
    }
}
