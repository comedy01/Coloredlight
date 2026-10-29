package dev.coloredlight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The light volume on the GPU, as an RGBA8 texel buffer the terrain shaders read with texelFetch. A tiny black
 * buffer stands in while there is no volume, because the pipelines always expect the binding.
 */
public final class LightVolumeBuffer {
    public static final String NAME = "ColoredLightVolume";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;

    private static GpuBuffer buffer;
    private static GpuBuffer empty;
    private static LightTracker.Volume uploaded;

    private LightVolumeBuffer() {
    }

    /**
     * Uploads the volume the frame's uniforms describe if it changed. Runs with the uniforms each frame, before any
     * render pass is open, because buffer writes are not allowed inside one.
     */
    public static void upload() {
        LightTracker.Volume volume = LightFrame.volume();
        if (volume != null) {
            try {
                upload(volume);
                return;
            } catch (RuntimeException e) {
                ColoredLightClient.fail(e);
            }
        }
        uploaded = null;
    }

    /** The buffer to bind in a terrain pass: the uploaded volume, or the black stand-in. */
    public static GpuBuffer bind() {
        if (uploaded != null && buffer != null && !buffer.isClosed()) {
            return buffer;
        }
        if (empty == null || empty.isClosed()) {
            ByteBuffer black = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            empty = RenderSystem.getDevice().createBuffer(() -> "ColoredLight empty volume", USAGE, black);
        }
        return empty;
    }

    private static void upload(LightTracker.Volume volume) {
        if (volume == uploaded && buffer != null && !buffer.isClosed()) {
            return;
        }
        ByteBuffer data = volume.data().duplicate();
        data.clear();
        // A volume patched in place after a block change only needs the bytes the patches touched.
        if (buffer != null && !buffer.isClosed() && buffer.size() == data.capacity() && uploaded != null
                && uploaded.data() == volume.data() && uploaded.version() == volume.baseVersion()) {
            int from = volume.dirtyFrom(), to = volume.dirtyTo();
            if (to > from) {
                RenderSystem.getDevice().createCommandEncoder()
                        .writeToBuffer(buffer.slice(from, to - from), data.slice(from, to - from));
            }
            uploaded = volume;
            return;
        }
        if (buffer == null || buffer.isClosed() || buffer.size() != data.capacity()) {
            if (buffer != null && !buffer.isClosed()) {
                buffer.close();
            }
            buffer = RenderSystem.getDevice().createBuffer(() -> "ColoredLight volume", USAGE, data.capacity());
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        uploaded = volume;
    }
}
