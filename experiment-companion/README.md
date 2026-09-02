# GeoStrata experiment companion

This subproject builds `geostrata-correlated-experiment`, an **optional experimental Fabric mod** that depends on the normal GeoStrata jar. The artifact and mod ID keep their existing names for compatibility, but correlated/province host geology now belongs to core GeoStrata.

Installing the companion activates the worldgen systems that are still experimental:

1. GeoStrata graded ore-deposit placement, including the orogenic emerald occurrence;
2. diamond geology: deep structural corridors plus rare kimberlite/lamproite intrusive events;
3. ore/debug commands used for validation.

Core owns the geology catalogs, province/succession runtime, correlated sedimentary placement and province-background replacement. The companion does not register those geology features or remove the legacy fallback rock blobs; normal core does that itself. Fabric Loader's native mod-presence check still activates the ore and diamond experiment snapshots without copying their data resources.

The companion suppresses vanilla Overworld placed features only for the common resources whose GeoStrata replacements have passed the paired discoverability benchmark: coal, iron and copper. Experimental gold, emerald and diamond geology still runs, but vanilla generation for those rarer resources remains as a fallback until replacement coverage has been demonstrated. Redstone and lapis likewise remain Minecraft-owned.

This deliberately allows duplicate rare-resource generation in disposable validation worlds. That is preferable to deleting a vanilla resource before the experimental replacement has proven adequate availability. Suppression can be expanded material-by-material once benchmark evidence supports it.

The CI ore benchmark has one narrow exception for measurement only. When `GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND=true` is present, the companion also removes vanilla diamond placed features so the benchmark can attribute vanilla diamond-block IDs to GeoStrata's structural/pipe routes without ambiguity. Normal companion launches do not set this environment variable and therefore keep ordinary vanilla diamonds.

## Testing

Build both artifacts with:

```text
./gradlew clean build :experiment-companion:build
```

Install the normal GeoStrata jar from `build/libs/` for core correlated geology. Add the companion jar from `experiment-companion/build/libs/` only when testing experimental ore/diamond generation, then create a **fresh or disposable world**. Existing chunks will not be regenerated.

Useful checks for this test pass:

- core-only cave/cliff exposures show coherent correlated beds rather than unrelated local blobs;
- with the companion installed, coal/iron/copper graded deposits replace their vanilla Overworld generation while retaining discoverability;
- orogenic mountain terrain can produce graded emerald veins in the intended host rocks alongside the vanilla fallback;
- experimental gold deposits remain geology-qualified and host-aware alongside the vanilla fallback;
- cratonic interiors can produce deep diamond structural corridors and rare kimberlite/lamproite pipes without removing ordinary vanilla diamonds;
- redstone and lapis remain available through vanilla generation;
- generation remains deterministic for a fixed seed and does not overwrite bedrock, caves, fluids, structures or unrelated blocks.

Do not use the companion for a long-lived survival world yet. Its purpose is to expose the ore and diamond systems that still need replacement-readiness evidence; the correlated/province geology underneath them is now part of normal GeoStrata worldgen.
