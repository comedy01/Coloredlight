# Colored Light — Handoff

Read this first when starting a new session in this project. **Continue implementing the plan below without asking the user anything.**

## CURRENT (2026-10-27) Firelight compat
**User request:** "make the mod we just made colored lights compatible with firelight, same flickering effect but just with the new colours".
Code done: FirelightCompat (reflection: runs Firelight's ShaderPatches.patch first and offsets the UBO past FlickerFrame.BYTES), SodiumHook OFFSET, both GlobalSettingsUniformMixins, colored.glsl `coloredlight_flicker_sample`, ShaderPatches FIRELIGHT BLOCKS/TERRAINS.
Video sent (2026-10-27): selftest `glassFilmScene` (`-Pfl.glassfilm=true`), frames are upright (no vflip), 49 fps. Finding: an orange torch (1,0.45,0.2) boxed in blue glass barely turns blue (orange x blue filter is roughly neutral). Possible fix if the user wants it: blue glass hue should win more. Firelight jar removed from run/mods.
Next: build Fabric 26.3, put Firelight/build/release/firelight-1.0.2+mc26.3.jar in run/mods, runSelftest, check log for patch warnings plus color ratio.


**Status 2026-10-27:** Fabric 26.3 build OK. runSelftest with firelight-1.0.2+mc26.3 in run/mods: ALL CHECKS PASSED, ratio 1.81 colored vs 1.59 plain, flicker visible (sample range 10-16), no patch warnings. Firelight jar removed from run/mods again. Next: build the other targets (they're not built yet) and re-upload if the user asks.

## OLDER (2026-10-26), then only the parts you need

**User request:** "keep MIT, start on a full bug check, and then start the full backporting process, also make it compatible with shaders". The license is MIT (decided). The Glass Strength slider goes 0–300%. The jar's slider is right: the user's 100% cap came from an old jar, so tell them that.

**Token rule (user):** use few tokens. After each step below, update this section (mark it done, add notes), then tell the user they can `/clear` and say "resume".

**Bug found:** only 26.3 (gpu-renderpearl) shows color. `ShaderPatches.volumeBinding` is set only by renderpearl, and the VOLUME header needs `name.contains("terrain")`, but the GL path patches the name "vanilla". So GL raw, Sodium raw, blaze3d 26.1–26.2 and 1.16.5 get no `COLOREDLIGHT_VOLUME`, and `coloredlight_tint_at` returns vec3(1).

**Steps:**
1. [x] Write `src/main/java/dev/coloredlight/client/GlLightVolume.java` (GL31):
   - Use a `GL_TEXTURE_BUFFER` texture plus a buffer, with `glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA8, buf)`.
   - Mirror `src/compat/gpu-renderpearl/java/.../LightVolumeBuffer.java`'s upload logic:
     - Skip when `volume == uploaded`.
     - Do a partial `glBufferSubData` over dirtyFrom..dirtyTo when `uploaded.data()==volume.data() && uploaded.version()==volume.baseVersion()`.
     - Otherwise do a full `glBufferData`.
   - Use a 4-byte black stand-in when `LightFrame.volume()` is null.
   - Bind on fixed texture unit 15, saving and restoring GL_ACTIVE_TEXTURE and the texture-buffer binding.
2. [x] `RawUniforms`:
   - Add a 5th entry for the `ColoredLightVolume` location and set `glUniform1i(loc, 15)` once.
   - In the once-per-frame block, call `GlLightVolume.upload()`.
   - Bind the TBO on unit 15 before the `entry[3]==frame` early return.
3. [x] ShaderPatches: VOLUME when (renderpearl binding && terrain) or (raw && GL3.1 && #version>=140). TBO RGBA8 on unit 15 (GlLightVolume), sampler set+bound in RawUniforms.onUse. 1.16.5 TerrainProgram: `#version 150 compatibility` for VS+FS when GL3.2, else 120 without volume. NOT COMPILED YET.
4. [x] Fabric 1.21.11 vanilla + Sodium: ALL CHECKS PASSED, floor red/blue 1.89 colored vs 1.64 uncolored (color works). Fixes: GlLightVolume uses `GL31.GL_TEXTURE_BUFFER` as the glGet pname; LightTracker.filterOf now calls new `Levels.lightBlock(state)` (added to all 5 levels-* packs: getLightDampening on 26, getLightBlock() on 2102, getLightBlock(EmptyBlockGetter, ZERO) older). Only 1.21.11 compiled so far; the matrix (step 7) checks the rest.
5. [x] Blaze3d 26.1–26.2: `gpu-blaze3d` now has LightVolumeBuffer (renderpearl port), uploads in GlobalSettingsUniformMixin, and binds in RenderSystemMixin (TAIL of bindDefaultUniforms). The pipeline hook on "ChunkSection" is split by API: `pipeline-uniform` (26.1/26.1.2, RenderPipeline.Builder.withUniform) and `pipeline-bindgroup` (26.2, BindGroupLayouts). Fabric 26.2 and 26.1.2 compile; the 26.2 self-test passes. Not checked in-game yet (26.1.x has no self-test).
6. [x] Iris: ShaderCreatorMixin patches gbuffers as "iris-gbuffers"; IRIS_TERRAIN tints glColor/glColorRaw via coloredlight_vertex_tint (RAW + VOLUME). Raw uniforms upload via new GlUseProgramMixin (_glUseProgram TAIL -> RawUniforms.onUse) in gpu-renderpearl and gpu-blaze3d. 26.3 `runSelftest -Piris` ALL CHECKS PASSED (red/blue 1.83 colored vs 1.49); plain selftest still passes.
7. [x] Matrix 42/42 OK + legacy-forge 1.16.5 OK (fixed hasOnlyAir via Levels.onlyAir).
8. [x] DESCRIPTION rewritten 2026-10-26 with shader support (headline, feature, compat, FAQ); DESCRIPTION line 46 + README line 47 updated. All 33 jars sent as coloredlight-1.0.1-all.zip. Next: ask user before CurseForge upload.
9. [x] (user 2026-10-26: 26.3 worked in-game with and without shaders) Dev self-tests on the major versions: `./selftests.sh` (TARGETS env overrides the list) writes build/selftests/summary.txt. First run: fabric 26.1.2 and neoforge 26.3 PASS; the other six failed to compile (InactivityFpsLimit is 1.21.2+), fixed with a reflection helper `noAfkLimit` in ColoredLightSelfTest. Re-run of those six: log in build/selftests/run2.log. Result: all six FAILED "torch light is not colored" (ratio ~0.60 vs ~0.70); cause was the TEST: pre-26.x `getPixelRGBA` returns ABGR so red/blue were swapped (real ratios 1.67 vs 1.43 = colored fine). Fixed `rgb()` in capture-116/capture-sync-118/capture-sync-rgba Captures; summary now shows the selftest FAILED line. Re-run of the six: build/selftests/run3.log. Re-run: all 6 ALL CHECKS PASSED (ratio on ~1.6x off). selftests.sh FAILED grep now uses -F.
10. [x] Real-install tests on all 42 targets with `docs/realtest.py` (a port of Firelight's realtest.py): portablemc offline install, a vanilla flat-world server, quick-play join, then FAIL on Mixin/NoSuchMethod/crash lines, PASS 25s after 'Dev joined'. Jars are frozen in build/release. Run: `python -P docs/realtest.py > build/realtest.log` (WKFROM=<loader>-<mc> resumes; args filter as `<loader>-<mc>`, e.g. `forge-1.16.5`). Result 2026-10-26: 42/42 PASS. forge 1.16.5 failed at first (terrain shader varying `coloredlight_light` clashed with the colored.glsl function of the same name), fixed by renaming it to `coloredlight_lightUv` in gl-116 TerrainProgram; the rebuilt jar was copied to build/release and passes (build/realtest-116.log). legacy-forge has no runSelftest task.
11. [ ] Gallery images: `galleryScene` (`-Pfl.gallery=true`, fabric 26.3, ~1.5 min) now shoots 6 pitch dark rooms (gamma 0), each with one sea lantern behind one stained-glass block (red, blue, lime, purple, orange, cyan). Logs show a saturated light volume on the floor (e.g. blue 0409aa), but in the screenshots the floor is almost uncolored (beige, faint red). The tint looks too weak in the render; fixing that needs a mod change, which the user has not approved yet. Added fl-06-glass-rainbow (6 lamps behind red/orange/lime/cyan/blue/purple glass in one room) and fl-07-lamp-rainbow (6 differently colored lamps); both sent to the user, awaiting feedback.
12. [x] CurseForge upload to project 1767795: 29/29 jars, see `docs/uploaded-1.0.1.json`.

## Goal (rewritten by the user)

All of Firelight's flicker code goes. The mod becomes **colored block light plus stained-glass tinting**, client-side and without shaders.

- Every light source gives off its own color. Examples: lava lights a dark cave orange, torches give a red-orange glow, redstone gives red.
- **Stained glass:** light shining through colored glass takes on the glass color. Light passing through several colors of glass in series, and sources behind different glass colors, must be handled properly.
- Colors blend where sources overlap.
- Works on vanilla rendering and on Sodium/Embeddium.
- Disabled while an Iris shaderpack is in use (`Compat.shaderPackInUse()`).

## Name and identity (decided)

- **Display name:** "Colored Light". "Colored Lights" mods are a different kind of mod: they add new light blocks.
- **Slug:** `colored-light` on CurseForge and Modrinth. It was free on both when checked.
  - Taken: `colored-lights` and, on Modrinth, `colored-lighting`.
  - Rejected or taken names: Tinct, Lumina, Chromalux.
- **Subtitle:** "Colored Lighting for Sodium & Embeddium"
- **Release:** CurseForge first.
- **Repo URL in metadata:** `github.com/comedy01/ColoredLight`. This repo does not exist yet.
- **Competitors:**
  - Colorful Lighting (erykczy): not compatible with Sodium.
  - Colorful Lighting: Sodium/Embeddium Edition. Its version coverage is unverified (TODO).
  - Gegy's Colored Lights (abandoned) and RPLE.
- **First release target (proposed, not confirmed by the user):** NeoForge 1.21.1 and Forge 1.20.1.

## Rules

- `C:\Users\Alten\IdeaProjects\ColoredLight` is **not a git repo**. It came from a `git archive` of Firelight 1.0.1 at `C:\Users\Alten\IdeaProjects\Firelight`, renamed to modId `coloredlight` and package `dev.coloredlight`.
- **Do not touch** the Firelight repo's uncommitted working-copy changes.
- Run `git init` only if the user asks. Commit or push only when asked. **No attribution lines** in commit messages or PRs.
- Use absolute paths in Bash.

## Build and test

- **Tests:** `cd /c/Users/Alten/IdeaProjects/ColoredLight && JAVA_HOME=~/.jdks/ms-21.0.12.1 ./gradlew test -q`
  - The default JAVA_HOME is JDK 8, which is too old.
  - Failures are recorded in `build/test-results/test/*.xml`; grep for `<failure`.
- **Also run:** `./gradlew build` and `./gradlew runSelftest`, using the same JAVA_HOME.
- **Build flags:**
  - `-Psodium`, `-Piris` and `-Ploader=neoforge|forge`
  - `-Pminecraft_version=...`
  - `-Pfl.X=Y`, which becomes `-Dcoloredlight.X`
- **Other JDKs:** `~/.jdks/ms-25.0.4.1` and `~/.jdks/openjdk-26.0.2.1`.
- **gradle.properties:** minecraft_version=26.3, loader=fabric, mod_version=1.0.1, maven_group=dev.coloredlight, archives_base_name=coloredlight.
- **How build.gradle picks versions:**
  - It reads `versions/<mc>.properties` or `versions/<loader>/<mc>.properties`.
  - The `packs=` line lists compat packs in `src/compat/<pack>/java`, each with optional `mixins.txt` and `selftest-mixins.txt`.
- **Example `packs=` lines:**
  - 1.21.11: `options-list,screen-field,world-gamerules,input-event,text-components,server-119,backend-raw,gl-opengl,camera-getmain,levels-2102,torches-base,capture-scaled`
  - forge/1.16.5: `...levels-116,torches-116,capture-116,client-level-116,gl-116`
  - 26.3: `...backend-ubo,levels-26,torches-base,capture-scaled,shaders-include,gpu-renderpearl,camera-main`
- **legacy-forge/build.gradle (Forge 1.16.5):**
  - Compiles with release 17, then a jvmdg downgrade, so records and switch expressions are fine.
  - `main_excludes` drops `dev/coloredlight/mixin/ClientLevelMixin.java`.
- **Version targets:**
  - `versions/*.properties`: Fabric 1.18.2 to 26.3
  - `versions/forge/`: 1.16.5, 1.18.2, 1.19.2 and 1.20.1
  - `versions/neoforge/`: 1.21 to 26.3

## Status

### CURRENT (2026-10-26)

Version goal: Fabric 1.18.2 to 26.3 (every minor), NeoForge 1.21 to 26.3, Forge 1.16.5 (`legacy-forge/`), 1.18.2, 1.19.2, 1.20.1. Optional extras: Forge 1.12.2, NeoForge 1.20.1.

The Minecraft side is implemented. Fabric 26.3 builds, the unit tests pass and `runSelftest` prints ALL CHECKS PASSED. Mixed-direction glass sources that get merged use dir 7 (`GlassLight.ANY_DIRECTION`). Known limitation: color can leak a little around corners next to stained glass (noted in the README).
Matrix build: `bash matrix.sh` (repo root) builds all 42 configs one at a time (22 Fabric, 16 NeoForge, 4 Forge). It writes `build/matrix/<loader>-<v>.log` and `build/matrix/summary.txt` (OK/FAIL per config, then DONE). Run it in the background and never start a second Gradle build at the same time.

Matrix result (2026-10-25): **42/42 OK** (22 Fabric, 16 NeoForge, 4 Forge), no FAILs. `build/matrix/summary.txt` ends with DONE.

Far-light fix (2026-10-25): the user reported that lights a little further away were not colored, and later that a hard source cap made blocks gain and lose the color while moving.
- `LightColors.MAX_SOURCES` went from 64 to 128. `FLOATS` follows from it, so it is now 516.
- `ShaderPatches` builds the uniform array size from `MAX_SOURCES` (`FIELDS`, `RAW`). It also prepends `#define COLOREDLIGHT_MAX_SOURCES` to colored.glsl, so the GLSL loop limit always matches.
- LOD clusters (`core/LightClusters`): sources within 16 blocks (Chebyshev) stay exact. Further away, sources with the same dir share a slot per grid cell, and the cell size doubles with each doubling of distance. The centre and color are weighted by level², and the merged level is enough to reach every block any member reaches (capped at 63). If the result still has more than MAX_SOURCES slots, every far cell grows a step (up to MAX_EXTRA = 7 times; near lights never merge, see the near-glass fix below). Past the coarsest grid the furthest slots are dropped. `Candidates.fit` in LightTracker calls it.
- `LightTracker`: RANGE 128 horizontally, VERTICAL ±64, CACHE_LIMIT 8192. Stained glass was traced only within GLASS_RANGE 64; superseded by the glass-distance fix below.
- Edge fade: `LightFrame` puts RANGE in frame[0] / `ColoredLightInfo.x`. colored.glsl and its Java mirror `LightColors.tint` fade the tint to white over the last 16 blocks, so there is no hard cutoff.
- Tests (now including LightClustersTest and a fade test) and the self-test pass. Matrix rerun: 42/42 OK.

Glass-distance fix (2026-10-25): the user saw a light behind stained glass tint its surroundings with the glass color up close, then the light color at mid distance, then the glass color again further out.
- Cause: merging many glass virtual sources into one slot lost their combined weight against the single real light, and glass was only traced to 64 + GLASS_REACH.
- `LightClusters.Result.weights`: single slots get `LightColors.WEIGHT_UNIT` (4). Merged slots get `weightUnits(sum of level² / merged level²)`, in quarters, clamped to 1..255.
- The weight is packed into y: `LightColors.encodeY(y, units)` = y + 512*units, decoded by `weightUnits(float)`. `tint()` and colored.glsl multiply the weight by units/4. units 0 (raw y) means no multiplier.
- `LightTracker`: GLASS_RANGE was removed, so glass is traced over the full RANGE + GLASS_REACH. Glass sections are a `Long2ObjectOpenHashMap` keyed by `SectionPos.asLong`. `GlassLight.propagate` results are cached in `PROPAGATED` per light (limit 4096), cleared when the generation changes (block or chunk change) or in `forget()`.
- Tests (new weight tests in LightClustersTest and LightColorsTest) and the self-test pass. The Fabric 26.3 jar is installed in the user's mods folder, awaiting in-game feedback.

Stability fix (2026-10-25, after user report "moving further and closer the light keeps changing"): `LightClusters` cells were keyed on camera-relative coordinates, so every camera step regrouped far lights (new centroids, colors and weights, amplified by the weight multiplier). Cells are now world-aligned (`fit(..., ox, oy, oz, previous)`; `LightTracker` passes the camera block and a static `lastExtra`), and the extra coarsening only drops back once the finer grid fits within 3/4 of the slots. A light can still change cell when it crosses a distance band (16/32/64… blocks from the camera), which is unavoidable. New tests: `slotsDoNotChangeWhenTheCameraMoves` and `coarseningOnlyDropsWithRoomToSpare` (31 tests). Waiting on an in-game retest; if it still shifts, the next suspect is the merged-slot weight multiplier (consider capping it).

Near-glass fix (2026-10-25/26, after user report "up close the light through glass doesn't even color the surroundings anymore"):
- Cause 1: glass virtual sources only had a flat 2x weight, so near a single pane or window, the unfiltered torch behind the glass (which the shader cannot occlude) tied with them. The floor came out orange, not red.
- Cause 2: under coarse fits (extra > 0), even lights within 16 blocks got merged, mixing the glass sources with the torch.
- `LightColors.tint` and colored.glsl now use a cone weight instead of `weight *= 2`. If `along = dot(d, n) < 0` the source is skipped. Otherwise `cone = along/length` (0.25 for ANY_DIRECTION) and `weight *= 1 + CONE*cone²`, with CONE = 15. The Java and GLSL code must stay identical.
- `LightClusters.shift` returns 0 for lights within EXACT = 16, whatever the extra, so near lights never merge. `MAX_FAR_EXTRA` was removed. Past MAX_EXTRA = 7 the furthest slots are dropped (cap).
- New tests:
  - GlassTintEndToEndTest: `singlePaneTintsBeyondIt`, `windowTintsBeyondIt`, `singlePaneTintsAfterCoarseFits`, `windowTintsInADenseCrowd`. They check that air and the floor surface beyond the glass come out red (r > 0.99, g/b < 0.6) from close and far cameras.
  - LightClustersTest: `nearLightsStayExactInAHugeCrowd`, plus the shift asserts.
  - All tests and the self-test pass. The 26.3 jar is installed, awaiting the in-game retest.
- Not done: `LightTracker.blockChanged` bumps the generation on every block change. That only costs performance; fixing it needs a per-version canOcclude API.

Glass-source redesign (2026-10-26, after user report "colored light through glass only barely visible at specific angles and distances, and the hard edge is back, moving with the camera, quite close"):
- Cause 1: few glass cells emitted sources, and those often got sideways dirs, so the cone weight pointed the wrong way.
- Cause 2: the EXACT = 16 boundary is camera-relative, so lights past 16 blocks always merged, giving a hard edge that moved with the camera.
- `GlassLight.propagate` (RADIUS 14 diamond BFS, THRESHOLD 64, FLOOR 0.15): every visited glass cell whose color differs from the source by >= THRESHOLD emits a source toward an exit axis (sign of the offset; the exit cell is inside the radius and neither opaque nor glass). Ties go to the axis with the most glass beside the cell (the wall normal), then the largest |offset|. `merge` keys on cell + dir, so ANY_DIRECTION (7) is no longer produced (still decoded).
- `LightClusters`: MIN_EXTRA = -4. A negative extra widens the exact radius (EXACT << -extra, so 256 blocks at -4, which covers the whole tracked range). The fit starts at the previous extra, tries one finer and takes it if it fits in 3/4 of the slots, otherwise coarsens until it fits. With up to 128 sources nothing merges at all. `LightTracker.lastExtra` starts and resets at MIN_EXTRA.
- Tests: `GlassLightTest.wallCellsAllFaceAwayFromTheTorch`, the merge tests split into a same-dir and a different-dir case, `LightClustersTest.nothingMergesWhileEverythingFits`, plus new shift asserts.

Next steps for the next session:
1. Optional extras: Forge 1.12.2, NeoForge 1.20.1.
2. In-game testing on vanilla rendering and on Sodium/Embeddium (the matrix only proves the configs compile).
3. Do not upload to CurseForge without asking the user first.

Build: `export JAVA_HOME=~/.jdks/ms-21.0.12.1`. Forge 1.16.5: `cd legacy-forge && ./gradlew build`. Self-test: `./gradlew runSelftest > build/selftest-run.log`, then grep for ALL CHECKS PASSED.

The sections below are the original plan and are kept for history.

### DONE: platform-independent core (20 unit tests pass)

**`core/LightColors.java`**
- Constants: `MAX_SOURCES=64`, `FLOATS=260`, `MARK=1234.5f`.
- `encode(level, dir, rgb)` = `level*262144 + dir*32768 + r5*1024 + g5*32 + b5`. The result is an exact integer as a float.
- Decoders: `level(f)`, `dir(f)` and `rgb(f)`.
- `normal(dir)`: 1=+x, 2=−x, 3=+y, 4=−y, 5=+z, 6=−z; 0 returns null.
- **Frame layout:** `[0]` unused, `[1]` intensity, `[2]` count, `[3]` MARK, then 4 floats per source (relative x, y, z, encoded).
- **`tint(frame, x, y, z, intensity)`** is the Java mirror of the GLSL and must stay identical to it:
  - reach = level − manhattan distance; the source is skipped if reach ≤ 0.
  - weight = reach².
  - A directional source is skipped if dot(offset, normal) < 0; otherwise its weight is doubled.
  - The result is the weighted average color, normalized by its max channel, then `1 + (n−1)*intensity`.

**`core/LightColor.java`**
- Constants: `WHITE=0xFFFFFF`, `OPAQUE=−1`, `NONE=0`.
- **`forBlock(name)`** maps a registry path to a color:

  | Block names | Color |
  |---|---|
  | soul | blue |
  | redstone_lamp | warm |
  | redstone | red |
  | copper torch / copper lantern | green |
  | sea_lantern, end_rod, beacon | cool white |
  | torch | (1, .45, .2) |
  | lava, fire, lantern, furnace, magma, candle | (1, .6, .25) |
  | amethyst, portal | purple |
  | glowstone etc. | yellow |
  | froglights | their own colors |
  | glow_lichen, sculk | teal |
  | anything else | (1, .92, .8) |

- **`filter(name)`:** dye colors for `*_stained_glass(_pane)`, OPAQUE for `tinted_glass`, otherwise NONE.
- **`isGlass(name)`**

**`core/GlassLight.java`**
- Constants: RADIUS=14, THRESHOLD=64, FLOOR=.15.
- Types: `interface Grid { int filter(x, y, z) }` and `record Source(x, y, z, level, dir, rgb)`.
- **`propagate(grid, source)`:**
  - A BFS within manhattan RADIUS, with level dropping by 1 per step.
  - At a glass cell, the color is multiplied by the filter.
  - If the color changed by ≥ THRESHOLD, it emits a virtual directional Source with dir = step index + 1 and the remaining level.
  - OPAQUE cells block.
- **`merge(list)`:** a weighted average per cell. dir becomes 0 if the directions differ.
- Also: `multiply`, and the public `pack(x, y, z)`.

**Tests:** `LightColorsTest`, `LightColorTest` and `GlassLightTest` in `src/test/java/dev/coloredlight/core`. FlickerTest is already gone.

### TODO: the Minecraft side (nothing here has been changed yet)

These files still contain the old flicker code and must be replaced or deleted:
- `core/Flicker` and `core/FlameKind`
- `client/FlickerFrame`, `client/FlameTracker` and `client/Flames`
- `glsl/flicker.glsl`
- the `torches-{116,base,legacy}` compat packs

#### Old templates, for reference

**FlameTracker**
- Fields: `RANGE=40`, `CACHE_LIMIT=2048`, `Long2ObjectOpenHashMap<Entry> CACHE`, `cachedLevel`, `volatile Snapshot snapshot`.
- `blockChanged(BlockPos, BlockState before, BlockState after)` removes the `SectionPos.asLong(x>>4, y>>4, z>>4)` entry.
- `chunkChanged(ClientLevel, cx, cz)` loops `Levels.minSectionY..maxSectionY`.
- **`tick(Minecraft mc)`:**
  - Reads `ColoredLightClient.config()` and `ColoredLightClient.active()`.
  - Camera position comes from `Cameras.position(mc)`.
  - Loops over sections around the camera with `SectionPos.blockToSectionCoord`. `Levels.chunk(level, sx, sz)` may return null.
  - Section lookup is `chunk.getSections()[sy − minSectionY]`. A cache hit counts only if `entry.section() == section`.
- **`scan(section)`:**
  - Checks `Levels.mayHaveFlames` first.
  - Loops y/z/x calling `section.getBlockState(x, y, z)` and takes `min(15, getLightEmission())`.
  - Packs `x | z<<4 | y<<8 | ...`.
- **`Candidates`:** growing arrays with `order = (long) dist<<32 | idx`. `nearest(limit)` sorts.
- **`forget()`**

**FlickerFrame**
- Fields: FRAME float[], `BYTES` buffer (allocateDirect, nativeOrder) and static cameraX/Y/Z.
- **`update(Vec3 camera)`:**
  - count = `ColoredLightClient.active() && camera != null && !Compat.shaderPackInUse() ? snapshot.count() : 0`.
  - Writes position = snapshot + 0.5 − camera, using `out.putFloat(i*4, …)`, and zero-fills the rest.
- Also: `bytes()`, `writeTo(ByteBuffer target, int offset)` (duplicate, nativeOrder) and `count()`.
- `time()` and `flicker(packed, x, y, z)` must go. Grep for callers of `FlickerFrame.flicker`, which may be in a Sodium vertex hook or entity code.

**Levels packs**
- All 5 packs have `mayHaveFlames(LevelChunkSection)` = `section != null && !section.hasOnlyAir() && section.maybeHas(Flames::maybeFlame)`. levels-116 uses `!section.isEmpty()` instead.
- They also provide minSectionY, maxSectionY, chunk, chunkX and chunkZ, which are version-specific.

#### Implementation plan (do these in order)

1. **`client/LightSources`** (replaces Flames)
   - `maybeLight(state)` = `state.getLightEmission() > 0`.
   - `maybeGlass(state)` = `LightColor.isGlass(name(state))`.
   - `color(state)` = `LightColor.forBlock(name)`; `filter(state)` = `LightColor.filter(name)`.
   - `name(state)` is the last `.` segment of `block.getDescriptionId()`, cached in an `IdentityHashMap<Block, String>`.
2. **Levels (all 5 packs):** rename `mayHaveFlames` to `mayHaveLights` (using `LightSources::maybeLight`) and add `mayHaveGlass` (using `LightSources::maybeGlass`).
3. **`client/LightTracker`** (replaces FlameTracker, same structure)
   - Cache entries hold packed longs (pos 12 bits | light 4 | rgb 24) plus a `hasGlass` flag.
   - If config `stainedGlass()` is on and there is glass in range:
     - Build a Grid from `level.getBlockState(BlockPos)`: glass → its filter, `canOcclude()` → OPAQUE, otherwise WHITE.
     - Run `GlassLight.propagate` per source, `merge` the results and add the virtual sources.
     - Cache the glass result by a generation counter (bumped on block/chunk changes) plus the camera section.
   - `Snapshot(count, x, y, z, levels, dirs, rgbs)` uses `nearest(LightColors.MAX_SOURCES)`.
   - `blockChanged` invalidates when either the old or the new state is a light or glass.
4. **`client/LightFrame`** (replaces FlickerFrame)
   - Header: (0, intensity, count, MARK).
   - Each source: (pos + .5 − camera, `LightColors.encode(level, dir, rgb)`).
   - Keep `bytes()`, `writeTo()`, `count()` and `BYTES`.
5. **`glsl/colored.glsl`** (replaces flicker.glsl)
   - Use the guard `COLOREDLIGHT_COLORED_GLSL`, keeping the same `#ifdef COLOREDLIGHT_GLOBALS` / `#else` passthrough structure.
   - Uniforms: `ColoredLightInfo` (vec4) and `ColoredLightSources[i]` (vec4).
   - `vec3 coloredlight_tint_at(vec3 pos)` mirrors `LightColors.tint` and returns vec3(1) if `Info.w != 1234.5`.
   - **`coloredlight_sample(sampler2D, ivec2 uv, vec3 pos)`:**
     - full = the normal sample.
     - sky = the sample with block coordinate x = 0.5/16.
     - rgb = `full.rgb + max(full − sky, 0)*(tint − 1)`.
     - Keep the clamp to [0.5/16, 15.5/16], `COLOREDLIGHT_HALF_TEXEL` and the `__VERSION__ >= 130` variants.
   - Also add `coloredlight_light(sampler2D, vec2 coord, vec3 pos)`.
   - `coloredlight_vertex_tint(vec2 coord, vec3 pos)` = `mix(vec3(1), tint, bl/(bl + sl + .001))`.
6. **Config:**
   - Keep `enabled`.
   - Add `intensity`: 0–100, default 100; the getter returns 0..1.
   - Add `stainedGlass`, default true.
   - Drop strength and torches/lanterns/candles/fires/furnaces/entities.
7. **Settings screen:** row 1 is enabled plus the intensity slider; row 2 is stainedGlass plus reset. Also update the options screens in the compat packs, ColoredLightModMenu and the ForgeClient variants.
8. **en_us.json:** keys title, enabled, intensity, stained_glass and reset. Each gets a tooltip except title.
9. **ShaderPatches:**
   - Load colored.glsl.
   - Sodium patch 3 becomes `v_Color = _vert_color * coloredlight_light(u_LightTex, _vert_tex_light_coord, position);`.
   - Patches 5/6 append `v_Color.rgb *= coloredlight_vertex_tint(<coord>, position|pos);`, but only if `v_Color = ` appears earlier. Otherwise warn and skip.
10. **gl-116:**
    - TerrainProgram: use a tint varying via `coloredlight_tint_at(pos)` with the full/sky formula.
    - TerrainProgram (lines ~28, 74, 81–82) and LevelRendererMixin: replace the FlickerFrame and FlameTracker references.
11. **Other callers:**
    - `GlobalSettingsUniformMixin` (gpu-blaze3d and gpu-renderpearl) → LightFrame.
    - `RawUniforms` lines 62–63.
    - `SodiumHook`: OFFSET 192; the log message becomes "colored light data".
    - `MinecraftTickMixin` → `LightTracker.tick`.
    - `ClientLevelMixin` (main and client-level-116) → `LightTracker.blockChanged` and `chunkChanged`.
12. **SelfTest** (`src/selftest/java/dev/coloredlight/selftest/ColoredLightSelfTest.java`):
    - Flicker references are around lines 245–307, 353–376 and 463/472.
    - Use `LightTracker.snapshot().count()` and `LightFrame.count()`.
    - Replace the torches-off check.
    - Turn the candle check into a check that the candle color is present.
13. **Metadata:** rewrite the descriptions in fabric.mod.json, mods.toml and neoforge.mods.toml, and rewrite README.md (still Firelight text).
14. **Delete:**
    - `Flicker`, `FlameKind`, `FlickerFrame`, `FlameTracker`, `Flames` and `flicker.glsl`.
    - The `torches-*` packs, and remove them from every `packs=` line in `versions/**` and `legacy-forge`.
15. **Verify:**
    - `grep -rniE "flicker|flame|Torches" src versions legacy-forge` must come back clean.
    - Run the tests, build and runSelftest on the default target, plus other versions and loaders where feasible.
    - Check that `getDescriptionId` and `canOcclude` exist on every MC version back to 1.16.5.
16. **Report to the user**, including the known limitation: the shader does no occlusion, so a source's own color can leak around and through glass near the source.

## Useful checks

- **Modrinth slug:** `curl https://api.modrinth.com/v2/project/<slug>` returns 200 if the slug is taken and 404 if it's free.
- **CurseForge slug:** WebFetch `https://www.curseforge.com/minecraft/mc-mods/<slug>`. A 404 means it's free.

Light volume rewrite (2026-10-26, after user direction "quality first ... from a distance or up close shouldn't matter ... light through glass needs to work properly"; 26.3 only until it works, then backport):
- `core/LightVolume.compute`: a CPU flood fill of coloured block light, spread like vanilla light (STEP 17 lost per block, brightest-first bucket queue, per-channel max merge). Opaque blocks stop the light. Glass multiplies it by its colour, normalised so the brightest channel is 255, with FLOOR 0.15. 8 LightVolumeTest tests.
- `LightTracker` computes a 10×6×10-section box on a daemon worker and publishes a 128×64×128 `Volume` (MARGIN 16, direct RGBA8 `data`). `LightFrame` puts the origin relative to the camera and the size into ColoredLightBox/ColoredLightSize. `volume()` is the volume those uniforms describe.
- GPU (renderpearl compat): `BindGroupLayoutsMixin` adds a TEXEL_BUFFER RGBA8_UNORM `ColoredLightVolume` to the "TerrainUniform" layout. `LightVolumeBuffer.upload()` runs at the TAIL of `GlobalSettingsUniformMixin.update`, outside render passes. `bind()` does no writes and returns the buffer, or a 4-byte black one. `ChunkSectionsToRenderMixin` sets the uniform at the HEAD of `renderLayers`. `ShaderPatches` adds `VOLUME` only to vanilla terrain shaders when `volumeBinding` is set. colored.glsl reads the volume with texelFetch under `#ifdef COLOREDLIGHT_VOLUME`.
- Selftest passes, with a floor red/blue ratio of 4.311 with colour vs 1.567 without. The jar is installed and waiting for in-game feedback.
- Open: the Sodium terrain path has no volume yet. The tint through glass looks weak in fl-02-stained-glass.png; consider a stronger tint or a lower FLOOR.

**Glass hue vs white torches (2026-10-26):** LightVolume now spreads two layers: white light (`light[]`) and tinted light (`tint[]`, from coloured sources or anything that crossed glass). Tinted light never passes back through the glass it crossed (`crossed[]` stores its direction). Output keeps the brightest layer's brightness, and the hue is `w + 2.5*t` (BOOST). Shader strength is now `* 6.0`, up from 3.0. Horizontal range is BELOW_H/ABOVE_H 7/6 (compute 224, inner 192, ±96 blocks); fall back to 6/5 if it is too slow. New test: `glassHueSurvivesABrighterWhiteTorch`. 9/9 tests and the selftest pass (ratio 4.313).

**Session end state (2026-10-26, closed after the glass-hue change):**
- Installed jar: `coloredlight-1.0.1+mc26.3.jar` (80777 bytes, built 17:53), copied to `%APPDATA%\.minecraft\Fabric\26.3\mods\`. The user still has to test it in game.
- Verified: 9/9 LightVolumeTest, 5/5 LightColorTest, selftest "ALL CHECKS PASSED" (floor red/blue ratio 4.313 with colour, 1.566 without).
- Not verified: performance at range 192 (fall back to BELOW_H/ABOVE_H 6/5 if slow), and the user's own room with several lights and one glass side. If the white torch still drowns the glass hue there, raise `LightVolume.BOOST` (2.5) or the multiplier in `colored.glsl` line 55 (6.0).
- Open: the Sodium terrain path has no volume. `ShaderPatches.terrain` adds VOLUME only when `volumeBinding && !terrain.sodium() && name.contains("terrain")`. Backports to other versions and loaders wait until 26.3 is accepted.
- LightVolume internals: `STEP=17`, `FLOOR=0.15`, `BOOST=2.5`, queue flag `TINT=1<<30`, and `STEPS` ordered so the opposite of d is `d^1`. Sources are normalised to `level*STEP` and go to the tint layer if r!=g||g!=b. Glass cells get the filtered light merged into `tint[]` for display only and are not queued; the walk then continues straight through. Output for cells with tint: brightness `max(max(w),max(t))`, hue `w + BOOST*t`.
- Data flow: `LightTracker.tick` packs lights as `x|z<<4|y<<8|level<<12|rgb<<16` and submits a Job to the single WORKER (guarded by BUSY). `compute` turns non-stained glass into WHITE, runs `LightVolume.compute(224,COMPUTE_V,224,...)` and publishes the inner 192 box as RGBA8 (MARGIN 16, CACHE_LIMIT 8192). `LightFrame` (MARK 1234.5, 12 floats) holds intensity, mark, enabled, origin minus camera and size. It is enabled only when active, a camera exists, no shader pack is in use and a volume exists. `LightVolumeBuffer` recreates the GPU buffer on a size change (line ~59).
- Shader: `coloredlight_tint_at` uses trilinear sampling of 8 cells, skips dark cells and fades out over 16 cells at the volume edge. `coloredlight_tinted(full,sky,tint) = full + max(full-sky,0)*(tint-1)`.
- Workflow notes: don't write Java via bash heredoc (quotes broke it); use Write/Edit. Never run two Gradle builds at once. Sodium ClassNotFound mixin warnings in the selftest log are normal. Selftest screenshots go to `run/screenshots/` (`fl-02-stained-glass.png` is the glass scene).

**Stronger tint, further range (2026-10-26, user request "make that 2,5x a little stronger, colored light visible from an even further distance"):**
- `LightVolume.BOOST` 2.5 -> 3.5.
- `LightTracker` BELOW_H/ABOVE_H 7/6 -> 9/8: compute box 288x96x288, published inner box 256 (about ±128 blocks horizontally). Vertical unchanged (BELOW_V/ABOVE_V 3/2).
- Cost: each job allocates about 8M cells (~135 MB of arrays), up from ~82 MB, and the GPU buffer is 16 MB. If it stutters or the worker lags, go back to 8/7 (±112).
- Tests, build and selftest pass (floor red/blue ratio 4.283 with colour, 1.566 without). Jar installed in the 26.3 mods folder, waiting for the in-game test.

## Full-height debug range (2026-10-26)
- LightTracker box is now `SECTIONS_H` x `SECTIONS_V` sections (defaults 12 x 26, override with
  `-Dcoloredlight.sectionsH=` / `-Dcoloredlight.sectionsV=`). The box is centred on the camera horizontally and slid
  vertically to stay within [minSection-1, maxSection+1], so 26 covers the whole 384-block overworld from any height.
- Published: 160 x 384 x 160 (+-80 blocks horizontally, full height). ~15M compute cells, ~21 B/cell → ~320 MB per job.
  Old setting was 18 x 6 (+-128 h, +-32 v). Selftest passes at -Xmx2G.

## Instant block updates + efficiency (2026-10-26)
- Placed/broken blocks now show on the next frame: `LightTracker.flush()` (called at the start of `LightFrame.update`)
  patches each changed block in place on the render thread with a small REACH=15 box, instead of waiting for the
  background full recompute. Patched volumes share `data` and carry `baseVersion` + `dirtyFrom..dirtyTo`; the
  26.3 `LightVolumeBuffer` then uploads only that byte range (`buffer.slice` + `writeToBuffer`). The full recompute
  still runs in the background and replaces the volume when done.
- `LightVolume.Workspace` reuses the light/tint/filter/crossed arrays and the bucket queue between computes (no more
  ~300 MB allocations per job). The spread loop decodes the cell once, bounds-checks only the moving axis, memoises
  the last glass scale and skips the tint-mix pass when nothing is tinted.
- Tests, build and selftest pass. Jar installed in the 26.3 mods folder.

## Perf test + icon (2026-10-26)
Run: `./gradlew runSelftest -Pfl.perf=true -q` (Fabric 26.3; 169 torches in an 81x81 brick room). All checks passed.
- fps off 303.5 / 325.8, on 299.2 / 339.7: no measurable cost.
- Full compute (worker thread): last 100 ms, average 147 ms.
- Block-change patch (render thread): 25 patches, slowest 8.25 ms; worst frame while placing torches 34.6 ms. Possible future optimisation.
- The selftest now logs worst frame per phase, compute timings (LightTracker.lastComputeMs/averageComputeMs) and patch timings (maxPatchMs/patches).
- New icon: torch on dark brick with red/green/blue glows mixing to white. Generated by a Pillow script (32px pixel art, 128 = nearest x4, 16 = Lanczos), written to assets/coloredlight/icon{16,32,128}.png.

## Stress test (2026-10-26)
Run: `./gradlew runSelftest -Pfl.stress=true`. The test builds a 121x121 field of 41 rows of mixed light sources, with stained glass over every other row and 25 light pillars 120 blocks tall: 6131 tracked lights in total. Each phase is measured off and then on, over 2 rounds:
- static
- flying (a circular path that changes height)
- churn (a whole 121-block row toggled every tick)

The test sets inactivityFpsLimit to MINIMIZED. Without that, the AFK limit caps the game at 30 fps and the measurements are useless.

Round 2 (fps / 1% low):

| Phase  | Off       | On        |
|--------|-----------|-----------|
| static | 301 / 182 | 304 / 182 |
| flying | 267 / 133 | 231 / 88  |
| churn  | 278 / 147 | 238 / 103 |

Other numbers:
- Slowest patch: 0.27 ms.
- Full compute (worker thread): about 270 ms.
- Worst frame with the mod on: 41 ms, while flying in round 1. With the mod off it was 59 ms.
