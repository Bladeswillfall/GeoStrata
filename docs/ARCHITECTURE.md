# GeoStrata architecture

## Product boundary

GeoStrata is a standalone Minecraft geology mod first and a modpack component second.

The core jar must launch and provide its baseline gameplay on Minecraft 1.20.1 with Fabric API only. A player should not need Conquest Reforged, Terralith, Tectonic or any other content/worldgen mod to satisfy GeoStrata registries or data loading.

The curated modpack may depend on more mods. **The GeoStrata core may not inherit those dependencies merely because the development instance contains them.**

## Compatibility tiers

### Tier 0 — standalone core

Core blocks, items, recipes, loot, tags and baseline worldgen. This is the compatibility floor and must remain testable by itself.

### Tier 1 — data-driven interop

Prefer Minecraft/Fabric data contracts before Java integration. Examples:

- GeoStrata biome tags select where geological features may generate.
- Block/item tags expose material families to recipes and other mods.
- Replacement/placement tags should be used where a modpack may need to add modded terrain blocks.

A datapack or another mod should be able to extend these tags without recompiling GeoStrata.

### Tier 2 — optional guarded integration

Use Java only when the external mod exposes behavior that tags/data cannot model. Every optional adapter must:

- be guarded by an installed-mod check before external classes are referenced;
- fail closed when the external mod is absent;
- leave the Tier 0 behavior unchanged;
- live in an integration-specific package rather than leaking external APIs through core classes.

### Tier 3 — separate compatibility content

If an integration requires many external block IDs, textures, structures, recipes or a compile dependency, ship it as a separate compatibility artifact/datapack when practical.

This is the preferred home for deep Conquest Reforged support. Conquest can enrich GeoStrata with additional palettes, geological set dressing, structures or substitutions without becoming a hard dependency of the base jar.

## Worldgen ownership

GeoStrata owns geology. Terrain generators own terrain shape unless an explicit integration says otherwise.

Baseline GeoStrata generation should therefore:

1. select biomes through extensible GeoStrata tags;
2. place geological material through configured/placed features;
3. avoid assuming a specific third-party biome set;
4. expose replacement/placement extension points before adding third-party IDs to core data;
5. keep feature density conservative enough that vanilla terrain remains recognizable and other worldgen mods retain room to operate.

Over time, feature placement should evolve from independent deposits toward a coherent geology model: province/lithology -> structure/stratigraphy -> weathering/surface expression -> resources. The data format should remain the integration seam even as the simulation becomes richer.

## Dependency rule

A proposed core dependency must answer **yes** to both questions:

1. Is GeoStrata fundamentally broken or meaningless without it?
2. Is there no reasonable tag, datapack, optional-adapter or compatibility-artifact alternative?

If either answer is no, the dependency does not belong in core.

## Compatibility examples

### Vanilla-only

GeoStrata uses vanilla biome tags as the default contents of its own biome-selection tags. No integration is required.

### Modded biome/terrain pack

The pack extends GeoStrata biome/replacement tags with its biomes and terrain blocks. GeoStrata Java code stays unchanged.

### Conquest Reforged

Core GeoStrata remains unchanged. A Conquest bridge may add palette substitutions, structures, decorative geology and additional tagged replacement blocks when Conquest is present.

## Repository rule

Minecraft launcher state, caches, worlds, downloaded jars and local instance files are not source code. They must not be committed to the mod repository. Pack manifests/configuration that are intentionally maintained should live under an explicit pack directory or separate repository rather than being mixed into the core source tree.
