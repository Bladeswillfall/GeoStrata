# Geological indicators

GeoStrata exposes player-facing signs of geology without adding a parallel terrain-generation system.

## Hydrocarbon reservoirs and oil seeps

`HydrocarbonReservoirField` is a deterministic semantic field keyed by world seed and 384-block cells. A reservoir can only exist where resolved deep lithology is sedimentary. Source-rich mudrocks are favored, followed by carbonates, sandstone, silt-rich sediments and coarse clastics.

GeoStrata owns petroleum occurrence, not a duplicate processing chain. The reservoir record provides stable center/radius, pressure, concentration and seep coordinates for evidence and optional technology integrations.

Sufficiently pressured reservoirs leave linked clues at their deterministic surface seep point while a player is nearby:

- sparse black seep/smoke particles show that the seep is active;
- a persistent `petroleum_stain` multiface block marks affected ground and exposed rock;
- stronger reservoirs can accumulate layered `bitumen` crust;
- when Create: Diesel Generators is installed, the rarest very-high-pressure reservoirs can expose that mod's native `crude_oil` source fluid.

`petroleum_stain` reuses Minecraft's native sculk-vein / lichen-style multiface behavior. It can cling to plausible natural supports including dirt, sand, clay, mud, gravel, vanilla Overworld base stone, GeoStrata rocks and `geostrata:petroleum_bearing_rocks`.

### Bitumen crust

`bitumen` reuses Minecraft's snow-layer state and geometry: one block represents one through eight stacked layers. Only reservoirs with pressure of at least `0.72` can form it. The deterministic 2-3 block seep footprint favors level/downhill cells, rejects cliff-scale jumps, thickens in depressions and becomes progressively sticky from three layers upward.

Bitumen currently has no survival drop. It remains geological evidence rather than a hand-harvested petroleum economy.

Both stain and bitumen are materialized lazily from the same seep query that already drives particles. Once placed they persist normally in the world.

### Subsurface petroleum-bearing hosts

The correlated sedimentary writer can expose petroleum-bearing variants inside rock bodies it is already generating. This does not add another feature or scan.

- `oil_shale` is a shale-bed expression inside the richer core of a mudrock-associated hydrocarbon body;
- `oil_sands` is a sandstone-bed expression where a sandstone-associated body has sufficient concentration and pressure.

These are host-rock variants, not new lithologies or ores. Semantic geology remains `shale` or `sandstone`, so stratigraphy and metamorphism do not acquire petroleum-specific rock types. Mining returns the ordinary host rather than crude, bitumen or a petroleum item. Both are exposed through `geostrata:petroleum_bearing_rocks`.

### Create: Diesel Generators

On Fabric 1.20.1, GeoStrata integrates with Create: Diesel Generators (`createdieselgenerators`) without a hard compile dependency.

CDG already owns the correct crude-oil fluid, bucket transfer behavior, pumpjack, refining and its persistent per-chunk oil store. GeoStrata therefore does not register a second crude fluid or pump system.

When CDG is present:

- GeoStrata seeds CDG's native `OilChunksSavedData` when a chunk is first encountered;
- five cached geology samples across the chunk resolve the richest intersecting GeoStrata reservoir;
- reservoir pressure and concentration map into CDG's native Fabric 1.20.1 rich-deposit range of roughly `8,000` to `400,000` mB;
- a geologically dry chunk is explicitly stored as `0`, preventing CDG's unrelated biome/RNG oil placement from becoming a second occurrence model;
- GeoStrata only writes when CDG reports `-1` (uninitialized). Existing positive, zero or depleted values are never overwritten, so pumpjack depletion remains authoritative;
- CDG's `oil_deposit` tag is vanilla bedrock, so its normal pipe-to-bedrock pumpjack validation, pumping cadence, fluid tank and depletion path remain unchanged;
- CDG's optional infinite-deposit mode still makes positive petroleum chunks infinite, while a tiny optional compatibility mixin preserves stored geological `0` chunks as dry instead of turning them into infinite wells;
- reservoirs with pressure at least `0.90` may materialize CDG's native `crude_oil` source block at the seep. Because this is CDG's own source fluid, ordinary CDG bucket filling works without compatibility recipes or a duplicate bucket item.

This is the intended ownership split: GeoStrata decides **where petroleum exists and how rich the geology is**; Create: Diesel Generators decides **how the oil is pumped, bucketed, processed and consumed**.

## Firedamp mist

Deep sedimentary geology exposes a sparse deterministic gas-proneness signal. Mudrock is favored most strongly, followed by silt-rich sediment, carbonate, sandstone and coarse clastics. Burial increases the signal; shallow geology does not display firedamp.

Players in sufficiently gas-prone underground locations may see a small amount of low-lying cloud-like mist. The implementation deliberately stops at the clue: no gas block/fluid, cave flood-fill, poisoning, explosions or finite gas state. Nearby fire, lit campfires and lava suppress the local presentation.

## Coal haze

Underground players near meaningful coal concentrations receive a light ash haze. Vanilla coal ore is a small signal, GeoStrata graded coal scales with base yield, and natural coal blocks are a strong signal. The bounded 13 x 9 x 13 scan runs once per second and never loads chunks.

## Performance boundary

The petroleum system reuses existing geology work:

- no second petroleum worldgen pass;
- no GeoStrata per-chunk petroleum save state;
- petroleum-bearing shale/sands are selected inside the existing correlated sedimentary replacement pass;
- stain, bitumen and rare free crude are tiny lazy seep writes;
- the CDG bridge runs only when CDG is installed and only for CDG-uninitialized chunks;
- one prepared semantic geology context is reused for the bridge's five chunk samples;
- once CDG has any saved value, future chunk loads do no geology work for that CDG deposit;
- CDG itself owns depletion, pumpjack behavior and crude-fluid storage.

Future indicators and integrations should query the existing semantic geology and avoid creating parallel occurrence models.
