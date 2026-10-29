package dev.coloredlight.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightColorTest {
    private static int r(int rgb) {
        return rgb >> 16 & 0xFF;
    }

    private static int g(int rgb) {
        return rgb >> 8 & 0xFF;
    }

    private static int b(int rgb) {
        return rgb & 0xFF;
    }

    @Test
    void lavaIsOrange() {
        int lava = LightColor.forBlock("lava");
        assertEquals(255, r(lava));
        assertTrue(g(lava) > b(lava) && g(lava) < 200);
    }

    @Test
    void torchIsRedOrange() {
        for (String name : new String[]{"torch", "wall_torch"}) {
            int torch = LightColor.forBlock(name);
            assertEquals(255, r(torch));
            assertTrue(g(torch) < g(LightColor.forBlock("lava")));
        }
    }

    @Test
    void redstoneIsRed() {
        int redstone = LightColor.forBlock("redstone_torch");
        assertEquals(255, r(redstone));
        assertTrue(g(redstone) < 80 && b(redstone) < 80);
    }

    @Test
    void soulIsBlue() {
        int soul = LightColor.forBlock("soul_wall_torch");
        assertTrue(b(soul) > r(soul));
    }

    @Test
    void glassFilters() {
        assertEquals(0xB02E26, LightColor.filter("red_stained_glass"));
        assertEquals(0x3AB3DA, LightColor.filter("light_blue_stained_glass_pane"));
        assertEquals(LightColor.OPAQUE, LightColor.filter("tinted_glass"));
        assertEquals(LightColor.NONE, LightColor.filter("glass"));
        assertEquals(LightColor.NONE, LightColor.filter("stone"));
        assertTrue(LightColor.isGlass("blue_stained_glass"));
        assertFalse(LightColor.isGlass("glass_pane"));
    }
}
