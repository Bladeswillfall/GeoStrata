# GeoStrata × Conquest Reforged

This is an optional, data-only compatibility datapack for **Minecraft 1.20.1**. It targets the current Conquest Reforged 1.20.1 material palette, with **1.7.0 Fabric** as the development baseline.

## What it does

- lets GeoStrata treat a curated set of natural-looking Conquest rock blocks as eligible terrain host material through `geostrata:worldgen/base_stone_replaceables`;
- lets GeoStrata surface-sediment features recognize a small set of Conquest soils, muds, silts, sands and gravels through the existing soil/mud/hydric replacement tags;
- lets vanilla sculk replace the same curated Conquest natural-rock palette where stone would normally be replaceable.

GeoStrata remains the geological authority. This bridge does **not** make Conquest a geology generator, replace GeoStrata lithology IDs, or add a second ore/deposit system.

## Safety boundary

Every direct `conquest:*` tag entry is optional (`required: false`). If Conquest is absent, or a future Conquest release renames a block, the missing entry is ignored instead of making the datapack fail to load.

The palette deliberately excludes decorative masonry, slabs, stairs, bricks, moss/plant-covered variants, polished construction blocks and other blocks that should not be treated as raw terrain merely because their name contains a rock type.

No Conquest textures, models, recipes, code or other assets are copied or redistributed here. The bridge contains registry IDs and ordinary Minecraft tag data only.

## Installation

Install GeoStrata and Conquest Reforged normally, then add this directory as a datapack (or package it as a datapack zip without changing its internal layout).

The standalone GeoStrata jar does not require this bridge or Conquest Reforged.
