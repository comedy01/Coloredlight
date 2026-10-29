package dev.coloredlight.core;

public final class LightColor {
    public static final int WHITE = 0xFFFFFF;
    public static final int OPAQUE = -1;
    public static final int NONE = 0;

    private static final String[] DYES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final int[] DYE_COLORS = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };

    private LightColor() {
    }

    static int rgb(double r, double g, double b) {
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }

    /** Light color for a block, by its registry path (e.g. "wall_torch"). */
    public static int forBlock(String name) {
        if (name.contains("soul")) {
            return rgb(0.35, 0.6, 1);
        }
        if (name.contains("redstone_lamp")) {
            return rgb(1, 0.85, 0.55);
        }
        if (name.contains("redstone")) {
            return rgb(1, 0.2, 0.15);
        }
        if (name.contains("copper_torch") || name.contains("copper_lantern")) {
            return rgb(0.45, 1, 0.6);
        }
        if (name.contains("sea_lantern") || name.contains("end_rod") || name.contains("beacon")
                || name.contains("conduit") || name.contains("end_gateway") || name.contains("end_portal")) {
            return rgb(0.8, 0.9, 1);
        }
        if (name.contains("torch")) {
            return rgb(1, 0.45, 0.2);
        }
        if (name.contains("lava") || name.contains("fire") || name.contains("lantern") || name.contains("campfire")
                || name.contains("jack_o_lantern") || name.contains("furnace") || name.contains("smoker")
                || name.contains("magma") || name.contains("candle")) {
            return rgb(1, 0.6, 0.25);
        }
        if (name.contains("amethyst") || name.contains("crying_obsidian") || name.contains("respawn_anchor")
                || name.contains("nether_portal") || name.equals("portal")) {
            return rgb(0.7, 0.35, 1);
        }
        if (name.contains("glowstone") || name.contains("shroomlight") || name.contains("cave_vines")
                || name.contains("glow_berries")) {
            return rgb(1, 0.85, 0.45);
        }
        if (name.contains("ochre_froglight")) {
            return rgb(1, 0.8, 0.4);
        }
        if (name.contains("verdant_froglight")) {
            return rgb(0.55, 1, 0.5);
        }
        if (name.contains("pearlescent_froglight")) {
            return rgb(1, 0.6, 0.95);
        }
        if (name.contains("glow_lichen") || name.contains("sculk")) {
            return rgb(0.4, 1, 0.85);
        }
        return rgb(1, 0.92, 0.8);
    }

    /** Filter color for a block: a dye color for stained glass, OPAQUE for tinted glass, NONE otherwise. */
    public static int filter(String name) {
        if (name.equals("tinted_glass")) {
            return OPAQUE;
        }
        String dye;
        if (name.endsWith("_stained_glass")) {
            dye = name.substring(0, name.length() - "_stained_glass".length());
        } else if (name.endsWith("_stained_glass_pane")) {
            dye = name.substring(0, name.length() - "_stained_glass_pane".length());
        } else {
            return NONE;
        }
        for (int i = 0; i < DYES.length; i++) {
            if (DYES[i].equals(dye)) {
                return DYE_COLORS[i];
            }
        }
        return NONE;
    }

    public static boolean isGlass(String name) {
        return name.endsWith("_stained_glass") || name.endsWith("_stained_glass_pane") || name.equals("tinted_glass");
    }
}
