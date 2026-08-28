# Vanilla stone compatibility

GeoStrata keeps Minecraft's vanilla stone building vocabulary intact while replacing natural underground host stone with geological lithologies.

## Default simple mode

- Natural GeoStrata rock blocks drop `minecraft:cobblestone` when mined normally.
- Silk Touch drops the exact GeoStrata rock block that was mined.
- GeoStrata rock items are exposed through `#geostrata:rocks`.
- `#geostrata:rocks` is appended to Minecraft 1.20.1's `#minecraft:stone_tool_materials` and `#minecraft:stone_crafting_materials` item tags.
- Vanilla `minecraft:stone`, `minecraft:cobblestone`, `minecraft:smooth_stone`, stone bricks and their normal processing/crafting chains remain unchanged.
- No custom Java logic is required for this compatibility layer.

This deliberately mirrors the useful part of vanilla stone behavior: ordinary mining feeds the generic cobblestone economy, while Silk Touch preserves the geological building block.

## Possible alternate mode: lithology-specific cobble

A future optional variant may add cobbled forms such as limestone cobble, shale cobble, basalt cobble, and similar per-lithology building blocks.

That is not part of the default mode. If added later, it should be optional and preserve an easy path back to vanilla cobblestone so existing recipes, structures, schematics, and mods remain compatible.

## Separate worldgen hardening

Before the correlated experiment becomes the default generator, verify that late geological replacement does not rewrite raw `minecraft:stone` intentionally placed by structures. Cobblestone, stone bricks, smooth stone, and other masonry are already outside GeoStrata's base-stone replacement host tag.
