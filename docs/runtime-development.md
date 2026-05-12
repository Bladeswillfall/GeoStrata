# Runtime Development Notes

GeoStrata runtime development uses the normal Fabric Loom source path.

## Build baseline

- Minecraft: 1.20.1
- Java: 17
- Fabric Loader: 0.15.11+
- Fabric API: 0.92.2+1.20.1
- Fabric Loom: 1.16-SNAPSHOT
- CI Gradle: 9.4.0

The uploaded `fabric-loom-1.16` reference was used as the build-system direction. Do not vendor Fabric Loom source into this repository; consume Loom through the Gradle plugin instead.

## Source layout

- Runtime blocks are registered in `src/main/java/com/geostrata/block/GeoStrataBlocks.java`.
- Creative-tab registration lives in `src/main/java/com/geostrata/item/GeoStrataItemGroups.java`.
- Placeholder textures live in `src/main/resources/assets/geostrata/textures/block`.
- Blockstates, models, loot tables, and tags live under the standard `src/main/resources` Fabric mod layout.

## Development rule

Do not maintain the reflective bootstrap jar path except as an emergency smoke-test artifact. The repository and GitHub Actions build should be the source of truth for release jars.

Keep Conquest Reforged optional. Base GeoStrata block IDs must exist without CR installed, and CR should only enhance visuals, palettes, recipes, or optional runtime behavior later.
