# Sediment hydrology

GeoStrata treats water and terrain shape as geological evidence rather than hard permission gates. Fine sediments should strongly prefer places where water can transport, settle or saturate them, while gameplay still benefits from occasional deposits away from the textbook-perfect location.

## Clay water-floor contract

Blue and red clay use Minecraft's native `disk` feature instead of ore-shaped blobs. Their strongest placement path samples `OCEAN_FLOOR_WG` and requires actual water at the selected column, so a deposit is tied to generated terrain rather than inferred only from a biome name.

| Material | Strong water-floor attempt | Background attempt |
| --- | ---: | ---: |
| Blue clay | 1 in 3 chunks | 1 in 24 chunks |
| Red clay | 1 in 10 chunks | 1 in 40 chunks |

Red clay also receives an additional water-floor attempt in badlands. These are gamified-realism starting values, not claims about real-world abundance.

## Shared sediment suitability

Clay loam, silty loam, peat soil, wet mud and compacted mud now share `geostrata:sediment_suitability`, a placement modifier that gates native Minecraft disk geometry. The modifier does not place blocks itself.

For one candidate position it samples the active terrain through `OCEAN_FLOOR_WG` at the center and four cardinal points 16 blocks away. The observations reuse `TerrainMorphologySample` to derive two normalized signals:

- `flatness` — high where local relief and slope are small;
- `valley` — high where the candidate lies below its cardinal neighbors.

Two direct contextual signals are added:

- `submerged` — whether the candidate position currently contains water;
- `preferredBiome` — whether the current biome belongs to the profile's GeoStrata-owned preferred biome tag.

The acceptance chance is intentionally simple and data-driven:

```text
base
+ flatnessWeight * flatness
+ valleyWeight * valley
+ submergedWeight * submerged
+ preferredBiomeBonus * preferredBiome
```

The result is clamped to `0..1` and compared with GeoStrata's stable coordinate hash rather than consuming Minecraft's feature RNG stream. A negative weight is allowed; compacted mud uses this to prefer exposed damp ground over a fully submerged riverbed.

The bundled starting profiles are:

| Sediment | Base | Flatness | Valley | Submerged | Preferred biome bonus |
| --- | ---: | ---: | ---: | ---: | ---: |
| Clay loam | 0.040 | 0.080 | 0.120 | 0.220 | river soils +0.300 |
| Silty loam | 0.025 | 0.100 | 0.180 | 0.250 | river soils +0.320 |
| Peat soil | 0.005 | 0.030 | 0.040 | 0.150 | swamp soils +0.550 |
| Wet mud | 0.010 | 0.050 | 0.060 | 0.350 | swamp soils +0.400 |
| Compacted mud | 0.020 | 0.050 | 0.060 | -0.120 | jungle soils +0.350 |

These values deliberately make the preferred environment a strong clue rather than a requirement. For example, a flat non-swamp peat candidate still has a small non-zero chance, while a flat wet swamp candidate is dramatically more likely.

Sandy loam remains on the existing coastal-biome baseline. Coastal evidence is a separate problem and should be migrated deliberately rather than hidden inside the first fluvial/wetland model.

## Replacement boundaries

`geostrata:worldgen/hydric_sediment_replaceables` describes natural shallow sediment that may receive transported or reworked hydric deposits. Core includes dirt, vanilla clay, sand, red sand, gravel and mud. Blue/red clay, wet mud and compacted mud use this boundary.

Clay loam, silty loam and peat currently target `geostrata:worldgen/soil_replaceables`, whose conservative vanilla default is dirt. This keeps soil-forming patches from freely painting across every sandy or gravelly bed.

`geostrata:worldgen/clay_replaceables` remains a conservative clay-material compatibility role. It is no longer the live clay-loam generation target; material equivalence and a terrain that may receive transported sediment are separate concepts.

Compatibility datapacks may extend these tags with natural terrain blocks from other generators without adding Java dependencies.

## Why the custom modifier exists now

The initial blue/red clay migration could be expressed entirely with Minecraft's existing disk, heightmap, water-predicate and rarity machinery, so no custom hydrology code was justified.

There are now several different sediment materials that need the same combination of terrain morphology, water state and soft biome preference. That repeated consumer is the reason the small shared suitability modifier now exists. Native Minecraft still owns the disk geometry; GeoStrata only supplies the geological acceptance decision that native placement primitives cannot express together.

## Direction of travel

Surface sediment evidence is now one reusable input alongside geological province, terrain morphology, host lithology and structural fields. Likely later consumers include sandy/coastal soils, floodplain sediment packages and selected sedimentary rock or mineral occurrence rules.

The same rule remains in force: migrate one family at a time, measure it in fresh worlds, and only generalize the machinery when another real consumer needs it.
