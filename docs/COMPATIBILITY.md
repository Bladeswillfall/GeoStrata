# GeoStrata compatibility extension points

GeoStrata is designed to remain useful on ordinary Fabric Minecraft while allowing modpacks and compatibility artifacts to teach its worldgen about additional terrain and content.

## Worldgen replacement tags

Core configured features target GeoStrata-owned block tags rather than third-party block IDs. The default contents reproduce vanilla behavior; another datapack or mod may append compatible blocks with `replace: false`.

| Tag | Meaning | Vanilla default |
| --- | --- | --- |
| `geostrata:worldgen/base_stone_replaceables` | Blocks that may be replaced by GeoStrata rock bodies | `#minecraft:stone_ore_replaceables`, `#minecraft:deepslate_ore_replaceables` |
| `geostrata:worldgen/soil_replaceables` | Ordinary soil host material for loam/peat patches | `minecraft:dirt` |
| `geostrata:worldgen/mud_replaceables` | Mud-like material-equivalence host | `minecraft:mud` |
| `geostrata:worldgen/clay_replaceables` | Clay-like material-equivalence host | `minecraft:clay` |
| `geostrata:worldgen/hydric_sediment_replaceables` | Natural shallow sediment that may receive transported/reworked wet deposits | dirt, clay, sand, red sand, gravel, mud |

These tags are deliberately conservative. Core should not add blocks from optional mods to them.

`hydric_sediment_replaceables` is intentionally broader than the material-equivalence tags. Blue/red clay, wet mud and compacted mud use it because transported or reworked hydric material can occupy several kinds of shallow natural sediment. Clay loam, silty loam and peat currently use the narrower `soil_replaceables` contract. `clay_replaceables` and `mud_replaceables` remain useful semantic compatibility roles even when a particular live worldgen feature no longer uses them as its placement target.

The core `base_stone_replaceables` definition has an additional geological-ownership invariant: it contains only the two vanilla ore-replaceable host-stone tags and never GeoStrata rock blocks or `#geostrata:rocks`. Once a GeoStrata body has claimed a block, a later independent body therefore cannot overwrite it through the common replacement target. The current compatibility baseline is consequently first-writer-wins at intersecting bodies; feature registration order is **not** a geological contact API and will be replaced by an explicit succession/contact planner before correlated strata become authoritative.

Third-party terrain integrations may still append genuine host stones from their own datapacks. That extension changes what natural terrain GeoStrata may replace; it should not add already-generated GeoStrata geology back into the host set.

## Example datapack extension

A terrain compatibility datapack can add its own base stone without changing GeoStrata Java or replacing GeoStrata's defaults:

```json
{
  "replace": false,
  "values": [
    "exampleterrain:limestone_base",
    "#exampleterrain:natural_stones"
  ]
}
```

Place that file at `data/geostrata/tags/blocks/worldgen/base_stone_replaceables.json` in the compatibility datapack.

## Biome targeting and evidence

GeoStrata owns biome tags such as `geostrata:has_mountain_rocks`, `geostrata:has_fluvial_rocks`, `geostrata:has_river_soils`, `geostrata:has_swamp_soils` and `geostrata:has_jungle_soils`. Integrations should extend those tags when a mod introduces biomes GeoStrata should recognize.

`geostrata:has_surface_sediments` is the broad registration boundary for evidence-driven shallow sediments and defaults to `#minecraft:is_overworld`. Clay loam, silty loam, peat, wet mud and compacted mud are registered through it. Their narrower river/swamp/jungle tags are read by `geostrata:sediment_suitability` as probability bonuses alongside terrain flatness, valley shape and actual water; they are not hard permission gates.

Blue/red clay use a separate strong-water path: the primary placement is registered broadly and requires an actual water column at `OCEAN_FLOOR_WG`. Red clay keeps an additional badlands boost, while both clays retain rare broad background placement for gameplay availability.

This distinction is intentional. A compatibility biome tag should describe useful environmental context, while generated terrain evidence remains able to influence placement even when a biome mod does not perfectly mirror vanilla categories.

## Material profile LUT

`data/geostrata/materials/material_profiles.json` is the human-facing index of GeoStrata materials. It covers every registered block, including derived limestone shapes, and declares:

| Field | Purpose |
| --- | --- |
| `primaryBlock` / `derivedBlocks` | Registry IDs owned by one material profile |
| `family` | Broad rock, soil, mud, clay or ore classification |
| `compatibilityRole` | Stable semantic key an optional adapter can map to another mod's equivalent material |
| `semanticTags` | Current GeoStrata tags describing the primary block |
| `gameplay.breaking` | Live copy source, hardness, blast resistance, tool/tier and sound settings |
| `gameplay.cultivation` | Explicit implementation status for soil-related behavior |
| `gameplay.oreEconomy` | Ore material/output identity, grade order and authoritative occurrence source |
| `assets.textureSet` | Current textures and whether they are placeholders or production assets |

The catalog is validated metadata, not a runtime-reloadable settings file. CI checks it against `GeoStrataBlocks.java`, mining tags, block models and texture files, so a trait or asset change must update both the implementation and its profile. Graded ore profiles are additionally checked against the runtime occurrence catalog and every block loot table, including fixed core base yield, output item, Fortune and Silk Touch behavior. In particular, `cultivation.status: not_implemented` means GeoStrata does not yet alter crop growth; it is not a hidden multiplier.

Compatibility artifacts should map an external block to a `compatibilityRole` or shared tag. They should not try to replace a registered GeoStrata block ID after registration. If an integration needs to adopt another mod's runtime behavior rather than merely classify equivalent blocks, that belongs in a guarded optional Java adapter because hardness and crop-growth hooks cannot be safely expressed by a core datapack alone.

## Streams Reflowing

GeoStrata ships a data-only bridge for Streams Reflowing. Streams Reflowing discovers bank-style JSON from other mod namespaces and uses ordinary Minecraft tags for several ecological/geographical decisions, so the integration requires no Streams Java classes and no hard dependency.

The core bridge is intentionally conservative:

- fluvial biomes can use silty loam and blue clay in stream beds/waterlines;
- swamp biomes can use wet mud and peat soil;
- jungle biomes can use compacted/wet mud;
- cut banks remain natural (`bank_enabled: false`), so erosion exposes the lithology GeoStrata actually generated instead of painting a biome-selected rock over it;
- selectors use GeoStrata-owned biome tags plus `minecraft:is_overworld`, allowing biome mods/modpacks to opt in by extending GeoStrata tags;
- `#streamsreflowing:rocky_banks` inherits `#geostrata:has_mountain_rocks`, so a biome classified once as a GeoStrata mountain also receives Streams' existing rocky-bank treatment;
- all GeoStrata earth blocks extend `#streamsreflowing:underwater_vegetation_floor`, allowing Streams' underwater flora system to recognize GeoStrata loams, muds, peat and clays as natural substrate;
- core does not contest Streams Reflowing's exact-biome presets. The curated GeoStrata pack may provide stronger pack-level choices later, but the standalone jar stays cooperative.

This arrangement is deliberately asymmetric: Streams Reflowing being absent changes nothing about GeoStrata, while its presence discovers extra GeoStrata-aware sediment, terrain-classification and vegetation behavior automatically.

## Conquest Reforged

Conquest remains optional integration content in the development pack. No Conquest adapter or palette contract currently ships in this repository. Add that data only with a distributable compatibility artifact that consumes it. GeoStrata's standalone jar must continue to load and generate without Conquest.

## Integration rule

Prefer, in order:

1. extend GeoStrata tags from a datapack;
2. add an optional compatibility data/resource artifact;
3. add guarded Java integration only when behavior cannot be expressed through data.

The standalone jar must never require a compatibility artifact to load.
