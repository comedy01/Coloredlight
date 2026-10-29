package dev.coloredlight.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderPatches {
    public static final String VANILLA_GLOBALS = "vanilla-globals";
    public static final String SODIUM_GLOBALS = "sodium-globals";
    public static final String VANILLA_TERRAIN = "vanilla-terrain";
    public static final String SODIUM_TERRAIN = "sodium-terrain";
    public static final String IRIS_TERRAIN = "iris-terrain";

    private static final Logger LOGGER = LogManager.getLogger(ColoredLightClient.MOD_ID);
    private static final String FIELDS = "\n    vec4 ColoredLightInfo;\n    vec4 ColoredLightBox;\n    vec4 ColoredLightSize;";
    private static final String DEFINE = "#define COLOREDLIGHT_GLOBALS\n";

    private record Block(String name, String layout, String lastField) {
    }

    private record Terrain(String name, String needs, String find, String replace, boolean sodium, boolean tint) {
    }

    private static final String FIRELIGHT_FIELDS = "vec4FirelightInfo;vec4FirelightSources[64];";
    private static final String FIRELIGHT_LAST = "vec4 FirelightSources[64];";

    private static final List<Block> BLOCKS = List.of(
            new Block(VANILLA_GLOBALS, "layout(std140)uniformGlobals{ivec3CameraBlockPos;floatGlintAlpha;vec3CameraOffset;"
                    + "floatGameTime;vec2ScreenSize;intMenuBlurRadius;intUseRgss;};", "int UseRgss;"),
            new Block(VANILLA_GLOBALS, "layout(std140)uniformGlobals{ivec3CameraBlockPos;vec3CameraOffset;vec2ScreenSize;"
                    + "floatGlintAlpha;floatGameTime;intMenuBlurRadius;intUseRgss;};", "int UseRgss;"),
            new Block(SODIUM_GLOBALS, "layout(std140)uniformu_Globals{mat4u_ProjectionMatrix;mat4u_ModelViewMatrix;"
                    + "vec4u_FogColor;vec2u_EnvironmentFog;vec2u_RenderFog;vec2u_TexelSize;vec2u_TexCoordShrink;floatu_FadePeriodInv;boolu_UseRGSS;};",
                    "bool u_UseRGSS;"),
            new Block(VANILLA_GLOBALS, "layout(std140)uniformGlobals{ivec3CameraBlockPos;floatGlintAlpha;vec3CameraOffset;"
                    + "floatGameTime;vec2ScreenSize;intMenuBlurRadius;intUseRgss;" + FIRELIGHT_FIELDS + "};", FIRELIGHT_LAST),
            new Block(VANILLA_GLOBALS, "layout(std140)uniformGlobals{ivec3CameraBlockPos;vec3CameraOffset;vec2ScreenSize;"
                    + "floatGlintAlpha;floatGameTime;intMenuBlurRadius;intUseRgss;" + FIRELIGHT_FIELDS + "};", FIRELIGHT_LAST),
            new Block(SODIUM_GLOBALS, "layout(std140)uniformu_Globals{mat4u_ProjectionMatrix;mat4u_ModelViewMatrix;"
                    + "vec4u_FogColor;vec2u_EnvironmentFog;vec2u_RenderFog;vec2u_TexelSize;vec2u_TexCoordShrink;floatu_FadePeriodInv;boolu_UseRGSS;"
                    + FIRELIGHT_FIELDS + "};", FIRELIGHT_LAST));

    private static final List<Terrain> TERRAINS = List.of(
            new Terrain(VANILLA_TERRAIN, "vec3 pos = ", "vertexColor = Color * sample_lightmap(Sampler2, UV2);",
                    "vertexColor = Color * coloredlight_sample(Sampler2, UV2, pos);", false, false),
            new Terrain(VANILLA_TERRAIN, "vec3 pos = ", "vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);",
                    "vertexColor = Color * coloredlight_sample(Sampler2, UV2, pos);", false, false),
            new Terrain(SODIUM_TERRAIN, "vec3 position = ", "v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);",
                    "v_Color = _vert_color * coloredlight_light(u_LightTex, _vert_tex_light_coord, position);", true, false),
            new Terrain(SODIUM_TERRAIN, "vec3 position = ", "v_Color = _vert_color * _sample_lightmap(u_LightTex, _vert_tex_light_coord);",
                    "v_Color = _vert_color * coloredlight_sample(u_LightTex, _vert_tex_light_coord, position);", true, false),
            new Terrain(SODIUM_TERRAIN, "vec3 position = ", "v_LightCoord = _vert_tex_light_coord;",
                    "v_LightCoord = _vert_tex_light_coord;\n    v_Color.rgb *= coloredlight_vertex_tint(_vert_tex_light_coord, position);", true, true),
            new Terrain(SODIUM_TERRAIN, "vec3 pos = ", "v_LightCoord = a_LightCoord;",
                    "v_LightCoord = a_LightCoord;\n    v_Color.rgb *= coloredlight_vertex_tint(a_LightCoord, pos);", true, true),
            new Terrain(VANILLA_TERRAIN, "vec3 pos = ", "vertexColor = Color * firelight_sample(Sampler2, UV2, pos);",
                    "vertexColor = Color * coloredlight_flicker_sample(Sampler2, UV2, pos);", false, false),
            new Terrain(SODIUM_TERRAIN, "vec3 position = ", "v_Color = _vert_color * texture(u_LightTex, firelight_coord(_vert_tex_light_coord, position));",
                    "v_Color = _vert_color * coloredlight_light(u_LightTex, firelight_coord(_vert_tex_light_coord, position), position);", true, false),
            new Terrain(SODIUM_TERRAIN, "vec3 position = ", "v_Color = _vert_color * firelight_sample(u_LightTex, _vert_tex_light_coord, position);",
                    "v_Color = _vert_color * coloredlight_flicker_sample(u_LightTex, _vert_tex_light_coord, position);", true, false),
            new Terrain(SODIUM_TERRAIN, "vec3 position = ", "v_LightCoord = firelight_coord(_vert_tex_light_coord, position);",
                    "v_LightCoord = firelight_coord(_vert_tex_light_coord, position);\n    v_Color.rgb *= coloredlight_vertex_tint(_vert_tex_light_coord, position);", true, true),
            new Terrain(SODIUM_TERRAIN, "vec3 pos = ", "v_LightCoord = firelight_coord(a_LightCoord, pos);",
                    "v_LightCoord = firelight_coord(a_LightCoord, pos);\n    v_Color.rgb *= coloredlight_vertex_tint(a_LightCoord, pos);", true, true),
            new Terrain(IRIS_TERRAIN, "getVertexPosition()", "glColor = _vert_color;",
                    "glColor = _vert_color;\n    glColor.rgb *= coloredlight_vertex_tint(_vert_tex_light_coord, getVertexPosition().xyz);", false, true),
            new Terrain(IRIS_TERRAIN, "getVertexPosition()", "glColorRaw = _vert_color;",
                    "glColorRaw = _vert_color;\n    glColorRaw.rgb *= coloredlight_vertex_tint(_vert_tex_light_coord, getVertexPosition().xyz);", false, true));

    private static final String RAW = "uniform vec4 ColoredLightInfo;\nuniform vec4 ColoredLightBox;\nuniform vec4 ColoredLightSize;\n#define COLOREDLIGHT_GLOBALS\n";
    private static final String HALF_TEXEL = "#define COLOREDLIGHT_HALF_TEXEL\n";
    private static final String VOLUME = "#define COLOREDLIGHT_VOLUME\nuniform samplerBuffer ColoredLightVolume;\n";

    public static volatile boolean volumeBinding;
    private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)");

    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    private static String code;

    private ShaderPatches() {
    }

    public static boolean applied(String name) {
        return APPLIED.contains(name);
    }

    public static String patch(String name, String source) {
        if (source == null || source.contains("COLOREDLIGHT_")) {
            return source;
        }
        try {
            String patched = name.startsWith("iris") ? source : globals(FirelightCompat.patch(name, source));
            patched = terrain(name, patched);
            return patched;
        } catch (RuntimeException | IOException e) {
            LOGGER.warn("Colored Light could not patch {}", name, e);
            return source;
        }
    }

    private static String globals(String source) {
        int search = 0;
        while (true) {
            int start = source.indexOf("layout(std140)", search);
            if (start < 0) {
                return source;
            }
            int close = source.indexOf("};", start);
            if (close < 0) {
                return source;
            }
            String squashed = squash(source.substring(start, close + 2));
            search = close + 2;
            for (Block block : BLOCKS) {
                if (squashed.equals(block.layout()) && !(Backend.RAW_UNIFORMS && block.name().equals(VANILLA_GLOBALS))) {
                    int field = source.indexOf(block.lastField(), start);
                    int end = field + block.lastField().length();
                    String withFields = source.substring(0, end) + FIELDS + source.substring(end);
                    APPLIED.add(block.name());
                    return withFields.substring(0, start) + DEFINE + withFields.substring(start);
                }
            }
            if (!Backend.RAW_UNIFORMS && (squashed.startsWith("layout(std140)uniformGlobals{") || squashed.startsWith("layout(std140)uniformu_Globals{"))) {
                LOGGER.warn("Colored Light does not know this layout of the shader globals; light will not be colored: {}", squashed);
            }
        }
    }

    private static String terrain(String name, String source) throws IOException {
        for (Terrain terrain : TERRAINS) {
            if (!source.contains(terrain.find()) || !source.contains(terrain.needs())) {
                continue;
            }
            int found = source.indexOf(terrain.find());
            int main = source.indexOf("void main()");
            if (main > found) {
                main = source.lastIndexOf("\nvoid ", found) + 1;
            }
            if (main <= 0) {
                LOGGER.warn("Colored Light could not patch {}: no main function", name);
                return source;
            }
            if (terrain.tint() && terrain.replace().contains("v_Color")) {
                int color = source.indexOf("v_Color = ");
                if (color < 0 || color > source.indexOf(terrain.find())) {
                    LOGGER.warn("Colored Light could not patch {}: v_Color is not set before the light coordinate", name);
                    continue;
                }
            }
            boolean raw = name.startsWith("iris") || (terrain.sodium() ? !usesSodiumGlobals(source) : Backend.RAW_UNIFORMS);
            boolean half = terrain.find().contains(" sample_lightmap(") || source.contains("(uv / 256.0) + 0.5 / 16.0")
                || source.contains("#define FIRELIGHT_HALF_TEXEL");
            String header = (raw ? RAW : "") + (half ? HALF_TEXEL : "") + (volume(terrain, name, source, raw) ? VOLUME : "");
            APPLIED.add(terrain.name());
            if (raw) {
                APPLIED.add(terrain.name() + "-raw");
                RawUniforms.shadersChanged();
            }
            return (source.substring(0, main) + header + code() + "\n" + source.substring(main)).replace(terrain.find(), terrain.replace());
        }
        return source;
    }

    public static String legacyTerrain(String version, boolean volume, String body) throws IOException {
        APPLIED.add(VANILLA_TERRAIN);
        APPLIED.add(VANILLA_TERRAIN + "-raw");
        if (volume) {
            APPLIED.add(VANILLA_TERRAIN + "-volume");
        }
        return version + RAW + (volume ? VOLUME : "") + code() + "\n" + body;
    }

    private static boolean volume(Terrain terrain, String name, String source, boolean raw) {
        if (volumeBinding && !terrain.sodium() && name.contains("terrain")) {
            return true;
        }
        return raw && GlLightVolume.supported() && glslVersion(source) >= 140;
    }

    private static int glslVersion(String source) {
        Matcher m = VERSION.matcher(source);
        return m.find() ? Integer.parseInt(m.group(1)) : 110;
    }

    private static boolean usesSodiumGlobals(String source) {
        return source.contains("sodium:include/globals.glsl") || source.contains("sodium:globals.glsl") || source.contains("uniform u_Globals");
    }

    private static String code() throws IOException {
        if (code == null) {
            try (InputStream in = ShaderPatches.class.getResourceAsStream("/assets/coloredlight/glsl/colored.glsl")) {
                if (in == null) {
                    throw new IOException("colored.glsl is missing from the jar");
                }
                code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return code;
    }

    private static String squash(String text) {
        return text.replaceAll("//[^\n]*", "").replaceAll("\\s+", "");
    }
}
