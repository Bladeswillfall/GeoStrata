# GeoStrata experimental geology companion

This subproject builds `geostrata-correlated-experiment`, an **optional experimental Fabric mod** that depends on the normal GeoStrata jar. The historical mod ID is intentionally retained so existing test setups do not break.

Installing the companion is the explicit opt-in switch for GeoStrata worldgen that is still being evaluated. It currently:

1. registers `geostrata:correlated_sedimentary_experiment` into biomes tagged `geostrata:has_common_rocks` at `UNDERGROUND_DECORATION`;
2. promotes the validated correlated-strata snapshot to `experimental_runtime`;
3. promotes the shared graded ore-deposit snapshot to `experimental_runtime`, including the orogenic emerald occurrence;
4. promotes the diamond geology snapshot to `experimental_runtime`, enabling deep structural diamond corridors and rare kimberlite/lamproite events.

The core mod already owns registration of the ore and diamond placed features. The companion does not copy their policies or register a parallel generator; its presence only promotes their validated, disabled-by-default snapshots. Activation uses Fabric Loader's native mod-presence check, so there is no second config or resource-order contract.

The companion changes world generation. Test it in a **fresh or disposable world**. Removing it restores standalone GeoStrata's disabled experimental snapshots but does not undo blocks already generated in existing chunks.

Useful diagnostics in the core mod include `/geostrata province`, `/geostrata terrain`, `/geostrata experiment`, `/geostrata ore <material>` and `/geostrata ore <material> candidate`. For emerald, use `/geostrata ore emerald` and `/geostrata ore emerald candidate` while checking mountainous/orogenic terrain.

Core remains authoritative for experiment tuning, province rules, host lithologies, vertical domains, ore economy and native-generation suppression policy. Vanilla ore generation remains unsuppressed during this test phase.

Production Java in this subproject uses the same PMD cyclomatic and cognitive complexity ceiling as the core mod. It is a separate artifact for behavior/compatibility reasons, not a separate engineering standard.
