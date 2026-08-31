# Metamorphic intensity and experimental runtime

GeoStrata has a deterministic metamorphic-intensity field and an opt-in runtime consumer for parent-aware regional metamorphism.

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

The correlated runtime reuses the terrain patch already sampled for `TerrainAwareStructuralField`. `TerrainPatch.morphologyAt(x,z)` bilinearly interpolates height, gradients, relief and prominence from that same fixed 128-block grid. Metamorphism therefore does not perform a second set of chunk-generator height queries and inherits the structural field's continuous cross-chunk terrain evidence.

## Slate, schist and gneiss suitability

The intensity value is converted into overlapping suitability curves rather than hard thresholds.

- **Slate** rises from low grade, is strongest around roughly 0.22–0.40, and fades by about 0.56.
- **Schist** begins overlapping slate around 0.36, dominates the middle grades, and fades by about 0.82.
- **Gneiss** begins appearing around 0.64 and reaches full high-grade suitability around 0.84.

The overlap is intentional. Natural geological contacts are transitional, and body ownership should not create a perfectly straight invisible line where every slate block suddenly becomes schist.

At very low intensity all three suitability values may be zero. That represents unmetamorphosed or weakly altered parent material rather than forcing a metamorphic rock everywhere.

## Live diagnostic

Use `/geostrata metamorphism` in a server world to inspect the field at the command source's current X/Z position.

The diagnostic reports:

- final metamorphic intensity;
- the currently dominant slate/schist/gneiss suitability;
- all three suitability weights;
- the seed-derived regional adjustment;
- the active terrain generator's adjustment;
- the primary and neighboring geological provinces.

The command is read-only. It remains useful for tuning the broad field independently of whether the correlated experiment companion is installed.

## Structural band ownership

`MetamorphicBandPlanner` assigns mudrock grade ownership without inventing another fold system.

The caller supplies the existing structural field's vertical offset, a structural site anchor and a band thickness. The planner converts world Y into a structure-adjusted band index:

```text
band = floor((Y - existing structural vertical offset) / band thickness)
```

Each site/band pair receives one stable `GeologyDeterminism` roll. The local slate/schist/gneiss suitability values then act as weights for that band. This has two useful properties:

- nearby blocks in the same folded/draped structural band do not independently roll into salt-and-pepper metamorphic blocks;
- broad metamorphic intensity still controls the geological outcome, so a structural band cannot create gneiss where gneiss suitability is zero.

The experimental runtime introduces no second band-scale tuning value. It reuses the active correlated succession's existing cycle thickness as the band thickness. If playtesting later proves that grade zones need a different scale, that should become an explicit tuning decision rather than an accidental constant now.

## Parent rock controls the product

`CorrelatedSedimentaryRuntime.TerrainAwareSite.outputLithology(...)` first resolves the exact virtual parent bed from the correlated structural field. In an experiment-owned **orogenic belt** chunk, metamorphic suitability only enables transformations that are valid for that parent's catalog `genesis`:

- `mudrock` → slate/schist/gneiss through the grade-band selector;
- `carbonate` → marble;
- `quartz_sandstone` → quartzite;
- unsupported parents remain unchanged.

The quartz-rich sedimentary parent is provider-owned vanilla `minecraft:sandstone`, represented semantically as `sandstone`. It is part of the correlated orogenic fining-upward succession rather than an independent GeoStrata fallback body.

This is more important than the grade number itself: high intensity is not permission to turn arbitrary rock into gneiss, marble or quartzite.

## Experimental world mutation

Runtime metamorphism is part of the existing correlated feature rather than a second registered feature.

When the optional correlated experiment companion is installed and the experiment owns an orogenic chunk:

1. the correlated structural field resolves the virtual sedimentary parent bed;
2. the same terrain-aware structural offset determines metamorphic suitability and, for mudrock, the grade band;
3. mudrock parents emit slate/schist/gneiss selected from the existing suitability curves;
4. carbonate parents emit marble when metamorphic suitability is present;
5. quartz-sandstone parents emit quartzite when metamorphic suitability is present;
6. unsupported parents emit their ordinary correlated lithology.

This avoids a second worldgen pass, a second ownership system and feature-order dependence between sedimentary generation and metamorphism.

The correlated feature still runs at `UNDERGROUND_DECORATION` and preserves ordinary ore blocks because they are not members of `geostrata:worldgen/base_stone_replaceables`.

### Legacy metamorphic cleanup inside owned chunks

Standalone GeoStrata still has old independent baseline features for slate, marble, quartzite, schist and gneiss. Those may already have run before the correlated experiment's decoration pass.

Inside an experiment-owned orogenic chunk only, the correlated pass therefore also treats an existing GeoStrata lithology-catalog block with `rockClass=metamorphic` as replaceable legacy placeholder material. The position is then rewritten from the authoritative correlated parent/output model.

This keeps the experimental geology readable without globally suppressing those baseline features. It does **not** match graded ore blocks, because ore blocks are not lithology-catalog rock entries.

Marble and quartzite now regenerate only where their required parent semantics exist. Limestone/carbonate beds can become marble; the correlated sandstone bed can become quartzite. Old random marble/quartzite fallback blocks inside owned chunks are therefore replaced by the parent-aware answer rather than retained merely because they happened to generate first.

## Why intensity and parent semantics remain separate

Metamorphic intensity alone is not enough to choose every metamorphic lithology.

```text
regional history + metamorphic intensity + parent lithology
                         ↓
              plausible metamorphic product
```

The intensity field answers whether meaningful metamorphism is plausible at the location. Parent composition then constrains the product. Only mudrock needs the slate/schist/gneiss grade selector; carbonate and quartz-rich sandstone have compositionally constrained products in this simplified gameplay model.

The same principle is reused by contact metamorphism around plutonic roots. Its thermal geometry can mark an aureole, but `ContactMetamorphism` still resolves the product from parent genesis: quartz sandstone becomes quartzite, carbonate becomes marble, suitable pelitic/silty parents become hornfels, and unsupported high-grade material remains unchanged.

## Runtime boundary

Standalone GeoStrata remains unchanged: installing only the core mod does not add the correlated feature to biome generation and does not activate the parent-aware replacement path.

With the optional experiment companion installed:

- sedimentary-basin and rift correlated chunks preserve sandstone and other sedimentary parents unless another valid process transforms them;
- orogenic correlated chunks may transform mudrock parents into slate/schist/gneiss, carbonate into marble, and quartz sandstone into quartzite;
- the transformation is deterministic from world seed, coordinates/site identity, structural field, terrain-generator evidence and loaded geology data;
- no runtime UUID, first-visited state or process-local random source participates;
- no new feature type or second metamorphic worldgen registration is introduced.

This remains experimental worldgen and should be evaluated in fresh/disposable worlds before the baseline metamorphic generators are retired more broadly.
