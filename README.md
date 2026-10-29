# Colored Light

Colored block light for Minecraft. Torches glow warm, soul fire glows blue, redstone glows red, and light that shines
through stained glass picks up the color of the glass, on the ground, the walls and everything nearby.

Colored Light is a small client-side mod for Minecraft on Fabric, NeoForge and Forge, compatible with Mod Menu on Fabric.

Supported versions: Fabric 1.18.2 and 1.19 to 26.3, NeoForge 1.21 to 26.3, Forge 1.16.5, 1.18.2, 1.19.2 and 1.20.1.
Works with Sodium and Embeddium.

## Features

- **Every light its own color** - torches, lanterns, campfires and fire glow warm orange, soul torches and soul fire
  glow blue, redstone glows red, sea lanterns and beacons glow cool white, and so on.
- **Stained glass tints light** - a light behind stained glass colors the light that passes through it, so a lamp behind
  red glass lights the room red.
- **Colors mix** - where two colored lights overlap, their colors blend.
- **Adjustable strength** - from a faint hint of color to fully saturated, and stained glass tinting can be turned off on
  its own.
- **Built to stay fast** - the color is added in the terrain shader, so no chunks are ever rebuilt.
- **Client-side only** - nothing to install on a server.

## Install

**Fabric**

1. Install [Fabric Loader](https://fabricmc.net/use/) for your version of Minecraft.
2. Put the Fabric Colored Light jar for your version in your `mods` folder. Fabric API is not needed.
3. Start the game.

Optional: add [Mod Menu](https://modrinth.com/mod/modmenu) to get a settings screen (*Mods > Colored Light > Configure*).

**NeoForge**

1. Install NeoForge for your version of Minecraft.
2. Put the NeoForge Colored Light jar in your `mods` folder.
3. Start the game. Settings are under *Mods > Colored Light > Config*.

**Forge (1.16.5, 1.18.2, 1.19.2, 1.20.1)**

1. Install Forge for your version of Minecraft.
2. Put the Forge Colored Light jar in your `mods` folder.
3. Start the game. Settings are under *Mods > Colored Light > Config*.

## Notes

- With an Iris shader pack turned on, Colored Light tints the pack's terrain as well (tested with Iris on 26.3; older Iris versions without `ShaderCreator` show the pack's own lighting)
  again.
- Color is worked out from the nearest lights around the camera, not traced block by block, so a little color can leak
  around corners next to stained glass.
- On Forge 1.20.1 with Embeddium, Embeddium writes a line to the log saying a mod changed its internals. That is
  Colored Light adding its color to Embeddium's terrain shader; it is harmless.

## Building

`./gradlew build -Pminecraft_version=26.3` builds the Fabric jar; add `-Ploader=neoforge` or `-Ploader=forge` for the
other loaders (version configs in `versions/`). Forge 1.16.5 builds from `legacy-forge/`:
`cd legacy-forge && ./gradlew build`. `./gradlew runSelftest` launches the game and runs the in-world checks.

## License

MIT
