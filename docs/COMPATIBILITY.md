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

## Integration rule

Prefer, in order:

1. extend GeoStrata tags from a datapack;
2. add an optional compatibility data/resource artifact;
3. add guarded Java integration only when behavior cannot be expressed through data.

The standalone jar must never require a compatibility artifact to load.
