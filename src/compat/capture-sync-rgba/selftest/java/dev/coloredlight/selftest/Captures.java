package dev.coloredlight.selftest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;

import java.util.function.Consumer;

final class Captures {
    private Captures() {
    }

    static void grab(RenderTarget target, int factor, Consumer<NativeImage> callback) {
        callback.accept(Screenshot.takeScreenshot(target));
    }

    static int rgb(NativeImage image, int x, int y) {
        int c = image.getPixelRGBA(x, y); // ABGR: red is the low byte
        return c & 0xFF00FF00 | (c & 0xFF) << 16 | c >> 16 & 0xFF;
    }

    static int[] abgr(NativeImage image) {
        return image.getPixelsRGBA();
    }
}
