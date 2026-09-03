# Geological indicators

GeoStrata exposes subtle player-facing signs of geology without adding a second terrain-generation system.

## Hydrocarbon reservoirs and oil seeps

`HydrocarbonReservoirField` is a deterministic semantic field keyed by world seed and 384-block cells. A reservoir can only exist where the resolved deep lithology is sedimentary. Source-rich mudrocks are favored, followed by carbonates, sandstone, silt-rich sediments and coarse clastics.

The field does not place an oil fluid or invent a processing chain. That is deliberate: GeoStrata owns the geological occurrence, while a future optional petroleum mod can own fluids, pumps and refining. The returned reservoir record provides stable center/radius, pressure, concentration and seep coordinates for those integrations.

Sufficiently pressured reservoirs now leave two linked clues at their deterministic surface seep point while a player is nearby:

- sparse black seep/smoke particles show that the seep is active;
- a persistent `petroleum_stain` multiface block marks a small deterministic patch of affected ground and exposed rock.

`petroleum_stain` deliberately reuses Minecraft's native sculk-vein / lichen-style multiface behavior instead of replacing every possible host with a petroleum-specific block. One stain block can cling to several adjacent faces, including sloped terrain and exposed rock edges.

The patch uses only existing vanilla dark/brown textures and thin model geometry, so no custom renderer or host-by-host texture matrix is required. It attaches only to plausible natural supports: dirt, sand, clay, mud, gravel, vanilla Overworld base stone and GeoStrata rock blocks. It does not replace vegetation or arbitrary occupied blocks.

The stain is materialized lazily from the same seep query that already drives particles. Once placed it persists normally in the world, and repeated checks become read-only. This gives existing saves the physical clue without adding another chunk-generation pass.

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

These indicators are deliberately post-generation and local:

- no new worldgen pass;
- no persistent per-chunk geology state;
- no per-tick full-radius scan;
- hydrocarbon geometry and gas potential are reconstructed from seed + geology;
- petroleum stain writes are bounded to a tiny patch and stop once the deterministic faces already exist;
- coal haze only examines a small loaded neighborhood once per second;
- firedamp scans for nearby heat sources only after a rare gas-prone geology match;
- particles and stain blocks are evidence, not geological authority.

Future indicators should reuse this pattern: query existing semantic geology or generated blocks, remain deterministic where appropriate, and avoid creating a parallel geology model.
