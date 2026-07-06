# Atlas Audit

Hytale server plugin that reports the predicted size of each client texture atlas built from the
loaded mods, and flags the ones that would exceed a GPU's texture-size limit.

## Why

The client packs textures into a handful of fixed-width atlases that grow in height as more textures
are added. A GPU can only hold a texture up to its `GL_MAX_TEXTURE_SIZE`. On minimum supported
hardware that is 8192. When an atlas grows past that limit the whole atlas fails to upload and every
texture in it renders black, including vanilla ones. GPUs that report 16384 have more room before reaching
the limit, which is why the failure is hardware-specific.

This plugin reproduces that packing on the server, where the pack that provided each asset is known,
so you can see which atlas is over the limit and which packs fill it.

## Audited atlases

- `Model/Entity`, `Custom UI`, `World Map`: membership is a plain asset-path filter, so these are
  exact.
- `Blocks`, `Item Icons`, `FX/Particles`: discovered from block, item, particle and trail assets.
  Block and FX predicted sizes are estimates, since the client packs those atlases with a different
  algorithm.

Each texture is attributed to the pack that provides it, and each pack line shows its texture count
and total footprint. Fluid and block-overlay textures are omitted. Those atlases are small and never
approach the limit.

## Output

- On boot: writes the full per-atlas report to `atlas-audit-report.txt` in the server working
  directory, and logs a one-line summary (at `WARNING` if any atlas would overflow the 8192
  min-spec limit).
- `/atlasaudit` (admin): prints the report and rewrites the file on demand, without a restart.

## Example

Output from a vanilla server with no extra mods. A mod that pushes an atlas past 8192 flips its
status to `OVERFLOW on min-spec GPUs`, and the per-pack breakdown shows which pack to trim.

<details>
<summary>Example <code>atlas-audit-report.txt</code> (vanilla 0.6.0-pre.6, largest lists trimmed)</summary>

```text
=== Texture atlas audit ===
Limit: 8192 (min-spec), 16384 (high-end)

Model/Entity    2079 tex    8192 x 8192    OK
    packs:
      Hytale:Hytale                    2079 tex      32.29 MPx
    largest:
      768x512  NPC/Elemental/Dragon_Frost/Models/Texture.png [Hytale:Hytale]
      704x544  NPC/Elemental/Dragon_Void/Models/Texture.png [Hytale:Hytale]
      672x512  NPC/Elemental/Dragon_Fire/Models/Texture.png [Hytale:Hytale]
      608x544  NPC/Swimming_Wildlife/Whale_Humpback/Models/Texture.png [Hytale:Hytale]
      512x512  NPC/Intelligent/Goblin_Duke/Models/Model_Textures/Vapid.png [Hytale:Hytale]

Blocks          2285 tex    8192 x 4096    OK (estimate)
    packs:
      Hytale:Hytale                    2285 tex      14.76 MPx
    largest:
      256x256  Blocks/Miscellaneous/ArcadeMachine_Texture.png [Hytale:Hytale]
      160x352  Blocks/Structures/Roofs/Metal_Roofs/Slate_Roof.png [Hytale:Hytale]
      160x352  Blocks/Structures/Roofs/Metal_Roofs/Metal_Bronze.png [Hytale:Hytale]

Item Icons      3447 tex    2048 x 8192    OK
    packs:
      Hytale:Hytale                    3447 tex      14.12 MPx
    largest:
      64x64  Icons/ItemsGenerated/Wood_Aspen_Branch_Corner.png [Hytale:Hytale]
      64x64  Icons/ItemsGenerated/Furniture_Jungle_Brazier.png [Hytale:Hytale]

FX/Particles     360 tex    8192 x 4096    OK (estimate)
    packs:
      Hytale:Hytale                     360 tex      14.12 MPx
    largest:
      1024x1024  Particles/Textures/Smoke/Smoke_Tall.png [Hytale:Hytale]
      768x768  Particles/Textures/Rnd/Fire_Trail.png [Hytale:Hytale]

Custom UI        187 tex    8192 x 4096    OK
    packs:
      Hytale:Hytale                     187 tex      17.20 MPx
    largest:
      3840x1074  UI/Custom/Pages/Memories/MemoriesUnlocked/BackgroundModal@2x.png [Hytale:Hytale]
      2110x1169  UI/Custom/Pages/Memories/MemoriesRunes@2x.png [Hytale:Hytale]

World Map         21 tex    2048 x 256     OK
    packs:
      Hytale:Hytale                      21 tex       0.06 MPx
    largest:
      500x21  UI/WorldMap/CompassBackground.png [Hytale:Hytale]
      64x64  UI/WorldMap/MapMarkers/Coordinate.png [Hytale:Hytale]
```

</details>

## Development

```sh
./setup.sh          # Run once: links Assets.zip from launcher install
./run-server.sh     # Build and start the server with the plugin
```

### Build

```sh
./mvnw package
```

The jar is written to `target/AtlasAudit-<version>.jar`. Drop it in the server's `mods/` directory.
