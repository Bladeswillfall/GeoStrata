# GeoStrata compatibility extension points

GeoStrata is designed to remain useful on ordinary Fabric Minecraft while allowing modpacks and compatibility artifacts to teach its worldgen about additional terrain and content.

## Worldgen replacement tags

Core configured features target GeoStrata-owned block tags rather than third-party block IDs. The default contents reproduce vanilla behavior; another datapack or mod may append compatible blocks with `replace: false`.

| Tag | Meaning | Vanilla default |
| --- | --- | --- |
| `geostrata:worldgen/base_stone_replaceables` | Blocks that may be replaced by GeoStrata rock bodies | `#minecraft:stone_ore_replaceables`, `#minecraft:deepslate_ore_replaceables` |
| `geostrata:worldgen/soil_replaceables` | Ordinary soil host material for loam/peat patches | `minecraft:dirt` |
| `geostrata:worldgen/mud_replaceables` | Mud-like host material | `minecraft:mud` |
| `geostrata:worldgen/clay_replaceables` | Clay-like host material | `minecraft:clay` |

These tags are deliberately conservative. Core should not add blocks from optional mods to them.

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

## Biome targeting

GeoStrata also owns biome tags such as `geostrata:has_mountain_rocks`, `geostrata:has_fluvial_rocks`, and `geostrata:has_river_soils`. Integrations should extend those tags when a mod introduces biomes GeoStrata should recognize.

## Material profile LUT

`data/geostrata/materials/material_profiles.json` is the human-facing index of GeoStrata materials. It covers every registered block, including derived limestone shapes, and declares:

| Field | Purpose |
| --- | --- |
| `primaryBlock` / `derivedBlocks` | Registry IDs owned by one material profile |
| `family` | Broad rock, soil, mud or clay classification |
| `compatibilityRole` | Stable semantic key an optional adapter can map to another mod's equivalent material |
| `semanticTags` | Current GeoStrata tags describing the primary block |
| `gameplay.breaking` | Live copy source, hardness, blast resistance, tool/tier and sound settings |
| `gameplay.cultivation` | Explicit implementation status for soil-related behavior |
| `assets.textureSet` | Current textures and whether they are placeholders or production assets |

The catalog is validated metadata, not a runtime-reloadable settings file. CI checks it against `GeoStrataBlocks.java`, mining tags, block models and texture files, so a trait or asset change must update both the implementation and its profile. In particular, `cultivation.status: not_implemented` means GeoStrata does not yet alter crop growth; it is not a hidden multiplier.

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
