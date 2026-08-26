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

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design rules, [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) for the concrete data extension points, and [`docs/GEOLOGY_MODEL.md`](docs/GEOLOGY_MODEL.md) for the live lithology contract and generator direction.

## Repository layout

- `src/` — authoritative GeoStrata mod source and resources.
- `pack/` — curated development/integration modpack source; it is not part of the core jar.
- `compat/` — integration-specific reference data and, over time, optional compatibility artifacts.
- `scripts/` — validation/build-support tooling.
- `archive/` and `docs/archive/` — superseded prototypes/design snapshots retained for historical reference only.

Minecraft launcher state, downloaded/processed jars, caches and player-local files do not belong in source control.

## Current content

GeoStrata currently registers rock families including limestone, chalk, shale, slate, mudstone, siltstone, marble, quartzite, schist, gneiss, basalt, rhyolite, conglomerate and breccia, plus several soil/clay materials.

World generation uses GeoStrata-owned biome tags such as `geostrata:has_mountain_rocks` and `geostrata:has_river_soils`, plus replacement tags such as `geostrata:worldgen/base_stone_replaceables`. Modpacks can extend those tags to teach GeoStrata about modded biomes and terrain blocks without changing Java code.

The semantic meaning of each live rock is recorded in `data/geostrata/geology/lithologies.json`. The current ore-style placements remain a baseline implementation while coherent geological bodies are developed.

## Build

GeoStrata targets Java 17 bytecode for Minecraft 1.20.1. The build itself uses Fabric Loom 1.16.3 and Gradle 9.4, which run under Java 21 in CI.

```text
gradle clean build
```

Built jars are written to `build/libs/`.

## Project status

**Pre-alpha.** Existing worlds should be treated as disposable while worldgen rules and registry contracts are still being stabilized.

The first milestone is a clean, reproducible standalone core. Compatibility packs and deeper geological systems should build on top of that baseline rather than bypassing it.
