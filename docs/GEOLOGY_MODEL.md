# GeoStrata geology model

GeoStrata is moving from a collection of themed stone features toward a world-level geology system. This document defines the first live contract for that transition.

## Current runtime versus geological intent

The current 1.20.1 pre-alpha still generates most rock types with ordinary Minecraft configured/placed ore features. Those features are a compatibility baseline, not the intended final geology model.

Limestone is the first migration pilot. Its configured feature now uses GeoStrata's `strata_lens` feature type, which produces a broad tapered bed/lens with gentle tilt and local warping while preserving the same `geostrata:worldgen/base_stone_replaceables` target contract. The remaining rock types stay on the ore-style baseline until the pilot is validated in real worlds.

`data/geostrata/geology/lithologies.json` records the semantic meaning of every live GeoStrata rock independently of the current generator. The file remains marked `runtimeStatus: metadata_only` until generation logic begins consuming the catalog directly.

That separation is important: we can make the geology model richer without silently changing every existing feature at once, and compatibility packs can reason about lithologies without depending on one terrain generator.

## Regional province sampling

GeoStrata has a deterministic province sampler that assigns broad regional context without changing chunk output yet. Province sites are jittered inside a 768-block grid and nearest-site ownership creates irregular Voronoi-style boundaries. Five archetypes currently exist: sedimentary basin, cratonic shield, orogenic belt, volcanic arc and rift province.

The sampler is a pure function of world seed and block X/Z. It stores no mutable world state, does not depend on chunk generation order and is covered by regression vectors. This is an important compatibility constraint: pregeneration, multiplayer and revisiting a partially explored world must all agree on province boundaries.

The sampler also tracks the second-nearest site and computes the exact local distance to the bisector between the two sites. Future runtime generation can therefore blend neighboring province profiles across a transition zone instead of producing visible hard borders. `/geostrata province` reports the current province, neighboring province and approximate distance to that boundary.

## Province profiles

`data/geostrata/geology/province_profiles.json` defines relative suitability for every live lithology in every province. The profile remains generation-neutral at this stage, but it is now loaded through Fabric's server-data resource manager and can be overridden by a datapack at the same `geostrata:geology/province_profiles.json` resource ID.

The runtime loader also reads the lithology catalog and validates that each profile covers the current catalog exactly. A malformed or incomplete datapack override fails resource reload instead of silently changing the geology model.

Weights are preferences, not permissions. Every province/lithology pair has a positive weight, so a low value means uncommon rather than impossible. The default profile declares a 192-block transition width. Effective weights blend the primary and neighboring profile as the sampler approaches a boundary rather than abruptly switching probabilities on the Voronoi line.

Use `/geostrata profile` to inspect the strongest effective lithologies and current primary/neighbor blend at the command source's location. This is diagnostic only in this stage; the next narrow runtime migration can make the limestone pilot consume its effective suitability weight.

## Lithology fields

Each core rock defines:

- `rockClass` — sedimentary, igneous or metamorphic; this must agree with the corresponding block tag.
- `genesis` — a more specific formation/process description such as carbonate, mudrock, mafic extrusive or high-grade banded metamorphic rock.
- `bodyStyle` — the geometry the future generator should aim for, such as beds, bands, sheets, channels or basement massifs.
- `depthAffinity` — a qualitative tendency rather than an absolute Y range.
- `continuity` — whether the intended body should generally be local or regionally persistent.
- `biomeTag` — the current GeoStrata-owned biome affinity used by baseline generation.
- `baselineFeature` — the configured/placed feature currently representing this lithology, whether it uses a vanilla or GeoStrata feature type.

The qualitative fields are intentional. GeoStrata should not encode "gneiss lives below Y=-32" as a universal truth when a terrain mod may radically alter relief, sea level or crust exposure.

## Direction of travel

The intended generator architecture is:

1. choose a broad geological province from world seed and low-frequency fields;
2. blend its lithological suitability with the neighboring province near regional boundaries;
3. assign a lithological succession/body family within that regional context;
4. construct coherent beds, bands, intrusive/volcanic bodies and contacts across chunk boundaries;
5. expose those bodies through terrain, caves, erosion and hydrology;
6. place ores/minerals as consequences of host lithology and geological process;
7. let optional integrations add valid host blocks, biome mappings, surface palettes and structures without redefining the core geology.

This is deliberately different from running fourteen independent ore generators. Feature migrations should be incremental: one geological body family is introduced, built, profiled and observed before the next family replaces its baseline implementation.

## Compatibility

Third-party mods should not edit the core lithology catalog to add their own blocks. Pack authors may override province suitability weights when building a distinct worldgen profile, but third-party block/biome compatibility should normally remain in GeoStrata's semantic extension tags rather than introducing registry IDs into core Java.

For example, a terrain mod can add its natural stone to `geostrata:worldgen/base_stone_replaceables`; a Conquest integration can add suitable surface/building palettes without making Conquest a dependency of the core jar.

See `docs/COMPATIBILITY.md` for the existing data extension points.

## Validation

Run:

```text
python3 scripts/validate_geology_catalog.py
python3 scripts/validate_province_profiles.py
```

The catalog validator enforces that every live rock appears exactly once in the catalog and exactly once in a rock-class tag, that referenced biome tags exist, and that each baseline configured/placed feature actually generates the catalogued block. The province validator enforces exact coverage of all five provinces and every live lithology, positive bounded weights, a valid blend width, and at least one characteristic regional context for every rock. CI runs both before the Gradle build. Province sampling and profile blending also have JUnit regression coverage so the world-seed-to-province mapping and blend behavior cannot drift accidentally.
