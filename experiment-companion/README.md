# GeoStrata experiment companion

This subproject builds `geostrata-correlated-experiment`, an **optional experimental Fabric mod** that depends on the normal GeoStrata jar. The artifact and mod ID keep their existing names for compatibility, but correlated/province host geology and proven common ore ownership now belong to core GeoStrata.

Installing the companion activates only the worldgen systems that are still experimental:

1. the remaining graded ore occurrences, currently gold and orogenic emerald;
2. diamond geology: deep structural corridors plus rare kimberlite/lamproite intrusive events;
3. ore/debug commands used for validation.

Core owns the geology catalogs, province/succession runtime, correlated sedimentary placement, province-background replacement, and graded coal/iron/copper generation. On Fabric, core also suppresses the corresponding vanilla Overworld coal/iron/copper placed features. The companion does not duplicate any of those responsibilities.

Experimental gold, emerald and diamond geology still runs alongside vanilla generation for those rarer resources until replacement coverage has been demonstrated. Redstone and lapis likewise remain Minecraft-owned. This deliberately allows duplicate rare-resource generation in disposable validation worlds rather than deleting a vanilla resource before the experimental replacement has proven adequate availability.

The CI ore benchmark has one narrow exception for measurement only. When `GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND=true` is present, the companion removes vanilla diamond placed features so the benchmark can attribute vanilla diamond-block IDs to GeoStrata's structural/pipe routes without ambiguity. Normal companion launches do not set this environment variable and therefore keep ordinary vanilla diamonds.

## Testing

Build both artifacts with:

```text
./gradlew clean build :experiment-companion:build
```

Install the normal GeoStrata jar from `build/libs/` for core geology and graded coal/iron/copper ownership. Add the companion jar from `experiment-companion/build/libs/` only when testing experimental gold/emerald/diamond generation, then create a **fresh or disposable world**. Existing chunks will not be regenerated.

Useful checks for this test pass:

- core-only cave/cliff exposures show coherent correlated beds rather than unrelated local blobs;
- core-only coal/iron/copper graded deposits replace their vanilla Overworld generation while retaining discoverability;
- with the companion installed, orogenic mountain terrain can produce graded emerald veins in the intended host rocks alongside the vanilla fallback;
- experimental gold deposits remain geology-qualified and host-aware alongside the vanilla fallback;
- cratonic interiors can produce deep diamond structural corridors and rare kimberlite/lamproite pipes without removing ordinary vanilla diamonds;
- redstone and lapis remain available through vanilla generation;
- generation remains deterministic for a fixed seed and does not overwrite bedrock, caves, fluids, structures or unrelated blocks.

Do not use the companion for a long-lived survival world yet. Its purpose is to expose the rare ore and diamond systems that still need replacement-readiness evidence; the correlated/province geology and common mining loop underneath them are now part of normal GeoStrata worldgen.
