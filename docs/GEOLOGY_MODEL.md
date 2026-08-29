# GeoStrata geology model

GeoStrata is a world-level geology system rather than a collection of decorative stone patches. The core rule is simple: terrain generators own terrain shape; GeoStrata owns the geological interpretation of the natural rock that terrain exposes.

## Current runtime

All fourteen catalogued natural rock baselines now use `geostrata:strata_lens` rather than vanilla `minecraft:ore` blobs. The historical `*_ore` IDs remain as stable datapack/worldgen identifiers.

The baseline geometry is body-specific:

- sedimentary rocks use coherent beds/lenses;
- conglomerate and breccia use local tapered coarse-clastic lenses;
- slate, schist, quartzite, gneiss and marble use coherent metamorphic fallback bands/bodies;
- basalt uses a broad thin sheet-like body;
- rhyolite uses a smaller thicker volcanic body.

Those independent bodies are a compatibility fallback. The opt-in correlated experiment is the more geological runtime: in owned sedimentary-basin, rift and orogenic chunks it replaces independent sedimentary fallback lenses with one continuous stratigraphic field and derives supported metamorphic outputs from their parent strata.

## Vertical coordinate rule

GeoStrata must not encode vanilla world height as geology.

Baseline rock placement uses dimension-relative vertical anchors (`above_bottom` / `below_top`) rather than absolute Minecraft Y coordinates. On a vanilla 1.20.1 overworld those margins reproduce the existing placement envelope closely; on a taller or deeper dimension the fallback envelope moves with the dimension instead of stopping around vanilla Y levels.

The correlated experiment goes further. Its schema declares:

```json
"verticalDomain": "dimension_bounds"
```

The runtime therefore evaluates the same stratigraphic field through the active world's actual bottom/top. Bed and cycle thickness do **not** scale with world height. A 500-block mountain exposes or uplifts more of the same geology; it does not turn an ordinary limestone bed into a 100-block-thick layer merely because the terrain is taller.

Bedrock, caves, fluids, structures, ores and other non-host blocks remain protected by the replacement predicate. The geological field may mathematically continue through the vertical domain, but only eligible natural host stone is mutated.

## Regional provinces

`GeologyProvinceSampler` deterministically assigns broad geological context from world seed and X/Z. Province sites are jittered in a coarse grid and nearest-site ownership creates irregular Voronoi-style regions.

Five archetypes exist:

- sedimentary basin;
- cratonic shield;
- orogenic belt;
- volcanic arc;
- rift province.

The sampler also tracks the neighboring site and distance to the province boundary. `province_profiles.json` supplies relative lithology suitability and blends neighboring profiles across a 192-block transition rather than creating hard geological borders.

Province selection is a pure function of seed and coordinates. It does not depend on chunk generation order, first visit, runtime UUIDs or mutable world state.

## Stratigraphy

`sedimentary_successions.json` defines lower-to-upper bed motifs and their relative thickness. `SedimentaryContactPlanner` gives contacts deterministic ownership, while `SedimentaryStratigraphicField` maps any X/Y/Z point to a cycle and position inside the selected succession.

The field keeps its own geological scale:

- cycle thickness;
- dip;
- warp amplitude;
- warp wavelength.

Those values are geological parameters, not percentages of dimension height.

The province site is the structural anchor. Repetition is explicit through the returned cycle index, allowing the field to extend through arbitrarily tall/deep compatible dimensions without inventing special high-altitude or deep-world variants of every rock.

## Terrain evidence

`ChunkGeneratorTerrainMorphologySampler` samples the **active terrain generator** using `OCEAN_FLOOR_WG`; it does not assume vanilla noise settings or load neighboring chunks. Compatible terrain mods therefore contribute their own mountain/ravine shape through the normal chunk-generator interface.

Sampling occurs on a shared 128-block world grid. `TerrainAwareStructuralField` bilinearly interpolates height and prominence between shared grid corners so neighboring chunks see the same broad terrain evidence.

Terrain response remains deliberately partial. GeoStrata should not simply copy the terrain surface underground.

Province drape/fold couplings are currently:

| Province | Drape | Fold |
| --- | ---: | ---: |
| Sedimentary basin | 18% | 5% |
| Cratonic shield | 8% | 2% |
| Orogenic belt | 55% | 75% |
| Volcanic arc | 35% | 35% |
| Rift province | 45% | 20% |

Fold amplification is capped at 25% of one succession cycle.

### Uplift versus erosion heuristic

Minecraft terrain gives GeoStrata shape, not geological history, so the model uses a deliberately small heuristic rather than pretending to reconstruct tectonics.

Positive prominence is treated as stronger ridge/uplift evidence and receives the normal province response. Increasingly negative prominence is treated primarily as erosional evidence. Drape and prominence-driven folding are smoothly attenuated as local prominence becomes more negative, down to 20% of their ordinary response.

The intended visual result is:

- large mountain ranges may uplift/deform strata and expose deeper units;
- ordinary relief produces restrained drape;
- deep ravines/canyons mostly cut through and expose existing strata instead of dragging strata down to follow the ravine floor.

This asymmetry is intentionally simple. It is a compatibility heuristic, not a tectonic simulator.

## Parent-aware metamorphism

In owned orogenic correlated chunks the runtime resolves the sedimentary parent first, then applies the shared metamorphic band/intensity model.

Current parent rules are:

- mudrock -> slate / schist / gneiss where the metamorphic band selects an output;
- carbonate -> marble using the same band ownership;
- unsupported parents remain their original lithology.

Quartzite remains fallback-only because GeoStrata does not yet define a valid quartz-rich sandstone parent. That relationship should be added only when the parent lithology actually exists.

## Surface sediments

Loose surface materials use terrain/water evidence rather than underground ore placement. Loams, peat, mud and clay use shallow native Minecraft placement plus GeoStrata suitability rules; actual water, flatness, valley shape and GeoStrata biome tags contribute as evidence.

This remains separate from structural bed deformation. Surface sediments describe recent deposition/reworking, while the structural field describes the underlying geological body.

## Ore occurrence

`ore_occurrences.json` defines the phase-one coal, iron, copper and gold geological contracts: material owner/output, valid host lithologies, province contexts and deposit styles.

The experimental deposit runtime uses deterministic candidate cells and style-specific bodies. It already searches vertical candidate cells from the active world's `bottomY` to `topY`, and when correlated geology owns a chunk its virtual host resolver reads the same full-dimension stratigraphic field. Vanilla/provider-native ores are still not suppressed while replacement abundance and economy remain experimental.

See `docs/ORE_SYSTEM.md` for the detailed grade/economy contract.

## Compatibility boundary

GeoStrata core targets semantic tags rather than third-party block IDs.

A terrain mod can make its natural stone eligible by extending:

```text
geostrata:worldgen/base_stone_replaceables
```

A biome/terrain compatibility datapack can likewise extend GeoStrata-owned biome tags. Java integration is only justified when data/tags cannot express the behavior.

This means a terrain mod with Y=500 mountains, Y=-200 ravines or custom natural stone should not require a second geology algorithm. GeoStrata reads the active generator's morphology, uses the active dimension's bounds and mutates only blocks declared as valid geological hosts.

## Determinism

Worldgen identity comes from stable inputs:

- world seed;
- dimension/worldgen configuration;
- stable province/site coordinates;
- loaded GeoStrata geology data;
- active terrain-generator height evidence.

No first-visited state or process-local random source defines geological identity. Changing a terrain generator or datapack is still changing the generation inputs and may therefore change the resulting geology.

## Validation

Run:

```text
python3 scripts/validate_geology_catalog.py
gradle test
```

`GeologyResourceContractTest` parses the shipped geology graph and validates configured/placed feature pairing. Natural rock fallback placements are required to use dimension-relative vertical anchors; absolute-Y rock placement is treated as a regression. Behavior tests cover province sampling, determinism, strata geometry, contacts, terrain interpolation, erosional attenuation, correlated ownership, metamorphic bands and ore geometry.

## Direction of travel

The intended authority chain remains:

1. terrain generator creates terrain shape;
2. GeoStrata chooses broad geological province/context;
3. succession/body fields define coherent rock geometry;
4. terrain evidence applies restrained uplift/fold response;
5. caves, cliffs, ravines and erosion expose that geology;
6. metamorphism and mineral deposits follow host/process context;
7. optional integrations add valid hosts/biomes/resources without redefining the geological model.

The remaining question is empirical tuning: whether these fields produce convincing abundance, exposures and performance across vanilla and extreme-height terrain in fresh worlds.
