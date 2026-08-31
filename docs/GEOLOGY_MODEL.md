# GeoStrata geology model

GeoStrata is a world-level geology system rather than a collection of decorative stone patches. Terrain generators own terrain shape; GeoStrata interprets eligible natural rock exposed by that terrain.

## Runtime authority

Standalone GeoStrata retains the natural-rock `strata_lens` compatibility fallback. The opt-in experiment companion enables the advanced runtime.

Advanced authority is layered rather than monolithic:

1. the active terrain generator creates terrain;
2. `GeologyProvinceSampler` resolves regional province/site context;
3. `CorrelatedSedimentaryRuntime` owns eligible ordered sedimentary host in experiment-owned chunks;
4. `ProvinceBackgroundRuntime` resolves the remaining province architecture;
5. the shared structural field deforms those geological authorities;
6. metamorphism, fault damage, diamonds and ore deposits consume the same semantic geology;
7. mutation features only replace eligible natural host and preserve caves, fluids, ores, structures and unrelated blocks.

`ProvinceBackgroundRuntime` is intentionally semantic rather than tied to mutation order. A consumer such as ore worldgen can ask what rock will occupy a location even when the late background replacement has not run yet.

Existing chunks are not retroactively rewritten.

## Provinces

`GeologyProvinceSampler` assigns five broad contexts from world seed and X/Z using deterministic jittered sites. Sampling is evaluated per world column, not once at chunk centre.

### Sedimentary Basin

Uses the existing `SedimentarySuccessionSelector` and the selected succession's own field profile. Structure is weakly folded with sparse modest dipping normal faults.

### Rift Province

Also reuses the existing succession selector. Structure is strongly extensional with closer, higher-throw normal faults, restrained listric curvature and narrow fault-damage breccia.

### Cratonic Shield

Uses basement/terrane architecture rather than repeating synthetic beds:

- broad warped gneiss/schist domains;
- narrow quartzite belts;
- occasional elongated marble lenses;
- subdued folding and sparse low-throw ancient faults.

### Orogenic Belt

Uses a deformed metamorphic gradient:

- gneiss high-grade core;
- schist intermediate zone;
- phyllite low-to-medium grade zone;
- slate outer low-grade zone;
- quartzite ribbons;
- comparatively common marble lenses.

Where correlated parent-aware stratigraphy owns the rock it remains the higher authority and may express real overturned younging direction. Background metamorphic architecture does not claim stratigraphic polarity.

### Volcanic Arc

Uses metamorphic basement cut by igneous/volcanic bodies:

- varied gneiss/schist basement;
- narrower quartzite cores;
- steep basalt dikes;
- finite basalt sills;
- zoned volcanic complexes with shallow rhyolite above granite/diorite plutonic roots;
- shallow volcanic breccia halos;
- deep parent-aware contact aureoles around the plutonic root.

The plutonic root reuses the same deterministic complex geometry as the rhyolite body rather than adding a second intrusion engine. Contact aureole geometry likewise reuses that complex and lets parent lithology decide the thermal product.

The rejected one-rock-per-province fill and later repeated four-lithology background matrix are no longer part of the advanced runtime.

## Province contacts

A province boundary is a geological contact, not a chunk boundary.

Per-column sampling removes the former 16×16 staircase caused by choosing one province from the chunk centre. Within 96 blocks of the actual boundary, `TerraneSuture` allows the two existing province architectures to meet on a bounded steeply dipping contact through depth.

At the reference surface the boundary remains at the X/Z position selected by `GeologyProvinceSampler`. With depth the neighbouring terrane may project beneath the surface province. The shift is capped so the suture cannot consume an entire province.

The suture does not blend random lithologies and does not create a third transition palette.

## Structural field

Geological position is assembled from small reusable transforms:

- base dip/warp;
- coarse terrain drape;
- terrain-evidence open-fold response;
- long-wavelength tectonic fold;
- along-strike fold closure envelope;
- sparse correlated Orogenic stratigraphic-polarity transform;
- discrete tectonic fault displacement.

`TectonicStructuralField` is the shared tectonic primitive. Terrain response remains separate so terrain can expose/amplify structure without becoming the source of tectonic structure.

### Folds

Folds are deterministic, province-scale and long-wavelength. A restrained second harmonic avoids perfectly sinusoidal contacts. `TectonicFoldClosures` modulates amplitude along strike so folds terminate into broad noses instead of infinite parallel stripes.

Sparse correlated Orogenic columns may use `TectonicFoldPolarity`:

- positive vertical scale: normal younging-up;
- near zero: near-vertical limb/hinge;
- negative: stratigraphic coordinate decreases upward, producing an actual overturned limb.

This is deliberately not a full recumbent-fold/nappe simulator.

### Faults

| Province | Regime | Geometry |
| --- | --- | --- |
| Sedimentary Basin | normal | sparse, modest, planar dipping |
| Cratonic Shield | ancient | sparse, low-throw, near-vertical |
| Orogenic Belt | reverse | strong dipping / thrust-style |
| Volcanic Arc | mixed | moderate near-vertical |
| Rift Province | normal | close, high-throw, listric |

Fault traces also have restrained long-wavelength along-strike meander. Meander amplitude is derived from existing fault parameters and remains bounded; it does not introduce another independent structural noise field.

Rift listric geometry is one restrained vertical curvature term. X/Z structural work is still resolved once per column and Y sampling applies the remaining piecewise fault state.

Narrow fault-damage breccia is exposed only where justified and uses the same shared fault geometry.

## Structural consumers

### Diamonds

Sparse deep structural diamonds in cratonic interiors project onto the same ancient fault family that displaces surrounding geology. Candidate cells own rarity; the structural field owns location.

Kimberlite/lamproite pipes remain a separate very-rare intrusive route.

### Ore veins

`FaultControlledOrePlanner` is the structural binding for fracture-style experimental `vein` deposits.

When a candidate is close enough to the shared fault field:

1. its centre is projected onto the fault trace at the candidate's actual Y;
2. nearby points on that same trace determine the local meandering strike;
3. the existing branched `OreDepositGeometry.Body` is re-oriented along that strike.

Body size, warp, branches, concentration and grade logic remain unchanged. Non-vein styles bypass the binding unchanged. Candidate activation is still keyed to the original deterministic cell, so fault projection cannot reroll abundance.

Ore host qualification is semantic and order-independent. Existing GeoStrata rock wins first; otherwise an eligible natural host may resolve through correlated or province-background runtime geology. Air, caves and unrelated blocks do not receive fictional future hosts.

## Terrain evidence and compatibility

`ChunkGeneratorTerrainMorphologySampler` samples the active terrain generator through `OCEAN_FLOOR_WG` on a shared 128-block grid and interpolates between shared points.

Current terrain couplings remain deliberately partial:

| Province | Drape | Terrain fold |
| --- | ---: | ---: |
| Sedimentary Basin | 18% | 5% |
| Cratonic Shield | 8% | 2% |
| Orogenic Belt | 55% | 75% |
| Volcanic Arc | 35% | 35% |
| Rift Province | 45% | 20% |

Positive prominence acts as uplift/ridge evidence. Strong negative prominence attenuates response so ravines primarily expose existing geology rather than drag the geological field to their floor.

No vanilla absolute Y is geological identity. Bed/cycle thickness remains geological scale rather than a percentage of dimension height.

Terrain-mod compatibility is semantic: a mod can make its natural stone eligible by extending `geostrata:worldgen/base_stone_replaceables`. Java integration is only justified where tags/data cannot express the behavior.

## Parent-aware metamorphism

In correlated Orogenic chunks the parent rock is resolved before metamorphic output:

- mudrock → slate / phyllite / schist / gneiss through the existing grade-band selector;
- carbonate → marble;
- quartz-rich sandstone → quartzite;
- unsupported parents remain unchanged.

The quartz-rich parent is the provider-owned semantic `sandstone`, backed by vanilla `minecraft:sandstone` and emitted by the shared sedimentary-stratigraphy runtime. The orogenic fining-upward succession therefore reads:

```text
breccia → conglomerate → sandstone → siltstone → shale
```

The same transformed stratigraphic coordinate is used for parent bedding and metamorphic selection on overturned limbs. Outside an eligible orogenic metamorphic context, sandstone remains sandstone.

Phyllite is a GeoStrata-owned runtime-only lithology. It is selected by the same regional metamorphic field and deterministic band roll as slate/schist/gneiss and by the existing Orogenic background gradient. It has no independent `phyllite_ore` fallback feature, no extra noise field and no separate worldgen pass.

Contact metamorphism is separately geometry-driven but uses the same parent-first rule. In the volcanic-arc plutonic aureole, suitable pelitic/silty/low-grade foliated material becomes hornfels, carbonate becomes marble, quartz sandstone becomes quartzite, and unsupported/high-grade country rock remains unchanged.

## Diagnostics

Useful commands:

```text
/geostrata province
/geostrata terrain
/geostrata field
/geostrata structure
/geostrata metamorphism
/geostrata experiment
/geostrata resolve
/geostrata ore <material> candidate
```

`/geostrata structure` reports the active authority, structural components, correlated polarity when applicable, current-Y fault position/distance/dip, damage-zone state, total displacement and near-boundary suture information.

`/geostrata resolve` reports the authoritative semantic lithology, province, body/process provenance and parent lithology when available alongside the actual world block.

`/geostrata ore <material> candidate` uses the same fault binding and host precedence as runtime generation.

## Determinism and performance

Worldgen identity is a pure function of stable world inputs. There is no mutable plate simulation or first-visited state.

The hot path is intentionally bounded:

- terrain evidence is coarse/cached;
- province context is cached per chunk;
- province model contexts are cached per unique site used by that chunk;
- X/Z structural work resolves once per generated column;
- only columns close enough to a boundary resolve a second terrane;
- ordinary correlated lithology runs cache until the next geological/structural boundary;
- sparse polarity-transformed columns currently use conservative one-block runs rather than a second complex boundary solver.

New structural features should reuse these primitives or justify both their geological value and runtime cost before adding another simulation layer.

## Validation

Run:

```text
python3 scripts/validate_geology_catalog.py
gradle test
```

Current tests cover deterministic province sampling, terrain response, province architectures, metamorphism, fault projection, dipping/listric/meandering fault geometry, fault-damage policy, fold closures, overturned correlated contacts, shared fault-controlled ore geometry and dipping terrane sutures.
