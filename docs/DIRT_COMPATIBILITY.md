# Vanilla dirt compatibility

GeoStrata keeps Minecraft's vanilla dirt economy intact while allowing natural surface soils to use geological soil variants.

## Default simple mode

- Clay loam, sandy loam, and silty loam drop `minecraft:dirt` when mined normally.
- Silk Touch drops the exact GeoStrata loam block that was mined.
- The three loams are appended to Minecraft 1.20.1's `#minecraft:dirt` block tag for vanilla and tag-aware plant/soil checks.
- All GeoStrata earth blocks remain shovel-mineable through the existing vanilla shovel tag integration.
- Vanilla `minecraft:dirt` and its normal crafting/building uses remain unchanged.

## Scope boundary

Peat soil, wet mud, compacted mud, blue clay, and red clay keep their own drops. They are distinct materials, not generic dirt substitutes.

No custom hoe/tilling behavior is added here. If a vanilla or modded farming action uses exact block identity instead of `#minecraft:dirt`, handle that separately only when a real compatibility need is demonstrated.
