# GeoStrata experiment companion

This subproject builds `geostrata-correlated-experiment`, an **optional experimental Fabric mod** that depends on the normal GeoStrata jar. The artifact and mod ID keep their existing names for compatibility, but the companion is now the single explicit switch for GeoStrata worldgen that is not ready for normal installs.

Installing the companion activates the currently implemented experimental systems:

1. correlated sedimentary worldgen and its terrain-aware succession/metamorphism runtime;
2. GeoStrata graded ore-deposit placement, including the orogenic emerald occurrence;
3. diamond geology: deep structural corridors plus rare kimberlite/lamproite intrusive events.

Core remains authoritative for all geology data and tuning. The companion does not copy catalogs or replace the experiment JSON resources; Fabric Loader's native mod-presence check promotes the validated snapshots to `experimental_runtime`. It only registers the correlated/background placed features itself because ore and diamond experimental placed features are already registered by core and gated by their runtime snapshots.

Normal GeoStrata installs remain unchanged. The companion suppresses vanilla Overworld placed features only for the common resources whose GeoStrata replacements have passed the paired discoverability benchmark: coal, iron and copper. Experimental gold, emerald and diamond geology still runs, but vanilla generation for those rarer resources remains as a fallback until replacement coverage has been demonstrated. Redstone and lapis likewise remain Minecraft-owned.

This deliberately allows duplicate rare-resource generation in disposable validation worlds. That is preferable to deleting a vanilla resource before the experimental replacement has proven adequate availability. Suppression can be expanded material-by-material once benchmark evidence supports it.

## Testing

Build both artifacts with:

```text
./gradlew clean build :experiment-companion:build
```

Install the normal GeoStrata jar from `build/libs/` and the companion jar from `experiment-companion/build/libs/`, then create a **fresh or disposable world**. Existing chunks will not be regenerated.

Useful checks for this test pass:

- cave/cliff exposures show coherent correlated beds rather than unrelated local blobs;
- coal/iron/copper graded deposits replace their vanilla Overworld generation while retaining discoverability;
- orogenic mountain terrain can produce graded emerald veins in the intended host rocks alongside the vanilla fallback;
- experimental gold deposits remain geology-qualified and host-aware alongside the vanilla fallback;
- cratonic interiors can produce deep diamond structural corridors and rare kimberlite/lamproite pipes without removing ordinary vanilla diamonds;
- redstone and lapis remain available through vanilla generation;
- generation remains deterministic for a fixed seed and does not overwrite bedrock, caves, fluids, structures or unrelated blocks.

Do not use the companion for a long-lived survival world yet. Its purpose is to make the current advanced worldgen reachable for evaluation while production defaults stay conservative.
