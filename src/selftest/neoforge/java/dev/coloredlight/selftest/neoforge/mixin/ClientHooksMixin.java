package dev.coloredlight.selftest.neoforge.mixin;

import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ClientHooks.class, remap = false)
abstract class ClientHooksMixin {
    @Redirect(method = "createGpuDevice", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;getAsBoolean()Z"), require = 0)
    private static boolean coloredlightSelftest$noValidation(ModConfigSpec.BooleanValue value) {
        return false;
    }

    @Redirect(method = "createGpuDevice", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;getDefault()Ljava/lang/Object;"), require = 0)
    private static Object coloredlightSelftest$noValidationDefault(ModConfigSpec.BooleanValue value) {
        return Boolean.FALSE;
    }
}
