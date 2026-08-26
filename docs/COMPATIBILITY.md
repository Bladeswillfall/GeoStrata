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

## Streams Reflowing

GeoStrata ships a data-only bridge for Streams Reflowing under `data/geostrata/streamsreflowing/bank_style/`. Streams Reflowing discovers bank-style JSON from other mod namespaces when it is installed; ordinary Minecraft and GeoStrata never load Java classes from Streams Reflowing.

The core bridge is intentionally conservative:

- fluvial biomes can use silty loam and blue clay in stream beds/waterlines;
- swamp biomes can use wet mud and peat soil;
- jungle biomes can use compacted/wet mud;
- cut banks remain natural (`bank_enabled: false`), so erosion exposes the lithology GeoStrata actually generated instead of painting a biome-selected rock over it;
- selectors use GeoStrata-owned biome tags plus `minecraft:is_overworld`, allowing biome mods/modpacks to opt in by extending GeoStrata tags;
- core does not contest Streams Reflowing's exact-biome presets. The curated GeoStrata pack may provide stronger pack-level choices later, but the standalone jar stays cooperative.

This arrangement is deliberately asymmetric: Streams Reflowing being absent changes nothing about GeoStrata, while its presence discovers extra GeoStrata-aware sediment styling automatically.

## Integration rule

Prefer, in order:

1. extend GeoStrata tags from a datapack;
2. add an optional compatibility data/resource artifact;
3. add guarded Java integration only when behavior cannot be expressed through data.

The standalone jar must never require a compatibility artifact to load.
