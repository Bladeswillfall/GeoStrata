# GeoStrata geology model

GeoStrata is a world-level geology system rather than a collection of decorative stone patches. Terrain generators own terrain shape; GeoStrata owns the geological interpretation of eligible natural rock that terrain exposes.

## Runtime authority

The normal standalone mod keeps the fourteen natural-rock `strata_lens` features as a compatibility fallback. The historical `*_ore` IDs remain stable datapack/worldgen identifiers even though the bodies are no longer vanilla ore blobs.

The opt-in experiment companion is the more geological runtime. Its authority is deliberately layered rather than replaced by one monolithic generator:

1. terrain generation establishes the world's shape;
2. GeoStrata resolves geological province and province-site context;
3. correlated succession/runtime geology owns eligible sedimentary host stone where its experiment claims a chunk;
4. province-specific architecture fills remaining eligible natural host stone;
5. metamorphism and mineral deposits consume the same geology/structural context;
6. caves, fluids, bedrock, structures, ores and other non-host blocks remain untouched by the late background fill.

Existing chunks are not retroactively rewritten.

## Province architecture

`GeologyProvinceSampler` deterministically assigns one of five broad geological contexts from world seed and X/Z using jittered coarse sites and nearest-site ownership. Province identity does not depend on chunk generation order or mutable runtime state.

### Sedimentary Basin

The Basin reuses the existing `SedimentarySuccessionSelector` and the selected succession's own `regional`/`local` field profile. There is no separate four-rock background palette.

Its tectonic field has sparse, modest normal faults and weak regional folding. The faults are planar but dipping rather than permanently vertical.

### Rift Province

Rift background geology also reuses the existing succession selector instead of a second rock matrix. Closely spaced, high-throw normal faults provide horst/graben-style displacement.

Rift faults are listric: their trace uses one restrained quadratic curvature term so the fault plane becomes shallower with depth. This is intentionally a small extension of the shared fault family, not a second 3-D fault simulator.

A very narrow breccia damage seam can reveal the shared fault plane in cave/cliff exposure. The same structural overlay is available to eligible correlated host so the visible plane does not disappear where correlated stratigraphy has higher authority. Ores, caves, fluids, structures and unrelated blocks remain protected.

### Cratonic Shield

Cratonic Shield uses an old metamorphic-basement architecture rather than repeated synthetic beds:

- broad warped gneiss/schist terranes;
- narrow quartzite belts;
- occasional elongated marble lenses;
- subdued folds and sparse low-throw ancient faults.

Marble therefore has a coherent geological home rather than receiving a global abundance multiplier.

### Orogenic Belt

Orogenic background geology is a strongly deformed metamorphic gradient:

- gneiss high-grade core;
- schist intermediate zone;
- slate outer zone;
- quartzite ribbons;
- comparatively common elongated marble lenses.

The shared structural field supplies strong folds and dipping reverse/thrust-style faults. Their planar dip varies deterministically from moderately low-angle thrust-style geometry to steeper reverse faults. A narrow breccia damage seam may expose the same fault through both eligible background and correlated host.

Where correlated parent-aware strata own host rock, they remain the ordered stratigraphic authority and may additionally express sparse overturned limbs. The fallback metamorphic gradient does not claim a younging direction and therefore does not pretend to be overturned stratigraphy.

### Volcanic Arc

Volcanic Arc uses a metamorphic basement cut by intrusive/volcanic structures:

- varied gneiss/schist basement;
- narrower quartzite cores;
- steep basalt dikes;
- basalt sills;
- local rhyolite bodies;
- breccia halos around dikes and rhyolite bodies.

Its shared mixed fault family currently remains vertical-plane geometry; no additional dipping/shear model is added until live testing justifies it.

## Structural field

The generated geological position is assembled from existing small transforms rather than one opaque noise function:

- base stratigraphic dip/warp;
- coarse terrain drape;
- terrain-evidence open-fold response;
- long-wavelength tectonic fold and axial closure envelope;
- correlated Orogenic stratigraphic-polarity transform where selected;
- discrete tectonic fault throw.

`TectonicStructuralField` is the authoritative tectonic primitive. The terrain-driven fold remains separate: terrain can amplify/expose geological structure without becoming the tectonic structure itself.

### Tectonic folds

Folds are deterministic, province-scale and long-wavelength. A restrained second harmonic avoids perfectly sinusoidal contacts. Orogenic belts receive the strongest fold amplitude; cratonic shields the weakest.

`TectonicFoldClosures` modulates the existing fold amplitude along strike so folds wax and wane into broad noses/closures instead of behaving like infinitely parallel stripes. It does not increase the underlying fold amplitude or introduce another noise field.

Sparse correlated Orogenic folds can also reverse stratigraphic polarity. `TectonicFoldPolarity` reuses the existing fold axis, wavelength, phase and closure envelope; existing phase values also choose whether the site activates, which compressed limb is selected, the overturn strength and a structural pivot below the terrain anchor. No second random structural field is introduced.

The vertical stratigraphic scale has a direct geological meaning:

- `+1` is ordinary younging-up stratigraphy;
- values approaching `0` represent a near-vertical hinge/limb;
- negative values reverse the upward younging direction and therefore represent an overturned limb.

The transform is applied only where an ordered correlated succession is authoritative. Tests verify that the correlated stratigraphic coordinate actually decreases as world Y increases on an overturned limb and that upward traversal crosses a reversed bed contact.

This is not yet a general recumbent-fold or nappe simulator. Large-scale repeated thrust sheets, recumbent closures and arbitrary multi-valued fold loops should only be added if they can reuse the same structural coordinate model without introducing a second voxel-scale simulation.

### Faults

Fault spacing, throw and regime vary by province. Fault identity is seed-derived and stable.

The current geometry is:

| Province | Regime | Geometry |
| --- | --- | --- |
| Sedimentary Basin | normal | sparse, modest, planar dipping |
| Cratonic Shield | ancient | sparse, low-throw, vertical plane |
| Orogenic Belt | reverse | strong planar dipping / thrust-style |
| Volcanic Arc | mixed | moderate vertical plane |
| Rift Province | normal | close, high-throw, listric |

Fault dip is expressed as horizontal trace shift per vertical block. Expensive X/Z work resolves once into a `TectonicStructuralField.Column`; only the piecewise fault-state lookup varies with Y. Rift curvature adds one quadratic term to that cached column.

The province site remains the zero-displacement reference at Y=0 so deterministic anchoring and existing 2-D callers remain stable. Legacy X/Z-only methods therefore mean the Y=0 trace.

`TerrainAwareStructuralField.Column` composes the cached base/terrain/fold terms with the Y-aware fault state. Correlated worldgen uses the same column and terminates its normal run cache at the next fault-state boundary so a dipping fault cannot be skipped by a long lithology cache run.

## Structural consumers

### Diamonds

The earlier diamond-only proxy corridor has been removed. Sparse deep structural diamond candidates in cratonic interiors project onto the same real ancient fault family that displaces the surrounding geology. Candidate cells still own rarity; the structural field owns geometry.

Kimberlite/lamproite pipes remain a separate very-rare intrusive route.

### Ore veins

Fracture-controlled experimental `vein` candidates reuse the shared fault field. If a candidate lies close enough to a fault, its centre is projected onto that fault plane at the candidate's own Y. This means a deep vein follows the deep position of a dipping/listric plane rather than its surface/Y=0 trace.

The existing branched vein body remains responsible for local fracture geometry. GeoStrata does not run a second ore-specific fault simulator.

## Terrain evidence and compatibility

`ChunkGeneratorTerrainMorphologySampler` samples the active terrain generator through `OCEAN_FLOOR_WG` without loading neighboring chunks. Sampling occurs on a shared 128-block grid and is bilinearly interpolated.

Province drape/fold couplings remain deliberately partial:

| Province | Drape | Terrain fold |
| --- | ---: | ---: |
| Sedimentary Basin | 18% | 5% |
| Cratonic Shield | 8% | 2% |
| Orogenic Belt | 55% | 75% |
| Volcanic Arc | 35% | 35% |
| Rift Province | 45% | 20% |

Positive prominence is treated as stronger uplift/ridge evidence. Increasingly negative prominence attenuates terrain response so ravines mostly expose existing geology instead of dragging the geological field down to their floor.

No absolute vanilla Y level is used as geological identity. Field thickness remains geological scale rather than a percentage of dimension height, and the experiment operates against the active dimension bounds.

Terrain/mod compatibility remains semantic: a terrain mod can make its natural stone eligible by extending `geostrata:worldgen/base_stone_replaceables`. Java integration is only justified where tags/data cannot express the behavior.

## Parent-aware metamorphism

In correlated orogenic chunks the runtime resolves the parent rock before metamorphic output:

- mudrock -> slate / schist / gneiss where the metamorphic band selects an output;
- carbonate -> marble;
- unsupported parents remain unchanged.

The same transformed stratigraphic Y is supplied to metamorphic band selection on a polarity-transformed limb, so parent bedding and metamorphic output do not silently use opposing structural coordinates.

Quartzite remains fallback-only until GeoStrata has a valid quartz-rich sandstone parent rather than inventing a false parent relationship.

## Diagnostics

Useful commands include:

- `/geostrata province`
- `/geostrata terrain`
- `/geostrata field`
- `/geostrata structure`
- `/geostrata metamorphism`
- `/geostrata experiment`

`/geostrata structure` reports the active authority, base/terrain/tectonic components, correlated stratigraphic polarity when applicable, fault regime and throw, nearest fault position/distance at the player's current Y, local fault dip, fault-damage-zone membership and total structural displacement. Background architecture reports polarity as `n/a` because it does not claim an ordered bed younging direction.

## Determinism and performance

Worldgen identity is a pure function of stable world inputs: world seed, geological site/province, loaded geology data and active terrain-generator evidence. There is no first-visited state or mutable plate simulation.

The structural implementation keeps the hot path small:

- terrain evidence is coarse and cached;
- tectonic orientation/spacing/throw/curvature resolve once per site;
- X/Z structural work resolves once per generated column;
- Y sampling performs only the remaining piecewise fault/contact work;
- ordinary correlated lithology runs are cached until the next geological or structural boundary;
- polarity-transformed columns conservatively use one-block lithology runs so a reversed contact cannot be skipped.

The one-block policy is intentionally simpler than adding a second boundary solver before profiling shows it is needed. The polarity transform is sparse and Orogenic-only; if live benchmarks show it is material, its linear per-column transform can support an exact reversed-boundary cache without changing the geology model.

This is the constraint for future structures: additional geometry should reuse these primitives or justify its runtime cost before a new simulation layer is introduced.

## Validation

Run:

```text
python3 scripts/validate_geology_catalog.py
gradle test
```

The repository tests cover province sampling, deterministic stratigraphy, terrain response, metamorphic bands, ore geometry, province-specific architectures, fault projection, dipping fault planes, listric Rift curvature, fault-damage overlays, fold closures, structural run-boundary behavior and actual correlated stratigraphic reversal on overturned Orogenic limbs.
