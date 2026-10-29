package dev.coloredlight.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightVolumeTest {
    private static final int N = 20;
    private static final int RED = 0xB02E26;
    private static final int BLUE = 0x3C44AA;

    private static int[] open() {
        int[] filters = new int[N * N * N];
        Arrays.fill(filters, LightColor.WHITE);
        return filters;
    }

    private static void wall(int[] filters, int x, int filter) {
        for (int y = 0; y < N; y++) {
            for (int z = 0; z < N; z++) {
                filters[LightVolume.index(N, N, x, y, z)] = filter;
            }
        }
    }

    private static int at(int[] light, int x, int y, int z) {
        return light[LightVolume.index(N, N, x, y, z)];
    }

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
    void whiteLightFallsOffOneLevelPerBlock() {
        int[] light = LightVolume.compute(N, N, N, open(), new int[]{5, 10, 10, 15, LightColor.WHITE});
        assertEquals(0xFFFFFF, at(light, 5, 10, 10));
        int three = at(light, 8, 10, 10);
        assertEquals(204, r(three));
        assertEquals(r(three), g(three));
        assertEquals(r(three), b(three));
        assertEquals(at(light, 5, 13, 10), three);
    }

    @Test
    void redGlassTintsLightBehindIt() {
        int[] filters = open();
        wall(filters, 8, RED);
        int[] light = LightVolume.compute(N, N, N, filters, new int[]{5, 10, 10, 15, LightColor.WHITE});
        int behind = at(light, 10, 10, 10);
        assertTrue(r(behind) > 100, "bright behind glass: " + Integer.toHexString(behind));
        assertTrue(g(behind) < r(behind) * 0.4, "red behind glass: " + Integer.toHexString(behind));
        int front = at(light, 6, 10, 10);
        assertEquals(g(front), r(front), "white in front of glass");
    }

    @Test
    void glassInSeriesTintsTwice() {
        int[] red = open();
        wall(red, 8, RED);
        int[] both = red.clone();
        wall(both, 10, BLUE);
        int[] source = {5, 10, 10, 15, LightColor.WHITE};
        int onlyRed = at(LightVolume.compute(N, N, N, red, source), 12, 10, 10);
        int redBlue = at(LightVolume.compute(N, N, N, both, source), 12, 10, 10);
        assertTrue(redBlue != 0);
        assertNotEquals(onlyRed, redBlue);
    }

    @Test
    void opaqueWallBlocksLight() {
        int[] filters = open();
        wall(filters, 8, LightColor.OPAQUE);
        int[] light = LightVolume.compute(N, N, N, filters, new int[]{5, 10, 10, 15, LightColor.WHITE});
        assertEquals(0, at(light, 9, 10, 10));
        assertEquals(0, at(light, 8, 10, 10));
    }

    @Test
    void weakSourceDoesNotReachFar() {
        int[] light = LightVolume.compute(N, N, N, open(), new int[]{5, 10, 10, 3, LightColor.WHITE});
        assertTrue(at(light, 7, 10, 10) > 0);
        assertEquals(0, at(light, 10, 10, 10));
    }

    @Test
    void whiteTorchMixesWithTintedLight() {
        int[] filters = open();
        wall(filters, 8, RED);
        int[] tinted = LightVolume.compute(N, N, N, filters, new int[]{5, 10, 10, 15, LightColor.WHITE});
        int[] mixed = LightVolume.compute(N, N, N, filters, new int[]{5, 10, 10, 15, LightColor.WHITE, 12, 10, 10, 10, LightColor.WHITE});
        int alone = at(tinted, 10, 10, 10);
        int both = at(mixed, 10, 10, 10);
        assertTrue(g(both) > g(alone), "torch adds white");
        assertTrue(r(both) >= g(both), "glass still shows");
    }

    @Test
    void glassHueSurvivesABrighterWhiteTorch() {
        int[] filters = open();
        wall(filters, 8, RED);
        int[] light = LightVolume.compute(N, N, N, filters, new int[]{5, 10, 10, 15, LightColor.WHITE, 13, 10, 10, 15, LightColor.WHITE});
        int near = at(light, 11, 10, 10);
        assertTrue(g(near) < r(near) * 0.85, "red still shows next to a white torch: " + Integer.toHexString(near));
    }

    @Test
    void sourceColourIsNormalised() {
        int[] light = LightVolume.compute(N, N, N, open(), new int[]{5, 10, 10, 15, 0x400000});
        assertEquals(0xFF0000, at(light, 5, 10, 10));
    }

    @Test
    void sourcesOutsideTheBoxAreIgnored() {
        int[] light = LightVolume.compute(N, N, N, open(), new int[]{-1, 10, 10, 15, LightColor.WHITE, 5, N, 5, 15, LightColor.WHITE});
        assertTrue(Arrays.stream(light).allMatch(v -> v == 0));
    }
}
