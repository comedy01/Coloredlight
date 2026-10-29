package dev.coloredlight.client;

import dev.coloredlight.core.LightColor;
import dev.coloredlight.core.LightVolume;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class LightTracker {
    private static final int SECTIONS_H = Math.max(3, Integer.getInteger("coloredlight.sectionsH", 12));
    private static final int SECTIONS_V = Math.max(3, Integer.getInteger("coloredlight.sectionsV", 26));
    private static final int BELOW_H = SECTIONS_H / 2;
    private static final int BELOW_V = SECTIONS_V / 2;
    private static final int COMPUTE_H = SECTIONS_H * 16;
    private static final int COMPUTE_V = SECTIONS_V * 16;
    private static final int MARGIN = 16;
    private static final int INNER_H = COMPUTE_H - 2 * MARGIN;
    private static final int INNER_V = COMPUTE_V - 2 * MARGIN;
    private static final int INNER_BYTES = INNER_H * INNER_H * INNER_V * 4;
    private static final long[] NO_LIGHTS = new long[0];
    private static final int CACHE_LIMIT = 8192;
    private static final int REACH = 15;
    private static final int PATCH_SPAN = 16;
    private static final int PATCH_BOXES = 8;
    private static final boolean LITTLE = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final Long2ObjectOpenHashMap<Entry> CACHE = new Long2ObjectOpenHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ColoredLight volume");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<Finished> FINISHED = new AtomicReference<>();
    private static final Long2IntOpenHashMap FRESH = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap SINCE_SNAPSHOT = new Long2IntOpenHashMap();
    private static final ByteBuffer[] SPARES = new ByteBuffer[2];
    private static final IntArrayList PATCH_SOURCES = new IntArrayList();
    private static final IntArrayList WORKER_SOURCES = new IntArrayList();
    private static LightVolume.Workspace patchSpace;
    private static LightVolume.Workspace workerSpace;
    private static boolean workerHasSpace;
    private static boolean inFlight;
    private static ClientLevel cachedLevel;
    private static int generation;
    private static int lastGeneration = -1;
    private static long lastSection = Long.MIN_VALUE;
    private static boolean lastStained;
    private static int lastGlassStrength = -1;
    private static int version;
    private static int epoch;
    private static volatile Volume volume;
    private static volatile long computeNanos;
    private static volatile long computeTotalNanos;
    private static volatile int computes;
    private static long patchMaxNanos;
    private static int patchCount;
    private static int patchFrom;
    private static int patchTo;
    private static boolean patchTinted;

    private record Entry(LevelChunkSection section, long[] lights, int[] filters) {
    }

    private record Job(int epoch, int version, int minX, int minY, int minZ, Entry[] entries, boolean stained,
                       ByteBuffer target) {
    }

    private record Finished(int epoch, Volume volume) {
    }

    public record Volume(int originX, int originY, int originZ, int sx, int sy, int sz, ByteBuffer data, int lights,
                         boolean tinted, int version, boolean stained, int baseVersion, int dirtyFrom, int dirtyTo) {
        public int colorAt(int x, int y, int z) {
            int lx = x - originX, ly = y - originY, lz = z - originZ;
            if (lx < 0 || ly < 0 || lz < 0 || lx >= sx || ly >= sy || lz >= sz) {
                return 0;
            }
            int at = LightVolume.index(sx, sz, lx, ly, lz) * 4;
            return (data.get(at) & 0xFF) << 16 | (data.get(at + 1) & 0xFF) << 8 | data.get(at + 2) & 0xFF;
        }
    }

    private LightTracker() {
    }

    public static Volume volume() {
        return volume;
    }

    public static int lightCount() {
        Volume current = volume;
        return current == null ? 0 : current.lights();
    }

    public static double lastComputeMs() {
        return computeNanos / 1e6;
    }

    public static double averageComputeMs() {
        int n = computes;
        return n == 0 ? 0 : computeTotalNanos / 1e6 / n;
    }

    public static double maxPatchMs() {
        return patchMaxNanos / 1e6;
    }

    public static int patches() {
        return patchCount;
    }

    public static void resetTimings() {
        patchMaxNanos = 0;
        patchCount = 0;
    }

    public static boolean hasTintedCell() {
        Volume current = volume;
        return current != null && current.tinted();
    }

    public static int colorAt(int x, int y, int z) {
        Volume current = volume;
        return current == null ? 0 : current.colorAt(x, y, z);
    }

    public static void blockChanged(BlockPos pos, BlockState before, BlockState after) {
        if (before == after || sameLight(before, after)) {
            return;
        }
        CACHE.remove(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        int delta = (after.getLightEmission() > 0 ? 1 : 0) - (before.getLightEmission() > 0 ? 1 : 0);
        long key = pos.asLong();
        FRESH.addTo(key, delta);
        if (inFlight) {
            SINCE_SNAPSHOT.addTo(key, delta);
        }
    }

    public static void chunkChanged(ClientLevel level, int chunkX, int chunkZ) {
        if (level != cachedLevel) {
            return;
        }
        generation++;
        for (int sy = Levels.minSectionY(level); sy <= Levels.maxSectionY(level); sy++) {
            CACHE.remove(SectionPos.asLong(chunkX, sy, chunkZ));
        }
    }

    public static void flush() {
        if (FRESH.isEmpty() && FINISHED.get() == null) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level == cachedLevel && ColoredLightClient.active()) {
            boolean patching = !FRESH.isEmpty();
            long start = System.nanoTime();
            sync();
            if (patching) {
                patchMaxNanos = Math.max(patchMaxNanos, System.nanoTime() - start);
                patchCount++;
            }
        }
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || !ColoredLightClient.active()) {
            FRESH.clear();
            SINCE_SNAPSHOT.clear();
            if (volume != null || lastGeneration != -1) {
                invalidate();
            }
            if (level == null && cachedLevel != null) {
                forget();
            }
            release();
            return;
        }
        if (level != cachedLevel) {
            forget();
            cachedLevel = level;
        }
        sync();
        Vec3 camera = Cameras.position(mc);
        int csx = SectionPos.blockToSectionCoord((int) Math.floor(camera.x));
        int csy = SectionPos.blockToSectionCoord((int) Math.floor(camera.y));
        int csz = SectionPos.blockToSectionCoord((int) Math.floor(camera.z));
        long cameraSection = SectionPos.asLong(csx, csy, csz);
        boolean stained = ColoredLightClient.config().stainedGlass();
        int glassStrength = ColoredLightClient.config().glassStrengthPercent();
        LightVolume.setGlassStrength(glassStrength / 100f);
        if (inFlight || generation == lastGeneration && cameraSection == lastSection && stained == lastStained
                && glassStrength == lastGlassStrength) {
            return;
        }

        int minSy = Levels.minSectionY(level);
        int maxSy = Levels.maxSectionY(level);
        int baseSy = Math.max(minSy - 1, Math.min(csy - BELOW_V, maxSy + 2 - SECTIONS_V));
        Entry[] entries = new Entry[SECTIONS_H * SECTIONS_H * SECTIONS_V];
        for (int ix = 0; ix < SECTIONS_H; ix++) {
            int sx = csx - BELOW_H + ix;
            for (int iz = 0; iz < SECTIONS_H; iz++) {
                int sz = csz - BELOW_H + iz;
                ChunkAccess chunk = Levels.chunk(level, sx, sz);
                if (chunk == null) {
                    continue;
                }
                for (int iy = 0; iy < SECTIONS_V; iy++) {
                    int sy = baseSy + iy;
                    if (sy < minSy || sy > maxSy) {
                        continue;
                    }
                    entries[ix + SECTIONS_H * (iz + SECTIONS_H * iy)] = entry(level, chunk, sx, sy, sz);
                }
            }
        }

        lastGeneration = generation;
        lastSection = cameraSection;
        lastStained = stained;
        lastGlassStrength = glassStrength;
        Job job = new Job(epoch, ++version, (csx - BELOW_H) * 16, baseSy * 16, (csz - BELOW_H) * 16, entries, stained,
                takeSpare());
        inFlight = true;
        workerHasSpace = true;
        try {
            WORKER.execute(() -> run(job));
        } catch (RuntimeException e) {
            inFlight = false;
            throw e;
        }
    }

    private static void run(Job job) {
        Volume result = null;
        try {
            long start = System.nanoTime();
            result = compute(job);
            long took = System.nanoTime() - start;
            computeNanos = took;
            computeTotalNanos += took;
            computes++;
        } catch (Throwable t) {
            ColoredLightClient.fail(t);
        } finally {
            FINISHED.set(new Finished(job.epoch(), result));
        }
    }

    private static void sync() {
        Finished done = FINISHED.getAndSet(null);
        if (done != null) {
            inFlight = false;
            Volume result = done.volume();
            if (result != null && done.epoch() == epoch) {
                Volume old = volume;
                volume = result;
                if (old != null) {
                    retire(old.data());
                }
                apply(SINCE_SNAPSHOT);
                FRESH.clear();
            } else if (result != null) {
                retire(result.data());
            }
            SINCE_SNAPSHOT.clear();
        }
        if (!FRESH.isEmpty()) {
            apply(FRESH);
            FRESH.clear();
        }
    }

    private static void apply(Long2IntOpenHashMap changes) {
        if (changes.isEmpty()) {
            return;
        }
        Volume patched = volume == null ? null : patch(volume, changes);
        if (patched != null) {
            volume = patched;
        } else if (volume != null || !inFlight) {
            generation++;
        }
    }

    private static Volume patch(Volume v, Long2IntOpenHashMap changes) {
        long[] keys = changes.keySet().toLongArray();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : keys) {
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        patchFrom = Integer.MAX_VALUE;
        patchTo = Integer.MIN_VALUE;
        patchTinted = false;
        if (maxX - minX < PATCH_SPAN && maxY - minY < PATCH_SPAN && maxZ - minZ < PATCH_SPAN) {
            patchBox(v, minX, minY, minZ, maxX, maxY, maxZ);
        } else if (keys.length <= PATCH_BOXES) {
            for (long key : keys) {
                int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                patchBox(v, x, y, z, x, y, z);
            }
        } else {
            return null;
        }
        int lights = v.lights();
        for (long key : keys) {
            int x = BlockPos.getX(key) - v.originX(), y = BlockPos.getY(key) - v.originY(), z = BlockPos.getZ(key) - v.originZ();
            if (x >= 0 && y >= 0 && z >= 0 && x < v.sx() && y < v.sy() && z < v.sz()) {
                lights += changes.get(key);
            }
        }
        if (patchFrom >= patchTo) {
            return v;
        }
        int base = v.version(), from = patchFrom, to = patchTo;
        if (v.baseVersion() >= 0 && v != LightFrame.volume()) {
            base = v.baseVersion();
            from = Math.min(from, v.dirtyFrom());
            to = Math.max(to, v.dirtyTo());
        }
        return new Volume(v.originX(), v.originY(), v.originZ(), v.sx(), v.sy(), v.sz(), v.data(), lights,
                v.tinted() || patchTinted, ++version, v.stained(), base, from, to);
    }

    private static void patchBox(Volume v, int x0, int y0, int z0, int x1, int y1, int z1) {
        int ox = v.originX(), oy = v.originY(), oz = v.originZ();
        int ax = Math.max(ox, x0 - REACH), ay = Math.max(oy, y0 - REACH), az = Math.max(oz, z0 - REACH);
        int bx = Math.min(ox + v.sx(), x1 + REACH + 1);
        int by = Math.min(oy + v.sy(), y1 + REACH + 1);
        int bz = Math.min(oz + v.sz(), z1 + REACH + 1);
        if (ax >= bx || ay >= by || az >= bz) {
            return;
        }
        int ix0 = Math.max(ox - MARGIN, ax - REACH), iy0 = Math.max(oy - MARGIN, ay - REACH);
        int iz0 = Math.max(oz - MARGIN, az - REACH);
        int ix1 = Math.min(ox + v.sx() + MARGIN, bx + REACH), iy1 = Math.min(oy + v.sy() + MARGIN, by + REACH);
        int iz1 = Math.min(oz + v.sz() + MARGIN, bz + REACH);
        int sx0 = ix0 >> 4, sy0 = iy0 >> 4, sz0 = iz0 >> 4;
        int nw = ((ix1 - 1) >> 4) - sx0 + 1, nh = ((iy1 - 1) >> 4) - sy0 + 1, nd = ((iz1 - 1) >> 4) - sz0 + 1;
        ClientLevel level = cachedLevel;
        int minSy = Levels.minSectionY(level);
        int maxSy = Levels.maxSectionY(level);
        Entry[] entries = new Entry[nw * nh * nd];
        for (int ix = 0; ix < nw; ix++) {
            for (int iz = 0; iz < nd; iz++) {
                ChunkAccess chunk = Levels.chunk(level, sx0 + ix, sz0 + iz);
                if (chunk == null) {
                    continue;
                }
                for (int iy = 0; iy < nh; iy++) {
                    int sy = sy0 + iy;
                    if (sy >= minSy && sy <= maxSy) {
                        entries[ix + nw * (iz + nd * iy)] = entry(level, chunk, sx0 + ix, sy, sz0 + iz);
                    }
                }
            }
        }

        int w = ix1 - ix0, h = iy1 - iy0, d = iz1 - iz0;
        LightVolume.Workspace ws = patchSpace;
        if (ws == null) {
            ws = patchSpace = new LightVolume.Workspace();
        }
        ws.prepare(w * h * d);
        gather(entries, nw, nh, nd, ix0 - sx0 * 16, iy0 - sy0 * 16, iz0 - sz0 * 16, w, h, d, v.stained(), ws.filters(),
                PATCH_SOURCES);
        int[] light = LightVolume.compute(ws, w, h, d, PATCH_SOURCES.elements(), PATCH_SOURCES.size());

        ByteBuffer data = v.data();
        int count = bx - ax;
        for (int y = ay; y < by; y++) {
            for (int z = az; z < bz; z++) {
                int at = LightVolume.index(v.sx(), v.sz(), ax - ox, y - oy, z - oz) * 4;
                if (write(data, at, light, ws.filters(), LightVolume.index(w, d, ax - ix0, y - iy0, z - iz0), count)) {
                    patchTinted = true;
                }
            }
        }
        patchFrom = Math.min(patchFrom, LightVolume.index(v.sx(), v.sz(), ax - ox, ay - oy, az - oz) * 4);
        patchTo = Math.max(patchTo, (LightVolume.index(v.sx(), v.sz(), bx - 1 - ox, by - 1 - oy, bz - 1 - oz) + 1) * 4);
    }

    private static void gather(Entry[] entries, int nw, int nh, int nd, int offX, int offY, int offZ, int w, int h,
                               int d, boolean stained, int[] filters, IntArrayList sources) {
        sources.clear();
        for (int iy = 0; iy < nh; iy++) {
            int y0 = Math.max(0, iy * 16 - offY), y1 = Math.min(h, iy * 16 + 16 - offY);
            for (int iz = 0; iz < nd; iz++) {
                int z0 = Math.max(0, iz * 16 - offZ), z1 = Math.min(d, iz * 16 + 16 - offZ);
                for (int ix = 0; ix < nw; ix++) {
                    int x0 = Math.max(0, ix * 16 - offX), x1 = Math.min(w, ix * 16 + 16 - offX);
                    if (x0 >= x1 || y0 >= y1 || z0 >= z1) {
                        continue;
                    }
                    Entry entry = entries[ix + nw * (iz + nd * iy)];
                    int[] section = entry == null ? null : entry.filters();
                    for (int y = y0; y < y1; y++) {
                        for (int z = z0; z < z1; z++) {
                            int row = LightVolume.index(w, d, 0, y, z);
                            if (section == null) {
                                Arrays.fill(filters, row + x0, row + x1, LightColor.WHITE);
                                continue;
                            }
                            int from = (x0 + offX - ix * 16) + 16 * ((z + offZ - iz * 16) + 16 * (y + offY - iy * 16));
                            if (stained) {
                                System.arraycopy(section, from, filters, row + x0, x1 - x0);
                            } else {
                                for (int x = x0; x < x1; x++) {
                                    int f = section[from + x - x0];
                                    filters[row + x] = isGlass(f) ? LightColor.WHITE : f;
                                }
                            }
                        }
                    }
                    if (entry == null) {
                        continue;
                    }
                    for (long p : entry.lights()) {
                        int x = ix * 16 - offX + (int) (p & 15);
                        int y = iy * 16 - offY + (int) (p >> 8 & 15);
                        int z = iz * 16 - offZ + (int) (p >> 4 & 15);
                        if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) {
                            continue;
                        }
                        sources.add(x);
                        sources.add(y);
                        sources.add(z);
                        sources.add((int) (p >> 12 & 15));
                        sources.add((int) (p >> 16 & 0xFFFFFF));
                    }
                }
            }
        }
    }

    private static boolean write(ByteBuffer data, int at, int[] light, int[] filters, int from, int count) {
        boolean tinted = false;
        for (int i = 0; i < count; i++) {
            int rgb = light[from + i];
            data.putInt(at + i * 4, LITTLE ? 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF : rgb << 8 | 0xFF);
            if (rgb != 0 && !tinted && isGlass(filters[from + i])) {
                tinted = true;
            }
        }
        return tinted;
    }

    private static Volume compute(Job job) {
        LightVolume.Workspace ws = workerSpace;
        if (ws == null) {
            ws = workerSpace = new LightVolume.Workspace();
        }
        ws.prepare(COMPUTE_H * COMPUTE_H * COMPUTE_V);
        IntArrayList sources = WORKER_SOURCES;
        gather(job.entries(), SECTIONS_H, SECTIONS_V, SECTIONS_H, 0, 0, 0, COMPUTE_H, COMPUTE_V, COMPUTE_H,
                job.stained(), ws.filters(), sources);
        int[] light = LightVolume.compute(ws, COMPUTE_H, COMPUTE_V, COMPUTE_H, sources.elements(), sources.size());

        int lights = 0;
        int[] found = sources.elements();
        for (int s = 0; s + 4 < sources.size(); s += 5) {
            if (inner(found[s], found[s + 1], found[s + 2])) {
                lights++;
            }
        }
        ByteBuffer data = job.target() != null ? job.target()
                : ByteBuffer.allocateDirect(INNER_BYTES).order(ByteOrder.nativeOrder());
        boolean tinted = false;
        int at = 0;
        for (int y = 0; y < INNER_V; y++) {
            for (int z = 0; z < INNER_H; z++) {
                int row = LightVolume.index(COMPUTE_H, COMPUTE_H, MARGIN, MARGIN + y, MARGIN + z);
                tinted |= write(data, at, light, ws.filters(), row, INNER_H);
                at += INNER_H * 4;
            }
        }
        return new Volume(job.minX() + MARGIN, job.minY() + MARGIN, job.minZ() + MARGIN, INNER_H, INNER_V, INNER_H,
                data, lights, tinted, job.version(), job.stained(), -1, 0, INNER_BYTES);
    }

    private static void retire(ByteBuffer data) {
        if (data.capacity() != INNER_BYTES || data == SPARES[0] || data == SPARES[1]) {
            return;
        }
        if (SPARES[0] == null) {
            SPARES[0] = data;
        } else if (SPARES[1] == null) {
            SPARES[1] = data;
        } else {
            SPARES[0] = SPARES[1];
            SPARES[1] = data;
        }
    }

    private static ByteBuffer takeSpare() {
        Volume shown = LightFrame.volume();
        Volume current = volume;
        for (int i = 0; i < SPARES.length; i++) {
            ByteBuffer spare = SPARES[i];
            if (spare != null && (shown == null || shown.data() != spare) && (current == null || current.data() != spare)) {
                SPARES[i] = null;
                return spare;
            }
        }
        return null;
    }

    private static boolean inner(int x, int y, int z) {
        return x >= MARGIN && y >= MARGIN && z >= MARGIN
                && x < MARGIN + INNER_H && y < MARGIN + INNER_V && z < MARGIN + INNER_H;
    }

    private static boolean isGlass(int filter) {
        return filter != LightColor.WHITE && filter != LightColor.OPAQUE && filter != LightColor.NONE;
    }

    private static boolean sameLight(BlockState before, BlockState after) {
        int emission = Math.min(15, after.getLightEmission());
        return Math.min(15, before.getLightEmission()) == emission && filterOf(before) == filterOf(after)
                && (emission == 0 || LightSources.color(before) == LightSources.color(after));
    }

    private static int filterOf(BlockState state) {
        int filter = LightSources.filter(state);
        if (filter != LightColor.NONE) {
            return filter;
        }
        return Levels.lightBlock(state) >= 15 ? LightColor.OPAQUE : LightColor.WHITE;
    }

    private static Entry entry(ClientLevel level, ChunkAccess chunk, int sx, int sy, int sz) {
        int sectionIndex = sy - Levels.minSectionY(level);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return null;
        }
        LevelChunkSection section = sections[sectionIndex];
        if (section == null) {
            return null;
        }
        long key = SectionPos.asLong(sx, sy, sz);
        Entry entry = CACHE.get(key);
        if (entry != null && entry.section() == section) {
            return entry;
        }
        entry = scan(section);
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, entry);
        return entry;
    }

    static Entry scan(LevelChunkSection section) {
        if (Levels.onlyAir(section)) {
            return new Entry(section, NO_LIGHTS, null);
        }
        boolean mayHaveLights = Levels.mayHaveLights(section);
        long[] found = NO_LIGHTS;
        int count = 0;
        int[] filters = null;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (mayHaveLights) {
                        int light = Math.min(15, state.getLightEmission());
                        if (light > 0) {
                            if (count == found.length) {
                                found = Arrays.copyOf(found, Math.max(8, count * 2));
                            }
                            found[count++] = x | z << 4 | y << 8 | light << 12 | (long) LightSources.color(state) << 16;
                        }
                    }
                    int filter = filterOf(state);
                    if (filter != LightColor.WHITE) {
                        if (filters == null) {
                            filters = new int[4096];
                            Arrays.fill(filters, LightColor.WHITE);
                        }
                        filters[x + 16 * (z + 16 * y)] = filter;
                    }
                }
            }
        }
        return new Entry(section, count == 0 ? NO_LIGHTS : Arrays.copyOf(found, count), filters);
    }

    private static void invalidate() {
        epoch++;
        volume = null;
        lastGeneration = -1;
        lastSection = Long.MIN_VALUE;
        FRESH.clear();
        SINCE_SNAPSHOT.clear();
        Arrays.fill(SPARES, null);
    }

    private static void release() {
        patchSpace = null;
        if (workerHasSpace) {
            workerHasSpace = false;
            WORKER.execute(() -> workerSpace = null);
        }
    }

    public static void forget() {
        CACHE.clear();
        cachedLevel = null;
        invalidate();
        release();
    }
}
