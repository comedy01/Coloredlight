package dev.coloredlight.client;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GlLightVolume {
    public static final String NAME = "ColoredLightVolume";
    public static final int UNIT = 15;

    private static int buffer;
    private static int texture;
    private static long size = -1;
    private static LightTracker.Volume uploaded;
    private static Boolean supported;

    private GlLightVolume() {
    }

    public static boolean supported() {
        if (supported == null) {
            try {
                supported = GL.getCapabilities().OpenGL31;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return supported;
    }

    public static void upload() {
        if (!supported()) {
            return;
        }
        try {
            LightTracker.Volume volume = LightFrame.volume();
            if (volume == uploaded && buffer != 0) {
                return;
            }
            if (buffer == 0) {
                buffer = GL15.glGenBuffers();
                texture = GL11.glGenTextures();
            }
            int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
            try {
                upload(volume);
            } finally {
                GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
            }
            uploaded = volume;
        } catch (RuntimeException e) {
            uploaded = null;
            ColoredLightClient.fail(e);
        }
    }

    private static void upload(LightTracker.Volume volume) {
        if (volume == null) {
            ByteBuffer black = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, black, GL15.GL_DYNAMIC_DRAW);
            size = 4;
            return;
        }
        ByteBuffer data = volume.data().duplicate();
        data.clear();
        if (size == data.capacity() && uploaded != null && uploaded.data() == volume.data()
                && uploaded.version() == volume.baseVersion()) {
            int from = volume.dirtyFrom(), to = volume.dirtyTo();
            if (to > from) {
                data.position(from);
                data.limit(to);
                GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, from, data);
            }
            return;
        }
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        size = data.capacity();
    }

    public static void bind() {
        if (buffer == 0 || !supported()) {
            return;
        }
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL11.GL_RGBA8, buffer);
        GL13.glActiveTexture(active);
    }
}
