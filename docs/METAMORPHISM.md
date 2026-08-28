# Metamorphic intensity staging

GeoStrata now has a pure deterministic metamorphic-intensity field for staging the future slate → schist → gneiss migration. It does not place or replace blocks yet.

The field answers a deliberately narrow question: **how strongly has this broad area been metamorphosed?** It does not try to decide every metamorphic rock from intensity alone.

## Regional history first

`MetamorphicIntensityField` starts from the existing geological province sampler rather than creating a second regional partition system. The current baseline intensities are:

| Province | Baseline intensity |
| --- | ---: |
| Sedimentary basin | 0.12 |
| Rift province | 0.32 |
| Cratonic shield | 0.52 |
| Volcanic arc | 0.55 |
| Orogenic belt | 0.72 |

These are model weights, not literal temperature/pressure measurements. They encode the broad gameplay/geological expectation that ordinary sedimentary basins are usually low grade, old shields commonly expose metamorphic basement, and mountain-building belts are the strongest setting for regional metamorphism.

Province boundaries use the same soft blend behavior as the wider geology model. The field therefore changes gradually near neighboring geological regions rather than stepping instantly from one grade to another.

## Smooth regional variation

A province is not uniformly metamorphosed. The field adds seed-derived low-frequency variation on a 384-block lattice and smooth-interpolates between lattice corners. This produces broad zones hundreds of blocks across without ore-like blobs or chunk-boundary seams.

The maximum regional variation depends on province:

| Province | Variation amplitude |
| --- | ---: |
| Sedimentary basin | ±0.08 |
| Rift province | ±0.12 |
| Cratonic shield | ±0.15 |
| Volcanic arc | ±0.14 |
| Orogenic belt | ±0.18 |

The hash uses `GeologyDeterminism`, so the same world seed and coordinates always produce the same field and do not consume Minecraft feature RNG state.

## Terrain is evidence, not history

The sampler can optionally accept `TerrainMorphologySample`. Relief and ridge/valley prominence then make a small adjustment to the regional value.

The maximum coupling is intentionally restrained:

| Province | Maximum terrain adjustment |
| --- | ---: |
| Sedimentary basin | 0.01 |
| Cratonic shield | 0.02 |
| Rift province | 0.04 |
| Volcanic arc | 0.05 |
| Orogenic belt | 0.08 |

This means terrain can help expose or reinforce a plausible mountain-belt core, but present-day topography cannot overwrite the seed-derived geological history. A huge mountain in a sedimentary basin does not automatically become a gneiss massif.

## Slate, schist and gneiss suitability

The intensity value is converted into overlapping suitability curves rather than hard thresholds.

- **Slate** rises from low grade, is strongest around roughly 0.22–0.40, and fades by about 0.56.
- **Schist** begins overlapping slate around 0.36, dominates the middle grades, and fades by about 0.82.
- **Gneiss** begins appearing around 0.64 and reaches full high-grade suitability around 0.84.

The overlap is intentional. Natural geological contacts are transitional, and future body ownership should not create a perfectly straight invisible line where every slate block suddenly becomes schist.

At very low intensity all three suitability values may be zero. That represents unmetamorphosed or weakly altered parent material rather than forcing a metamorphic rock everywhere.

## Live diagnostic

Use `/geostrata metamorphism` in a server world to inspect the staged field at the command source's current X/Z position.

The diagnostic reports:

- final metamorphic intensity;
- the currently dominant slate/schist/gneiss suitability;
- all three suitability weights;
- the seed-derived regional adjustment;
- the active terrain generator's adjustment;
- the primary and neighboring geological provinces.

The terrain contribution is sampled through the same `ChunkGeneratorTerrainMorphologySampler` seam used by the structural field, so vanilla and compatible terrain generators are observed through the active chunk generator rather than through a hard-coded biome or height assumption.

The command is intentionally read-only. It is there to tune and falsify the field before metamorphic worldgen depends on it.

## Why marble and quartzite are not included

Metamorphic intensity alone is not enough to choose every metamorphic lithology.

Marble requires an appropriate carbonate-rich parent rock. Quartzite requires quartz-rich parent material. Their future generation should therefore combine this intensity field with the correlated host/succession model rather than treating high grade as sufficient evidence.

That keeps the model compositional:

```text
regional history + metamorphic intensity + parent lithology
                         ↓
              plausible metamorphic product
```

For the first migration, slate, schist and gneiss are the useful grade sequence because their relative suitability can be staged without prematurely pretending the parent-rock problem is solved.

## Current runtime boundary

This milestone remains observational:

- `/geostrata metamorphism` exposes the live field for tuning;
- no baseline slate/schist/gneiss feature is suppressed;
- no chunk blocks are changed by the intensity field;
- no new worldgen feature type is registered;
- no datapack tuning contract is introduced before a runtime consumer needs one.

The next safe step is to use the diagnostic across representative vanilla and modded terrain, tune only if the evidence demands it, then connect a coherent metamorphic-band generator to the field in a separate migration. That generator should consume the same province, structural and host-ownership machinery rather than inventing another independent rock-placement system.
