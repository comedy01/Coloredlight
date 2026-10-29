package dev.coloredlight.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public final class RawUniforms {
    private static final Int2ObjectOpenHashMap<int[]> PROGRAMS = new Int2ObjectOpenHashMap<>();
    private static volatile boolean changed;
    private static volatile boolean any;
    private static int frame;
    private static int updatedFrame = -1;
    private static FloatBuffer info;
    private static FloatBuffer box;
    private static FloatBuffer size;
    private static long uploads;

    private RawUniforms() {
    }

    public static long uploads() {
        return uploads;
    }

    public static void shadersChanged() {
        changed = true;
        any = true;
    }

    public static void newFrame() {
        frame++;
    }

    public static void onCurrentProgram() {
        if (any) {
            onUse(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        }
    }

    public static void onUse(int program) {
        if (!any || program == 0) {
            return;
        }
        try {
            if (changed) {
                PROGRAMS.clear();
                changed = false;
            }
            int[] entry = PROGRAMS.get(program);
            if (entry == null) {
                entry = new int[]{GL20.glGetUniformLocation(program, "ColoredLightInfo"),
                        GL20.glGetUniformLocation(program, "ColoredLightBox"),
                        GL20.glGetUniformLocation(program, "ColoredLightSize"), -1,
                        GL20.glGetUniformLocation(program, GlLightVolume.NAME)};
                PROGRAMS.put(program, entry);
                if (entry[4] >= 0) {
                    GL20.glUniform1i(entry[4], GlLightVolume.UNIT);
                }
            }
            if (entry[4] >= 0) {
                if (updatedFrame != frame) {
                    updateFrame();
                }
                GlLightVolume.bind();
            }
            if (entry[0] < 0 || entry[3] == frame) {
                return;
            }
            if (updatedFrame != frame) {
                updateFrame();
            }
            GL20.glUniform4fv(entry[0], info);
            if (entry[1] >= 0) {
                GL20.glUniform4fv(entry[1], box);
            }
            if (entry[2] >= 0) {
                GL20.glUniform4fv(entry[2], size);
            }
            entry[3] = frame;
            uploads++;
        } catch (RuntimeException e) {
            ColoredLightClient.fail(e);
        }
    }

    private static void updateFrame() {
        updatedFrame = frame;
        LightFrame.update(Cameras.position(Minecraft.getInstance()));
        FloatBuffer all = LightFrame.bytes().asFloatBuffer();
        info = all.duplicate().position(0).limit(4).slice();
        box = all.duplicate().position(4).limit(8).slice();
        size = all.duplicate().position(8).limit(12).slice();
        GlLightVolume.upload();
    }
}
