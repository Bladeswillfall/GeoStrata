# GeoStrata architecture

## Product boundary

GeoStrata is a standalone Minecraft geology mod first and a modpack component second.

The **mod** should support Fabric, Forge and NeoForge through loader adapters around one shared geology/worldgen/gameplay implementation. The **modpack** is a separate product and may stay on whichever loader best serves that pack; its dependency choices must not dictate the core mod architecture.

The current shipping/development artifact is still Fabric 1.20.1. That is an implementation state, not a rule that shared GeoStrata logic may depend on Fabric.

## Multi-loader boundary

Use this direction of dependency:

```text
shared geology / worldgen / gameplay / data
                  ↓
       loader-owned lifecycle seams
          ↓          ↓          ↓
       Fabric      Forge     NeoForge
```

Shared code owns:

- deterministic geology, province, stratigraphy, metamorphism and ore-deposit algorithms;
- Minecraft-facing block/feature/placement **definitions** and gameplay behavior where the same Minecraft concept exists on every loader;
- server-data geology contracts, configured/placed-feature data, tags, loot, recipes and assets;
- compatibility semantics such as lithology, host rock, ore material, grade and deposit identity.

Loader adapters own:

- mod entrypoints and lifecycle/event subscriptions;
- registry **timing** and loader-specific registration APIs;
- biome/worldgen attachment APIs;
- resource-reload event hookup and installed-mod detection;
- creative-tab hooks and other loader-specific UI registration;
- loader-specific integrations with third-party mods.

A shared class may define a block, feature or placement modifier, but it should not decide *when* Fabric/Forge/NeoForge registers it. That distinction is what lets the same object model feed different loader lifecycles.

The current Fabric adapter lives under `com.geostrata.platform.fabric`. `build.gradle` runs `validateLoaderBoundaries` so Fabric, Forge or NeoForge APIs cannot leak back into the shared block/geology/worldgen packages.

Diagnostic command registration is still Fabric-facing in the current pre-alpha source layout. The command bodies are development tooling rather than core survival gameplay; move their event hookup behind the same platform boundary when the first non-Fabric build requires it rather than creating an abstraction with no consumer today.

### Mapping boundary

The current Fabric build uses Yarn mappings. Forge/NeoForge 1.20.1 normally use Mojang-named sources, so **shared Minecraft-facing source is not yet ready to compile unchanged on all three loaders**.

Do not solve this by copying geology/worldgen/gameplay classes into three source trees. Before the first Forge/NeoForge build is added, choose one common source namespace—preferably official Mojang mappings where the build toolchain permits it, or an equivalent single remapped common namespace—and migrate the shared Minecraft-facing source once.

That mapping migration is intentionally separate from the present lifecycle-boundary refactor: adding empty Forge/NeoForge Gradle modules before they can compile shared source would be scaffolding without value.

## Compatibility tiers

### Tier 0 — standalone core

Core blocks, items, recipes, loot, tags and baseline worldgen. Each published loader artifact must provide the same GeoStrata geology/gameplay contract without requiring the curated modpack or unrelated content mods.

### Tier 1 — data-driven interop

Prefer Minecraft data contracts before Java integration. Examples:

- GeoStrata biome tags select where geological features may generate.
- Block/item tags expose material families to recipes and other mods.
- Replacement/placement tags let packs add modded terrain blocks.

A datapack or another mod should be able to extend these tags without recompiling GeoStrata.

### Tier 2 — optional guarded integration

Use Java only when the external mod exposes behavior that tags/data cannot model. Every optional adapter must:

- be guarded by the relevant loader's installed-mod check before external classes are referenced;
- fail closed when the external mod is absent;
- leave Tier 0 behavior unchanged;
- live in an integration/platform-specific package rather than leaking external APIs through shared classes.

A loader-specific target is valid. For example, a Geolosys bridge may exist on a loader where Geolosys is available while Fabric remains unaffected. The bridge should read GeoStrata's deposit semantics rather than introduce a second geological model.

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

Over time, feature placement should evolve from independent deposits toward a coherent geology model: province/lithology -> structure/stratigraphy -> weathering/surface expression -> resources. The data format and deterministic geology model should remain shared across loaders.

## Dependency rule

A proposed shared/core dependency must answer **yes** to both questions:

1. Is GeoStrata fundamentally broken or meaningless without it?
2. Is there no reasonable vanilla API, data/tag contract, loader adapter or compatibility-artifact alternative?

If either answer is no, the dependency does not belong in shared core.

## Compatibility examples

### Vanilla-only

GeoStrata uses vanilla biome tags as the default contents of its own biome-selection tags. No integration is required.

### Modded biome/terrain pack

The pack extends GeoStrata biome/replacement tags with its biomes and terrain blocks. Shared GeoStrata Java code stays unchanged.

### Geolosys prospecting

GeoStrata remains the geological/deposit authority and natural cave/outcrop exposure remains the core discovery path. A loader-specific Geolosys integration may expose samples or prospecting clues for real GeoStrata deposits without enabling duplicate Geolosys deposit generation.

### Conquest Reforged

Shared GeoStrata remains unchanged. A Conquest bridge may add palette substitutions, structures, decorative geology and additional tagged replacement blocks when Conquest is present.

## Repository rule

Minecraft launcher state, caches, worlds, downloaded jars and local instance files are not source code. They must not be committed to the mod repository.

Pack manifests/configuration that are intentionally maintained may live under the existing explicit `pack/` directory or a separate repository, but pack code/configuration is not part of the shared multi-loader architecture. Changes intended to make the **mod** portable should not require changes to the development modpack.
