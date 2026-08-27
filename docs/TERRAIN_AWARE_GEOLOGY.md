# Terrain-aware geological deformation

GeoStrata should allow terrain shape to influence how geological structure is expressed without turning geology into a surface-following post-process or hard-coupling core to a particular terrain generator.

The architectural rule remains:

> **GeoStrata owns geology. Terrain generators own terrain shape.**

Terrain morphology is an input signal to geological deformation and exposure, not the source of geological identity.

## Decision

Do **not** generate finished geology and then move already-generated blocks to fit mountains, valleys or ridges. Do **not** make strata simply follow the final surface height.

Instead, GeoStrata should:

1. derive broad geological history from the world seed and geological province;
2. observe coarse terrain morphology through a generator-agnostic contract;
3. combine tectonic context with those observations into a bounded structural-deformation field;
4. sample lithology through that transformed geological coordinate field;
5. let the actual terrain, caves and hydrology expose the resulting bodies;
6. handle weathering, soil, scree, cut banks and other local surface responses separately after terrain exists.

The intended relationship is therefore:

```text
geological province / history
            +
coarse terrain morphology
            |
            v
structural deformation field
            |
            v
lithology / body ownership
            |
            v
terrain + caves + hydrology expose geology
            |
            v
surface weathering / sediment / palette integrations
```

A mountain can strengthen or spatially focus deformation in an orogenic context, but a mountain must not automatically imply tectonics. Likewise, strongly deformed geology may exist beneath relatively subdued terrain.

## Why this is not a terrain post-process

A literal "terrain generated a mountain, now bend the blocks under it" pass would create several problems:

- neighboring chunks may not exist when one chunk asks about broad terrain shape;
- forced neighbor generation would be expensive and can create generation-order coupling;
- rewriting generated blocks risks fighting caves, ores, structures and other worldgen passes;
- strata would tend to shrink-wrap hills and valleys instead of representing coherent geological structure;
- integration would become dependent on the internals and ordering of specific terrain mods.

GeoStrata's province sampler and correlated fields are intentionally deterministic from seed and coordinates. Terrain-aware deformation should preserve that property wherever it affects structural ownership.

## Structural response versus surface response

Terrain awareness is split into two different systems.

### Structural terrain response

Structural response operates at low spatial frequency and may influence:

- regional dip strength;
- fold amplitude and orientation;
- uplift expression;
- fault displacement or fault-block expression;
- where strongly deformed structures are most visible.

It should use coarse, deterministic terrain observations over tens to hundreds of blocks rather than reacting to individual blocks. `TerrainMorphologySample` summarizes center elevation, X/Z gradient, local relief and ridge/valley prominence from caller-supplied height observations. It contains no Minecraft classes and does not decide how those heights are obtained.

`ChunkGeneratorTerrainMorphologySampler` is the standalone Minecraft adapter. It queries the active `ChunkGenerator` for raw `OCEAN_FLOOR_WG` height at the center plus four cardinal observations using a default spacing of 128 blocks. Those raw generator queries do not require neighboring chunks to be loaded, so the diagnostic remains independent of exploration/generation order. Using ocean-floor worldgen height retains broad submarine relief instead of flattening every ocean observation to sea level.

Because the adapter talks only to Minecraft's active generator, ordinary noise-setting/datapack changes and terrain mods that participate through that generator can influence the same GeoStrata morphology contract without their APIs leaking into geological mathematics. Optional integrations may still provide richer signals when a terrain generator exposes useful deterministic fields that generic heights cannot represent.

`/geostrata terrain` reports the current raw generator height, relief, slope magnitude and X/Z gradient, prominence, and spacing. It is read-only and does not currently alter lithology ownership or generated blocks.

### Surface and exposure response

Surface response may inspect generated terrain much more directly because it does not own the underlying structural history. It may influence:

- exposed rock faces;
- weathered variants;
- scree and talus;
- soil thickness;
- river cut-bank materials;
- mud, clay and sediment deposition;
- optional visual palettes such as Conquest Reforged.

This layer can be more local because a cliff or riverbank is an expression of already-established geology rather than a cause of that geology.

## Coordinate deformation rather than block relocation

Structural geology should normally transform sampling coordinates rather than move blocks after generation.

The existing `SedimentaryStratigraphicField` already follows this model: dip and warp contribute a vertical offset before the normalized succession is sampled. Future deformation should extend the same principle.

Conceptually:

```text
geologicalY = worldY
            - regionalDip(x, z)
            - foldOffset(x, z)
            - faultDisplacement(x, z)
            - terrainResponse(x, z)
            - localWarp(x, z)
```

The exact sign and representation are implementation details; the important contract is that a coordinate is transformed and then geological ownership is queried. This preserves cross-chunk continuity and avoids a destructive block-moving phase.

Some later structures may require a full XYZ transform rather than a vertical-only offset. The public architecture should not assume that all deformation can forever be represented as `Y + offset`.

## Province meaning

Terrain response must be modulated by geological context rather than used directly as geology.

Examples:

- **sedimentary basin** — generally broad, shallow deformation; major terrain relief may modestly strengthen exposure or basin-margin dip;
- **cratonic shield** — old structural fabrics and basement exposure, but terrain relief should not manufacture young folding;
- **orogenic belt** — high potential for steep dip, folding, faulting and metamorphic banding; major ridges may strengthen the expression of that deformation;
- **rift province** — tilted fault blocks and displacement are more appropriate than simply increasing sedimentary dip everywhere;
- **volcanic arc** — terrain response may help place/expose volcanic and intrusive architecture, but sedimentary folding is not the universal response.

The long-term model should therefore resemble:

```text
structural response = geological deformation potential
                    * terrain expression
                    * deterministic regional variation
```

rather than:

```text
structural response = terrain shape
```

## Compatibility contract

Terrain-aware geology follows the existing compatibility tiers.

### Tier 0 — standalone

Core consumes generic observations available from ordinary Minecraft/Fabric worldgen. No terrain mod is required.

### Tier 1 — data-driven classification

Datapacks may extend GeoStrata biome and replacement tags or later structural-response tags without adding Java dependencies.

### Tier 2 — optional guarded terrain adapter

If a terrain mod exposes useful deterministic fields that generic height observations cannot represent, a guarded adapter may translate them into GeoStrata's own morphology/deformation contract. External APIs must not leak into core geological classes.

### Tier 3 — separate compatibility artifact

Deep integration that requires compile-time APIs, large data sets or terrain-mod-specific resources should live outside the standalone jar when practical.

## Current implementation boundary

`TerrainMorphologySample` and `ChunkGeneratorTerrainMorphologySampler` are staging/diagnostic infrastructure. The sampler observes the active generator but does not alter `SedimentaryStratigraphicField`, correlated experiment ownership, feature registration or current chunk output.

That keeps the current correlated experiment reproducible while establishing a tested, live vocabulary for terrain-aware work.

The morphology signal is intentionally not cached or consumed from production worldgen yet. Five raw height queries are appropriate for an explicit diagnostic command; a future runtime consumer must define a coarse deterministic field/cache strategy before sampling this signal at worldgen scale.

## Implementation sequence

1. **complete** — record the terrain-aware deformation architecture and compatibility rule;
2. **complete** — introduce a pure morphology sample with regression tests;
3. **complete** — add a deterministic `ChunkGenerator` morphology sampler using coarse spacing and no forced neighbor generation;
4. **complete** — expose morphology through read-only `/geostrata terrain` diagnostics;
5. define data-driven province deformation profiles and a pure deformation-response function;
6. compose that response with the stratigraphic field in diagnostics only;
7. add explicit fold and fault transforms with fixed regression vectors;
8. evaluate terrain generators and fresh worlds before granting the deformation field runtime block ownership;
9. keep local weathering/exposure behavior as a later, separate surface stage.

The next engineering step is therefore a data-driven province deformation contract and pure response calculation. It should turn province context plus `TerrainMorphologySample` into bounded structural parameters without yet changing block generation.
