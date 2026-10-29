package dev.coloredlight.client;

import java.lang.reflect.Method;

/** Lets colored light sit on top of Firelight's flicker when both mods are installed. */
public final class FirelightCompat {
    private static final int FALLBACK_BYTES = 1040;
    private static final Method PATCH;
    private static final int BYTES;

    static {
        Method patch = null;
        int bytes = 0;
        try {
            Class<?> frame = Class.forName("dev.firelight.client.FlickerFrame");
            bytes = FALLBACK_BYTES;
            try {
                bytes = frame.getField("BYTES").getInt(null);
            } catch (ReflectiveOperationException ignored) {
            }
            patch = Class.forName("dev.firelight.client.ShaderPatches").getMethod("patch", String.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        PATCH = patch;
        BYTES = (bytes + 15) & ~15;
    }

    private FirelightCompat() {
    }

    public static boolean present() {
        return BYTES > 0;
    }

    /** Bytes Firelight writes after the uniform block, which colored light must skip past. */
    public static int bytes() {
        return BYTES;
    }

    /** Runs Firelight's shader patch first, so colored light can build on its flicker. */
    public static String patch(String name, String source) {
        if (PATCH == null || source.contains("FIRELIGHT_")) {
            return source;
        }
        try {
            Object result = PATCH.invoke(null, name, source);
            return result instanceof String s ? s : source;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return source;
        }
    }
}
