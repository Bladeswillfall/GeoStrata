# GeoStrata

GeoStrata is a Fabric 1.20.1 geology mod built around one rule: **the base mod must be useful and stable on ordinary Minecraft, while optional integrations may make it richer when other mods are installed.**

The current pre-alpha provides a standalone rock/soil catalog and data-driven overworld placement. The long-term goal is a geology layer that other terrain, structure, building and content mods can extend without GeoStrata becoming hard-coupled to any one modpack.

## Compatibility contract

GeoStrata core depends only on:

- Minecraft 1.20.1
- Fabric Loader
- Fabric API
- Java 17 at runtime

Conquest Reforged, terrain generators, structure mods and other content mods are **optional integrations**, not core dependencies.

Compatibility should be added in this order:

1. data tags and datapack extension points;
2. optional resources/data that activate only when useful;
3. guarded Java integration when a data-only bridge cannot express the feature;
4. separate compatibility artifacts when an integration would otherwise make core depend on another mod.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the rules maintainers should preserve.

## Current content

GeoStrata currently registers rock families including limestone, chalk, shale, slate, mudstone, siltstone, marble, quartzite, schist, gneiss, basalt, rhyolite, conglomerate and breccia, plus several soil/clay materials.

World generation uses GeoStrata-owned biome tags such as `geostrata:has_mountain_rocks` and `geostrata:has_river_soils`. Modpacks can extend those tags to teach GeoStrata about modded biomes without changing Java code.

## Build

GeoStrata targets Java 17 bytecode for Minecraft 1.20.1. The build itself uses Fabric Loom 1.16 and Gradle 9.4, which run under Java 21 in CI.

```text
gradle clean build
```

Built jars are written to `build/libs/`.

## Project status

**Pre-alpha.** Existing worlds should be treated as disposable while worldgen rules and registry contracts are still being stabilized.

The first milestone is a clean, reproducible standalone core. Compatibility packs and deeper geological systems should build on top of that baseline rather than bypassing it.
