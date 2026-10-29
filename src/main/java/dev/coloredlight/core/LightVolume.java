package dev.coloredlight.core;

/**
 * Coloured block light over a box of cells, spread like vanilla block light: one level (17 of 255) is lost per
 * step and opaque cells stop it. White and tinted light spread as two layers, so a bright white torch cannot
 * drown the hue of light that came through glass; the result keeps the brightest layer's brightness and mixes
 * the hue with the tinted layer weighted up. Glass tints light that passes straight through it, and tinted
 * light never passes back through the glass it came through, so it stays on the far side.
 */
public final class LightVolume {
    /** Brightness lost per block, matching one vanilla light level on a 0..255 scale. */
    public static final int STEP = 17;
    /** Glass never removes a channel entirely, so dark glass still lets a little through. */
    private static final float FLOOR = 0.15f;
    /** How much more the tinted layer counts than white light when the hue of a cell is mixed. */
    private static final float BOOST = 3.5f;
    /** Marks queue entries of the tinted layer. */
    private static final int TINT = 1 << 30;
    /**
     * How strongly glass tints: 1 is the tuned look, 0 makes glass clear, higher values deepen the tint. Both the
     * filter channels and the tinted layer's weight are raised to this power.
     */
    private static volatile float glassStrength = 1f;
    private LightVolume() {
    }

    public static void setGlassStrength(float strength) {
        glassStrength = Math.max(0f, strength);
    }

    /**
     * Arrays for a compute, kept between computes so a box computed every frame or every few ticks does not
     * allocate megabytes each time. Directions are +x, -x, +y, -y, +z, -z, so the opposite of d is d ^ 1.
     */
    public static final class Workspace {
        private int[] light = new int[0];
        private int[] tint = new int[0];
        private int[] filters = new int[0];
        private byte[] crossed = new byte[0];
        private final Buckets queue = new Buckets();

        public Workspace() {
        }

        private Workspace(int cells, int[] filters) {
            light = new int[cells];
            tint = new int[cells];
            crossed = new byte[cells];
            this.filters = filters;
        }

        /** Makes room for a box of the given cells and clears its light; the filters are left for the caller to fill. */
        public void prepare(int cells) {
            if (light.length < cells) {
                light = new int[cells];
                tint = new int[cells];
                filters = new int[cells];
                crossed = new byte[cells];
            } else {
                java.util.Arrays.fill(light, 0, cells, 0);
                java.util.Arrays.fill(tint, 0, cells, 0);
                java.util.Arrays.fill(crossed, 0, cells, (byte) 0);
            }
            queue.reset();
        }

        /** One filter per cell at {@link #index}, as for {@link #compute(int, int, int, int[], int[])}. */
        public int[] filters() {
            return filters;
        }
    }

    /**
     * filters holds one entry per cell at {@link #index}: {@link LightColor#WHITE} lets light pass,
     * {@link LightColor#OPAQUE} stops it, anything else is a glass colour. sources holds groups of
     * (x, y, z, level, rgb); sources outside the box are ignored. Returns packed rgb per cell.
     */
    public static int[] compute(int sx, int sy, int sz, int[] filters, int[] sources) {
        return compute(new Workspace(sx * sy * sz, filters), sx, sy, sz, sources, sources.length);
    }

    /**
     * Like {@link #compute(int, int, int, int[], int[])}, reusing the workspace's arrays. The workspace must have
     * been {@link Workspace#prepare prepared} for the box and its filters filled in; only the first sourceLength
     * entries of sources are read. Returns the workspace's light array, which may be longer than the box.
     */
    public static int[] compute(Workspace ws, int sx, int sy, int sz, int[] sources, int sourceLength) {
        int cells = sx * sy * sz;
        int[] light = ws.light;
        int[] tint = ws.tint;
        // For tinted light: 1 + the direction it last crossed glass in, 0 when it never did.
        byte[] crossed = ws.crossed;
        int[] filters = ws.filters;
        Buckets queue = ws.queue;
        float strength = glassStrength;
        boolean anyTint = false;
        for (int s = 0; s + 4 < sourceLength; s += 5) {
            int x = sources[s], y = sources[s + 1], z = sources[s + 2];
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                continue;
            }
            int level = Math.min(15, Math.max(0, sources[s + 3]));
            int rgb = sources[s + 4];
            int max = Math.max(1, Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF)));
            int value = level * STEP;
            int r = value * (rgb >> 16 & 0xFF) / max;
            int g = value * (rgb >> 8 & 0xFF) / max;
            int b = value * (rgb & 0xFF) / max;
            boolean tinted = r != g || g != b;
            anyTint |= tinted;
            spread(tinted ? tint : light, crossed, queue, index(sx, sz, x, y, z), tinted, r << 16 | g << 8 | b, (byte) 0);
        }
        int plane = sx * sz;
        // Index offsets per direction, in the order of STEPS.
        int[] offsets = {1, -1, plane, -plane, sx, -sx};
        int lastFilter = LightColor.WHITE;
        int lastScale = -1;
        for (int entry = queue.pop(); entry >= 0; entry = queue.pop()) {
            boolean tinted = (entry & TINT) != 0;
            int cell = entry & ~TINT;
            int out = decay((tinted ? tint : light)[cell]);
            if (out == 0) {
                continue;
            }
            byte from = tinted ? crossed[cell] : 0;
            int yz = cell / sx;
            int x = cell - yz * sx;
            int y = yz / sz;
            int z = yz - y * sz;
            for (int d = 0; d < 6; d++) {
                // Only the coordinate along the direction changes, so only it needs a bounds check.
                int pos = d < 2 ? x : d < 4 ? y : z;
                int limit = d < 2 ? sx : d < 4 ? sy : sz;
                int sign = (d & 1) == 0 ? 1 : -1;
                int off = offsets[d];
                int rgb = out;
                boolean layer = tinted;
                byte through = from;
                int next = cell;
                // Walks straight on through glass, so thick glass and glass in series tint once per block.
                while (true) {
                    pos += sign;
                    if (pos < 0 || pos >= limit) {
                        break;
                    }
                    next += off;
                    int f = filters[next];
                    if (f == LightColor.WHITE || f == LightColor.NONE) {
                        spread(layer ? tint : light, crossed, queue, next, layer, rgb, through);
                        break;
                    }
                    if (f == LightColor.OPAQUE || layer && through == (d ^ 1) + 1) {
                        break;
                    }
                    if (f != lastFilter) {
                        lastFilter = f;
                        lastScale = glassScale(f, strength);
                    }
                    rgb = filter(rgb, lastScale);
                    layer = true;
                    anyTint = true;
                    through = (byte) (d + 1);
                    merge(tint, next, rgb);
                    rgb = decay(rgb);
                    if (rgb == 0) {
                        break;
                    }
                }
            }
        }
        if (!anyTint) {
            return light;
        }
        float boost = (float) Math.pow(BOOST, strength);
        for (int i = 0; i < cells; i++) {
            int t = tint[i];
            if (t == 0) {
                continue;
            }
            int w = light[i];
            int bright = Math.max(max(w), max(t));
            float r = (w >> 16 & 0xFF) + boost * (t >> 16 & 0xFF);
            float g = (w >> 8 & 0xFF) + boost * (t >> 8 & 0xFF);
            float b = (w & 0xFF) + boost * (t & 0xFF);
            float k = bright / Math.max(r, Math.max(g, b));
            light[i] = Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
        }
        return light;
    }

    public static int index(int sx, int sz, int x, int y, int z) {
        return x + sx * (z + sz * y);
    }

    private static void spread(int[] layer, byte[] crossed, Buckets queue, int cell, boolean tinted, int rgb, byte through) {
        int before = max(layer[cell]);
        if (!merge(layer, cell, rgb)) {
            return;
        }
        if (tinted && max(rgb) > before) {
            crossed[cell] = through;
        }
        queue.push(tinted ? cell | TINT : cell, max(layer[cell]));
    }

    /** The light one block further on, or 0 when it is used up. */
    private static int decay(int rgb) {
        int m = max(rgb);
        if (m <= STEP) {
            return 0;
        }
        int left = m - STEP;
        return (rgb >> 16 & 0xFF) * left / m << 16 | (rgb >> 8 & 0xFF) * left / m << 8 | (rgb & 0xFF) * left / m;
    }

    private static int filter(int rgb, int f) {
        return ((rgb >> 16 & 0xFF) * (f >> 16 & 0xFF) / 255) << 16 | ((rgb >> 8 & 0xFF) * (f >> 8 & 0xFF) / 255) << 8
                | (rgb & 0xFF) * (f & 0xFF) / 255;
    }

    /** -1 for clear cells, 0 for opaque ones, otherwise the glass colour scaled so its brightest channel is 255. */
    static int glassScale(int filter) {
        return glassScale(filter, glassStrength);
    }

    private static int glassScale(int filter, float strength) {
        if (filter == LightColor.OPAQUE) {
            return 0;
        }
        if (filter == LightColor.WHITE || filter == LightColor.NONE) {
            return -1;
        }
        int r = filter >> 16 & 0xFF, g = filter >> 8 & 0xFF, b = filter & 0xFF;
        float max = Math.max(1, Math.max(r, Math.max(g, b)));
        return channel(r / max, strength) << 16 | channel(g / max, strength) << 8 | channel(b / max, strength);
    }

    private static int channel(float f, float strength) {
        return Math.round((float) Math.pow(Math.max(FLOOR, f), strength) * 255);
    }

    private static int max(int rgb) {
        return Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF));
    }

    /** Per-channel maximum; true when any channel grew. */
    private static boolean merge(int[] light, int cell, int rgb) {
        int old = light[cell];
        int r = Math.max(old >> 16 & 0xFF, rgb >> 16 & 0xFF);
        int g = Math.max(old >> 8 & 0xFF, rgb >> 8 & 0xFF);
        int b = Math.max(old & 0xFF, rgb & 0xFF);
        int merged = r << 16 | g << 8 | b;
        if (merged == old) {
            return false;
        }
        light[cell] = merged;
        return true;
    }

    /** Cells by brightness, brightest first, so most cells settle on their first visit. */
    private static final class Buckets {
        private final int[][] cells = new int[256][];
        private final int[] sizes = new int[256];
        private int top = -1;

        void push(int cell, int key) {
            int[] bucket = cells[key];
            if (bucket == null) {
                bucket = cells[key] = new int[64];
            } else if (sizes[key] == bucket.length) {
                bucket = cells[key] = java.util.Arrays.copyOf(bucket, bucket.length * 2);
            }
            bucket[sizes[key]++] = cell;
            top = Math.max(top, key);
        }

        void reset() {
            java.util.Arrays.fill(sizes, 0);
            top = -1;
        }

        int pop() {
            while (top >= 0 && sizes[top] == 0) {
                top--;
            }
            return top < 0 ? -1 : cells[top][--sizes[top]];
        }
    }
}
