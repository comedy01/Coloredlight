package dev.coloredlight.core;

public final class LightVolume {
    public static final int STEP = 17;
    private static final float FLOOR = 0.15f;
    private static final float BOOST = 3.5f;
    private static final int TINT = 1 << 30;
    private static volatile float glassStrength = 1f;
    private LightVolume() {
    }

    public static void setGlassStrength(float strength) {
        glassStrength = Math.max(0f, strength);
    }

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

        public int[] filters() {
            return filters;
        }
    }

    public static int[] compute(int sx, int sy, int sz, int[] filters, int[] sources) {
        return compute(new Workspace(sx * sy * sz, filters), sx, sy, sz, sources, sources.length);
    }

    public static int[] compute(Workspace ws, int sx, int sy, int sz, int[] sources, int sourceLength) {
        int cells = sx * sy * sz;
        int[] light = ws.light;
        int[] tint = ws.tint;
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
                int pos = d < 2 ? x : d < 4 ? y : z;
                int limit = d < 2 ? sx : d < 4 ? sy : sz;
                int sign = (d & 1) == 0 ? 1 : -1;
                int off = offsets[d];
                int rgb = out;
                boolean layer = tinted;
                byte through = from;
                int next = cell;
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
