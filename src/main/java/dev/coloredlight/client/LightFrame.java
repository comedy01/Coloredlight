package dev.coloredlight.client;

import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class LightFrame {
    public static final float MARK = 1234.5f;
    public static final int FLOATS = 12;
    public static final int BYTES = FLOATS * 4;

    private static final float[] FRAME = new float[FLOATS];
    private static final ByteBuffer BYTES_BUFFER = ByteBuffer.allocateDirect(BYTES).order(ByteOrder.nativeOrder());
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static int count;
    private static LightTracker.Volume shown;

    private LightFrame() {
    }

    public static void update(Vec3 camera) {
        LightTracker.flush();
        float[] frame = FRAME;
        Arrays.fill(frame, 0f);
        LightTracker.Volume volume = LightTracker.volume();
        boolean enabled = ColoredLightClient.active() && camera != null && volume != null;
        if (camera != null) {
            cameraX = camera.x;
            cameraY = camera.y;
            cameraZ = camera.z;
        }
        frame[0] = ColoredLightClient.config().intensity();
        frame[1] = MARK;
        frame[2] = enabled ? 1f : 0f;
        if (enabled) {
            frame[4] = (float) (volume.originX() - cameraX);
            frame[5] = (float) (volume.originY() - cameraY);
            frame[6] = (float) (volume.originZ() - cameraZ);
            frame[8] = volume.sx();
            frame[9] = volume.sy();
            frame[10] = volume.sz();
        }
        count = enabled ? volume.lights() : 0;
        shown = enabled ? volume : null;
        ByteBuffer out = BYTES_BUFFER;
        out.clear();
        for (int i = 0; i < FLOATS; i++) {
            out.putFloat(i * 4, frame[i]);
        }
    }

    public static ByteBuffer bytes() {
        BYTES_BUFFER.clear();
        return BYTES_BUFFER;
    }

    public static void writeTo(ByteBuffer target, int offset) {
        ByteBuffer out = target.duplicate().order(ByteOrder.nativeOrder());
        for (int i = 0; i < FLOATS; i++) {
            out.putFloat(offset + i * 4, BYTES_BUFFER.getFloat(i * 4));
        }
    }

    public static LightTracker.Volume volume() {
        return shown;
    }

    public static int count() {
        return count;
    }
}
