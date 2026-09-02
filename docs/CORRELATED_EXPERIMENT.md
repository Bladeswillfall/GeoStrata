# Correlated geology runtime

GeoStrata's correlated sedimentary and province-background geology was incubated behind the separate `experiment-companion` artifact. It is now a normal core runtime: the bundled contract is `core_runtime + enabled`, and the Fabric core adapter attaches the correlated sedimentary and province-background features directly.

The companion no longer activates or attaches geology. Its remaining role is experimental common-ore suppression, benchmark-only diamond suppression and developer/debug commands.

Existing chunks are not retroactively rewritten. Use fresh chunks or fresh worlds when evaluating worldgen changes.

## Runtime authority

GeoStrata core does not use one universal rock matrix. Authority is layered:

1. the active terrain generator establishes terrain shape;
2. `GeologyProvinceSampler` resolves deterministic regional province/site context;
3. `CorrelatedSedimentaryRuntime` owns eligible ordered sedimentary host where the correlated contract claims a chunk;
4. `ProvinceBackgroundRuntime` resolves the remaining province architecture;
5. the shared structural field deforms both authorities;
6. metamorphism, fault damage, diamonds and ore deposits consume the same geology/structure;
7. mutation features replace only eligible natural host and preserve caves, fluids, ores, structures and unrelated blocks.

The semantic runtimes are authoritative; block-mutation feature order is not. This is important for ore generation: ore host qualification can resolve the future correlated or province-background lithology before the late background pass has physically replaced vanilla host stone.

## Correlated ownership

The correlated runtime currently targets:

- `basin_mudrock_carbonate_cycle`;
- `shelf_chalk_carbonate_cycle`;
- `rift_fining_upward_clastics`;
- `orogenic_fan_fining_upward`.

Allowed provinces are Sedimentary Basin, Rift Province and Orogenic Belt. Owned chunks must remain at least 96 blocks from the nearest province boundary; the transition zone is left to province-background/suture handling.

`CorrelatedExperimentChunkOwnership` retains its historical class name but normalizes ownership to the 16×16 chunk. The same decision clips superseded fallback bodies that would otherwise cross into correlated authority.

The authoritative correlated bed union is limestone, chalk, shale, mudstone, siltstone, breccia and conglomerate. Parent-aware Orogenic metamorphism may transform supported mudrock parents into slate/phyllite/schist/gneiss, carbonate into marble and quartz-rich sandstone into quartzite.

## Province architecture

`ProvinceBackgroundRuntime` is the shared semantic resolver used by both the late background mutation and pre-generation consumers such as ore host qualification.

Architecture is province-specific:

- **Sedimentary Basin** — existing succession selector + its field profile;
- **Rift Province** — existing succession selector + stronger extensional structure and narrow fault-damage breccia;
- **Cratonic Shield** — broad gneiss/schist basement terranes, narrow quartzite belts and occasional marble lenses;
- **Orogenic Belt** — deformed metamorphic gradient with gneiss core, then schist, phyllite and outer slate, plus quartzite ribbons and marble lenses;
- **Volcanic Arc** — varied metamorphic basement cut by basalt dikes/sills, local rhyolite bodies and breccia halos.

Phyllite is runtime-only. It reuses the existing regional metamorphic field and Orogenic background geometry and does not add a separate fallback feature or another worldgen pass.

The rejected one-rock-per-province fill and the later temporary repeated four-lithology matrix are no longer part of the runtime.

## Province boundaries and sutures

Province sampling is evaluated per world column, not once at the chunk centre. This removes the former 16×16 staircase where a curved province edge could become a chunk-shaped wall.

Within 96 blocks of a real province boundary, `TerraneSuture` gives the contact a deterministic steep dip through depth. At the terrain/sea-level reference the X/Z boundary remains where the province sampler places it; with depth the neighbouring terrane can project beneath the surface province. The horizontal shift is bounded so a suture cannot consume a whole province.

The suture selects between the two already-existing province architectures. It does not invent a third blended lithology palette or a new noise field.

`/geostrata structure` reports the surface boundary distance and, where applicable, the local suture dip and the terrane occupying the player's current Y.

## Structural field

`TectonicStructuralField` is the shared tectonic primitive. It composes with the existing base dip/warp and terrain-responsive field rather than replacing them.

Current structure includes:

- long-wavelength folds with restrained second harmonic;
- along-strike fold closures;
- sparse real stratigraphic-polarity reversal on correlated Orogenic folds;
- province-specific fault regimes;
- genuinely dipping Basin/Rift normal faults and Orogenic reverse/thrust-style faults;
- restrained listric curvature in Rift faults;
- restrained along-strike fault meander;
- narrow fault-damage breccia where justified.

Fault meander, curvature and fold closures reuse already-derived structural phases/parameters instead of adding independent voxel-scale noise fields.

## Shared structural consumers

Structural diamonds and fracture-controlled ore veins consume the same fault family that displaces the surrounding geology.

`FaultControlledOrePlanner` projects eligible `vein` candidates onto the fault trace at the candidate's actual Y and aligns the existing branched vein body's azimuth with the local meandering strike. Candidate cells still own rarity; the structural field owns geometry. Non-vein deposit styles are unchanged.

Ore activation remains keyed to material + original deterministic candidate cell. Moving a captured vein onto a fault cannot reroll whether the deposit exists.

## Terrain compatibility

`ChunkGeneratorTerrainMorphologySampler` samples the active chunk generator through `OCEAN_FLOOR_WG` on a shared 128-block grid and interpolates between shared points. GeoStrata therefore responds to compatible terrain generators without hard-coding their biome IDs or mountain heights.

Terrain response remains partial and province-dependent. Positive prominence acts as uplift/ridge evidence; strong negative prominence attenuates response so ravines mostly expose existing geology instead of dragging the geological field to the ravine floor.

Geological thickness is not scaled to dimension height. Core evaluates the same field across the active dimension bounds.

## Mutation safety

Late background mutation only targets the configured natural-host tag. It preserves:

- existing GeoStrata geology bodies;
- graded ores;
- caves and air;
- fluids;
- bedrock and unrelated blocks;
- known structure-piece bounding volumes.

Third-party natural stone can participate by extending `geostrata:worldgen/base_stone_replaceables` rather than requiring a Java integration.

## Diagnostics

Useful live-test commands:

```text
/geostrata province
/geostrata terrain
/geostrata field
/geostrata structure
/geostrata metamorphism
/geostrata experiment
/geostrata ore <material> candidate
```

`/geostrata field` uses the exact correlated runtime where correlated geology is authoritative. Outside that ownership it remains a virtual sedimentary diagnostic, so use `/geostrata structure`/`province` and the actual exposed blocks when inspecting province-background geology.

The metamorphism diagnostic reports slate, phyllite, schist and gneiss suitability from the same regional field used by the correlated runtime.

The ore candidate preview uses the same fault binding and semantic host precedence as runtime: existing GeoStrata host first, then eligible virtual correlated/background host. It does not assign a future host to air, caves or unrelated blocks.

## Determinism and performance

Worldgen identity remains a pure function of stable world inputs. There is no mutable plate simulation or first-visited state.

The hot path remains bounded:

- terrain evidence is coarse/cached;
- province context is cached per chunk;
- province model contexts are cached per unique geological site in a chunk;
- structural X/Z work resolves once per generated column;
- only near-boundary columns resolve both terranes;
- correlated runs cache through ordinary geological/structural boundaries;
- sparse polarity-transformed columns deliberately use conservative one-block runs until profiling justifies a more complex reversed-boundary cache.

## Current validation target

The next live-test priority is geometry rather than rock count: inspect fresh cave/cliff exposures that cross province boundaries and confirm that sutures read as steep regional contacts rather than chunk seams or vertical walls. Also verify that Volcanic, Cratonic and Orogenic provinces retain their distinct body architecture through those contacts.

Normal core GeoStrata worlds use this runtime. A datapack may disable the correlated contract for diagnosis or emergency recovery, but that does not dynamically resurrect the retired legacy rock-lens biome attachments because biome feature attachment is resolved before server datapacks load.
