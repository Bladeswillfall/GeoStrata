# Sediment hydrology

GeoStrata treats water as geological evidence rather than a hard permission gate. Fine sediments such as clay should strongly prefer places where water can transport and settle them, while gameplay still benefits from occasional deposits away from an obvious river or lake.

## Current clay contract

Blue and red clay now use Minecraft's native `disk` feature instead of ore-shaped blobs. The primary placement path samples `OCEAN_FLOOR_WG` and requires actual water at the selected column, so a deposit is tied to the generated terrain rather than inferred only from a biome name.

The default strong-water attempts are deliberately different:

| Material | Strong water-floor attempt |
| --- | ---: |
| Blue clay | 1 in 3 chunks |
| Red clay | 1 in 10 chunks |

Red clay receives an additional unrestricted water-floor attempt in badlands, preserving its previous regional identity without making badlands the only place it can exist.

Both materials also have a shallow background lane with no water requirement:

| Material | Background attempt |
| --- | ---: |
| Blue clay | 1 in 24 chunks |
| Red clay | 1 in 40 chunks |

These are gamified-realism weights, not claims about real-world clay abundance. Their purpose is to make waterways the obvious place to search while preventing a world seed or settlement location from making clay effectively unavailable.

## Replacement boundary

The disk target is `geostrata:worldgen/hydric_sediment_replaceables`. Core currently includes dirt, vanilla clay, sand, red sand, gravel and mud. Compatibility datapacks may extend that tag with natural sediment blocks from terrain mods.

The older `geostrata:worldgen/clay_replaceables` tag remains the conservative clay-like replacement contract used by the existing clay-loam baseline. The two tags should not be merged simply because their names are similar: one describes material equivalence, while the hydric tag describes terrain that may reasonably receive a transported shallow sediment deposit.

## Why use vanilla machinery?

Minecraft already has the exact primitives needed for this stage: disk geometry, ocean-floor height placement, water predicates, rarity filters and biome checks. GeoStrata therefore does not add a custom Java hydrology feature just to duplicate them.

This is the intended pattern for the wider geology brain: first identify useful environmental evidence exposed by the active terrain generator or vanilla worldgen system; only add custom code when the native primitives cannot express the geological rule safely.

## Direction of travel

Hydrology should eventually become one input alongside province, terrain morphology, host lithology and structural fields. Likely future consumers include silty loam, peat, wet mud, floodplain sediments and some sedimentary rock/ore occurrence rules.

Those migrations should happen individually. This clay change does not automatically move every earth block to the same distribution, and it does not introduce a new universal moisture score before another runtime consumer actually needs one.
