package dev.coloredlight.client;

import com.mojang.math.Matrix4f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.FloatBuffer;

public final class TerrainProgram {
    private static final Logger LOGGER = LogManager.getLogger(ColoredLightClient.MOD_ID);
    private static final String VERTEX = "uniform mat4 ColoredLightViewInverse;\n"
            + "varying vec4 coloredlight_color;\n"
            + "varying vec2 coloredlight_uv;\n"
            + "varying vec2 coloredlight_lightUv;\n"
            + "varying vec3 coloredlight_eye;\n"
            + "varying vec3 coloredlight_tint;\n"
            + "void main() {\n"
            + "    vec4 eye = gl_ModelViewMatrix * gl_Vertex;\n"
            + "    gl_Position = gl_ProjectionMatrix * eye;\n"
            + "    vec3 pos = (ColoredLightViewInverse * eye).xyz;\n"
            + "    coloredlight_color = gl_Color;\n"
            + "    coloredlight_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;\n"
            + "    coloredlight_lightUv = clamp((gl_TextureMatrix[2] * vec4(gl_MultiTexCoord2.xy, 0.0, 1.0)).xy, vec2(0.5 / 16.0), vec2(15.5 / 16.0));\n"
            + "    coloredlight_tint = coloredlight_tint_at(pos);\n"
            + "    coloredlight_eye = eye.xyz;\n"
            + "}\n";
    private static final String FRAGMENT = "uniform sampler2D ColoredLightBlocks;\n"
            + "uniform sampler2D ColoredLightLightmap;\n"
            + "uniform int ColoredLightFogMode;\n"
            + "uniform int ColoredLightFogRadial;\n"
            + "varying vec4 coloredlight_color;\n"
            + "varying vec2 coloredlight_uv;\n"
            + "varying vec2 coloredlight_lightUv;\n"
            + "varying vec3 coloredlight_eye;\n"
            + "varying vec3 coloredlight_tint;\n"
            + "void main() {\n"
            + "    vec4 light = texture2D(ColoredLightLightmap, coloredlight_lightUv);\n"
            + "    if (coloredlight_lightUv.x >= 1.0 / 16.0) {\n"
            + "        vec4 sky = texture2D(ColoredLightLightmap, vec2(0.5 / 16.0, coloredlight_lightUv.y));\n"
            + "        light.rgb += max(light.rgb - sky.rgb, vec3(0.0)) * (coloredlight_tint - vec3(1.0));\n"
            + "    }\n"
            + "    vec4 color = texture2D(ColoredLightBlocks, coloredlight_uv) * coloredlight_color * light;\n"
            + "    float distance = ColoredLightFogRadial == 1 ? length(coloredlight_eye) : abs(coloredlight_eye.z);\n"
            + "    float fog = 1.0;\n"
            + "    if (ColoredLightFogMode == 1) {\n"
            + "        fog = (gl_Fog.end - distance) * gl_Fog.scale;\n"
            + "    } else if (ColoredLightFogMode == 2) {\n"
            + "        float d = gl_Fog.density * distance;\n"
            + "        fog = exp(-d * d);\n"
            + "    } else if (ColoredLightFogMode == 3) {\n"
            + "        fog = exp(-gl_Fog.density * distance);\n"
            + "    }\n"
            + "    gl_FragColor = vec4(mix(gl_Fog.color.rgb, color.rgb, clamp(fog, 0.0, 1.0)), color.a);\n"
            + "}\n";

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int program;
    private static boolean broken;
    private static boolean bound;
    private static int viewInverse;
    private static int fogMode;
    private static int fogRadial;
    private static boolean radial;
    private static Boolean otherRenderer;

    private TerrainProgram() {
    }

    public static void bind(Matrix4f view) {
        if (otherRenderer()) {
            return;
        }
        if (broken || !ColoredLightClient.active()) {
            LightFrame.update(null);
            return;
        }
        try {
            if (program == 0 && !create()) {
                return;
            }
            if (LightTracker.lightCount() == 0) {
                LightFrame.update(null);
                return;
            }
            Matrix4f inverse = view.copy();
            if (!inverse.invert()) {
                return;
            }
            GL20.glUseProgram(program);
            bound = true;
            MATRIX.clear();
            inverse.store(MATRIX);
            MATRIX.rewind();
            GL20.glUniformMatrix4fv(viewInverse, false, MATRIX);
            int mode = 0;
            if (GL11.glIsEnabled(GL11.GL_FOG)) {
                int glMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
                mode = glMode == GL11.GL_LINEAR ? 1 : glMode == GL11.GL_EXP2 ? 2 : 3;
            }
            GL20.glUniform1i(fogMode, mode);
            GL20.glUniform1i(fogRadial, radial ? 1 : 0);
            RawUniforms.onUse(program);
        } catch (RuntimeException e) {
            unbind();
            broken = true;
            ColoredLightClient.fail(e);
        }
    }

    public static void unbind() {
        if (bound) {
            bound = false;
            GL20.glUseProgram(0);
        }
    }

    private static boolean otherRenderer() {
        if (otherRenderer == null) {
            otherRenderer = present("me.jellysquid.mods.sodium.client.gl.shader.GlProgram") || present("net.optifine.Config");
            if (otherRenderer) {
                LOGGER.info("Colored Light found Embeddium or OptiFine; its own terrain shader stays off");
            }
        }
        return otherRenderer;
    }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, TerrainProgram.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean create() {
        boolean volume = GlLightVolume.supported() && GL.getCapabilities().OpenGL32;
        String version = volume ? "#version 150 compatibility\n" : "#version 120\n";
        String vertex;
        try {
            vertex = ShaderPatches.legacyTerrain(version, volume, VERTEX);
        } catch (IOException e) {
            broken = true;
            LOGGER.warn("Colored Light could not build its terrain shader; light will not be colored", e);
            return false;
        }
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, version + FRAGMENT);
        if (vs == 0 || fs == 0) {
            broken = true;
            return false;
        }
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vs);
        GL20.glAttachShader(id, fs);
        GL20.glLinkProgram(id);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOGGER.warn("Colored Light could not link its terrain shader; light will not be colored: {}", GL20.glGetProgramInfoLog(id));
            GL20.glDeleteProgram(id);
            broken = true;
            return false;
        }
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(id);
        GL20.glUniform1i(GL20.glGetUniformLocation(id, "ColoredLightBlocks"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(id, "ColoredLightLightmap"), 2);
        GL20.glUseProgram(previous);
        viewInverse = GL20.glGetUniformLocation(id, "ColoredLightViewInverse");
        fogMode = GL20.glGetUniformLocation(id, "ColoredLightFogMode");
        fogRadial = GL20.glGetUniformLocation(id, "ColoredLightFogRadial");
        radial = GL.getCapabilities().GL_NV_fog_distance;
        program = id;
        RawUniforms.shadersChanged();
        LOGGER.info("Colored Light terrain shader ready");
        return true;
    }

    private static int compile(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOGGER.warn("Colored Light could not compile its terrain shader; light will not be colored: {}", GL20.glGetShaderInfoLog(id));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }
}
