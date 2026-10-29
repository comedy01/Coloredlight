package dev.coloredlight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class LightVolumeBuffer {
    public static final String NAME = "ColoredLightVolume";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;

    private static GpuBuffer buffer;
    private static GpuBuffer empty;
    private static LightTracker.Volume uploaded;

    private LightVolumeBuffer() {
    }

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
