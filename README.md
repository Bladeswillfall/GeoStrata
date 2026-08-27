# GeoStrata

GeoStrata is a Minecraft geology mod for Fabric.

The idea is fairly simple: make the ground feel like it came from somewhere. Different places should have different kinds of stone, soil, clay, and rock, rather than the world being made from the same handful of blocks everywhere.

It is still early days, so this is not a finished geology overhaul. The current version gives you a growing collection of rocks and soils, along with some basic world generation to place them in the overworld. The long-term plan is to build something that terrain mods, structure mods, building mods, and modpacks can all make use of without GeoStrata becoming tied to one particular setup.

GeoStrata should also be worthwhile on its own. You should not need a huge modpack, a special terrain generator, or a particular collection of building mods for it to do anything useful.

## What is included?

The current pre-alpha includes rock families such as:

- Limestone and chalk.
- Shale, slate, mudstone, and siltstone.
- Marble, quartzite, schist, and gneiss.
- Basalt and rhyolite.
- Conglomerate and breccia.
- Several types of soil and clay.

At the moment, the world generation is fairly basic. It uses ore-style placements as a starting point while the larger geological systems are being worked out. The eventual aim is to create more convincing geological areas, with related rocks appearing together in sensible places.

The mod also leaves room for other mods to join in. A modpack can add its own biomes, terrain blocks, or geological rules without GeoStrata needing to be rewritten for every individual mod.

## Where things live

- `src/` — the main GeoStrata source code and resources.
- `pack/` — the development and integration modpack. This is not included in the core mod jar.
- `compat/` — optional compatibility projects and add-ons.
- `scripts/` — scripts used for checking and supporting the build.

Minecraft launcher files, downloaded jars, caches, and personal world files should stay out of the repository.

## Technical details

GeoStrata core currently depends on:

- Minecraft 1.20.1.
- Fabric Loader.
- Fabric API.
- Java 17 at runtime.

Other mods are optional. This includes Conquest Reforged, terrain generators, structure mods, and other content mods.

Compatibility is intended to be added gradually:

1. Through tags and datapacks where possible.
2. Through optional resources that only do something when they are useful.
3. Through carefully guarded Java integrations where data alone is not enough.
4. Through separate compatibility projects when adding an integration directly would make the core mod depend on another mod.

The main extension points include GeoStrata biome tags such as:

```text
geostrata:has_mountain_rocks
geostrata:has_river_soils
```

There are also replacement tags for blocks that can be used as the base stone during world generation:

```text
geostrata:worldgen/base_stone_replaceables
```

The meaning of each rock is recorded in:

```text
data/geostrata/geology/lithologies.json
```

The material profiles for rocks, soils, mud, and clay are recorded in:

```text
data/geostrata/materials/material_profiles.json
```

These files describe things such as breaking behaviour, texture sets, semantic tags, and compatibility roles. Automated checks compare the catalogues with the actual mod implementation.

## Building

GeoStrata is built for Java 17 bytecode and Minecraft 1.20.1.

The build uses Fabric Loom 1.16.3 and Gradle 9.4. The CI build runs under Java 21.

```text
gradle clean build
```

Built jars are placed in:

```text
build/libs/
```

## Project status

**Pre-alpha.**

World generation and registry details are still changing, so existing worlds should be treated as disposable for now. Things may move around, get renamed, or change shape as the foundations are improved.

The first goal is a clean and dependable standalone mod. Optional compatibility packs and more ambitious geological features can then be built on top of that foundation.
