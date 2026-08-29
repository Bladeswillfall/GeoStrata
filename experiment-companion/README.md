# GeoStrata experiment companion

This subproject builds `geostrata-correlated-experiment`, an **optional experimental Fabric mod** that depends on the normal GeoStrata jar. The artifact and mod ID keep their existing names for compatibility, but the companion is now the single explicit switch for GeoStrata worldgen that is not ready for normal installs.

Installing the companion activates the currently implemented experimental systems:

1. correlated sedimentary worldgen and its terrain-aware succession/metamorphism runtime;
2. GeoStrata graded ore-deposit placement, including the orogenic emerald occurrence;
3. diamond geology: deep structural corridors plus rare kimberlite/lamproite intrusive events.

Core remains authoritative for all geology data and tuning. The companion does not copy catalogs or replace the experiment JSON resources; Fabric Loader's native mod-presence check promotes the validated snapshots to `experimental_runtime`. It only registers the correlated placed feature itself because ore and diamond experimental placed features are already registered by core and gated by their runtime snapshots.

Normal GeoStrata installs remain unchanged. Vanilla ore and diamond generation are **not suppressed** by this companion, so current test worlds intentionally contain both vanilla generation and GeoStrata's experimental occurrences.

## Testing

Build both artifacts with:

```text
./gradlew clean build :experiment-companion:build
```

Install the normal GeoStrata jar from `build/libs/` and the companion jar from `experiment-companion/build/libs/`, then create a **fresh or disposable world**. Existing chunks will not be regenerated.

Useful checks for this test pass:

- cave/cliff exposures show coherent correlated beds rather than unrelated local blobs;
- orogenic mountain terrain can produce graded emerald veins in the intended host rocks;
- coal/iron/copper/gold/emerald deposits remain geology-qualified and host-aware;
- cratonic interiors can produce deep diamond structural corridors and rare kimberlite/lamproite pipes;
- generation remains deterministic for a fixed seed and does not overwrite bedrock, caves, fluids, structures or unrelated blocks.

Do not use the companion for a long-lived survival world yet. Its purpose is to make the current advanced worldgen reachable for evaluation while production defaults stay conservative.
