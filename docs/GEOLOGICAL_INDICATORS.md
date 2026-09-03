# Geological indicators

GeoStrata exposes subtle player-facing signs of geology without adding a second terrain-generation system.

## Hydrocarbon reservoirs and oil seeps

`HydrocarbonReservoirField` is a deterministic semantic field keyed by world seed and 384-block cells. A reservoir can only exist where the resolved deep lithology is sedimentary. Source-rich mudrocks are favored, followed by carbonates, sandstone, silt-rich sediments and coarse clastics.

The field does not place an oil fluid or invent a processing chain. That is deliberate: GeoStrata owns the geological occurrence, while a future optional petroleum mod can own fluids, pumps and refining. The returned reservoir record provides stable center/radius, pressure, concentration and seep coordinates for those integrations.

Sufficiently pressured reservoirs leave linked clues at their deterministic surface seep point while a player is nearby:

- sparse black seep/smoke particles show that the seep is active;
- a persistent `petroleum_stain` multiface block marks a small deterministic patch of affected ground and exposed rock;
- stronger reservoirs can additionally accumulate layered `bitumen` crust close to the seep.

`petroleum_stain` deliberately reuses Minecraft's native sculk-vein / lichen-style multiface behavior instead of replacing every possible host with a petroleum-specific block. One stain block can cling to several adjacent faces, including sloped terrain and exposed rock edges.

The stain patch uses only existing vanilla dark/brown textures and thin model geometry, so no custom renderer or host-by-host texture matrix is required. It attaches only to plausible natural supports: dirt, sand, clay, mud, gravel, vanilla Overworld base stone and GeoStrata rock blocks. It does not replace vegetation or arbitrary occupied blocks.

### Bitumen crust

`bitumen` reuses Minecraft's snow-layer state and geometry: one block represents one through eight stacked layers instead of introducing separate thin/thick tar blocks.

Bitumen is intentionally rarer than staining. Only reservoirs with pressure of at least `0.72` can form it. The footprint is a deterministic 2-3 block radius around the same seep coordinate already used by the particles and stain. Placement follows the local terrain rather than drawing a circular blob:

- level and downhill cells are favored;
- uphill spread is strongly penalized;
- drops greater than two blocks and climbs greater than one block are rejected;
- depressions receive thicker accumulation;
- higher-pressure reservoir centers receive the deepest crusts.

Thin one- and two-layer crusts are mostly visual. From three layers upward, walking becomes progressively sticky; seven- and eight-layer crusts strongly reduce horizontal movement and also damp upward motion. This uses ordinary block/entity movement hooks rather than custom sinking physics.

Bitumen currently has no survival drop. It is geological evidence, not a new hand-harvested petroleum economy. Extraction, refining, crude-fluid production and depletion remain future technology-integration concerns.

Both stain and bitumen are materialized lazily from the same seep query that already drives particles. Once placed they persist normally in the world. This gives existing saves the physical clues without adding another chunk-generation pass.

### Subsurface petroleum-bearing hosts

The correlated sedimentary writer can also expose petroleum-bearing variants inside the same rock bodies it is already generating. This does not add another feature or scan: once the existing writer has resolved a bed, it can reuse the deterministic reservoir field before committing that block state.

Two deliberately narrow host expressions are supported:

- `oil_shale` is a shale-bed expression inside the richer core of a mudrock-associated hydrocarbon body;
- `oil_sands` is a sandstone-bed expression where a sandstone-associated body has both sufficient concentration and pressure.

These are host-rock variants, not new lithologies or ores. The semantic geology remains `shale` or `sandstone`, so stratigraphy, metamorphism and other geology consumers do not acquire petroleum-specific rock types. Mudstone is intentionally not renamed to oil shale, and metamorphosed shale/sandstone remains its metamorphic product rather than receiving a petroleum variant.

Mining the variants returns the ordinary host (`shale` or `sandstone`) rather than crude, bitumen or a petroleum item. Their role is discoverability and a future in-place integration point. Both are exposed through the `geostrata:petroleum_bearing_rocks` block tag so an eventual pump/drill integration can recognise them without GeoStrata defining machinery or processing rules.

## Firedamp mist

Deep sedimentary geology can also expose a sparse deterministic gas-proneness signal. Mudrock is favored most strongly, followed by silt-rich sediment, carbonate, sandstone and coarse clastics. Burial increases the signal; shallow geology does not display firedamp.

Players in sufficiently gas-prone underground locations may see a small amount of low-lying cloud-like mist near the cave or mine floor. The mist is a visual gameplay shorthand for otherwise invisible methane-rich firedamp, not a claim that methane is visibly foggy.

The implementation deliberately stops at the clue:

- no gas block or collectable fluid;
- no cave flood-fill or sealed-space simulation;
- no poisoning, suffocation, ignition fronts or explosions;
- no finite gas state or depletion model;
- nearby fire, lit campfires and lava simply suppress the local mist presentation.

The suppressor scan only runs after the cheap geology test has already found a rare firedamp-prone site.

## Coal haze

Underground players near a meaningful concentration of coal receive a light ash haze. The signal is derived from blocks already present in the loaded world:

- vanilla coal ores count as a small signal;
- GeoStrata Poor / Medium / Rich / Massive coal grades scale with their base yield;
- vanilla coal blocks count as a strong signal, matching the Massive-coal natural override when that feature is enabled.

The scan is bounded to a 13 x 9 x 13 volume around each underground player and runs once per second. It never loads chunks, searches for hidden deposits at long range, or participates in chunk generation.

## Performance boundary

These indicators and host expressions reuse existing geology work rather than building a parallel system:

- no new petroleum worldgen pass;
- no persistent per-chunk geology state;
- no per-tick full-radius scan;
- hydrocarbon geometry and gas potential are reconstructed from seed + geology;
- petroleum-bearing shale/sands are selected inside the already-running correlated sedimentary replacement pass, with reservoir sampling cached per relevant column;
- petroleum stain and bitumen writes are bounded to tiny deterministic patches near an already-resolved seep;
- coal haze only examines a small loaded neighborhood once per second;
- firedamp scans for nearby heat sources only after a rare gas-prone geology match;
- particles and petroleum evidence blocks are clues, not geological authority.

Future indicators should reuse this pattern: query existing semantic geology or generated blocks, remain deterministic where appropriate, and avoid creating a parallel geology model.
