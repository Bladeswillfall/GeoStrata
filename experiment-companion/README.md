# GeoStrata correlated experiment companion

This subproject builds `geostrata-correlated-experiment`, an **optional experimental Fabric mod** that depends on the normal GeoStrata jar.

Installing the companion does two things that standalone GeoStrata deliberately does not do:

1. it registers `geostrata:correlated_sedimentary_experiment` into biomes tagged `geostrata:has_common_rocks` at `UNDERGROUND_DECORATION`;
2. its Fabric mod ID lets core promote the already validated experiment snapshot to `experimental_runtime`.

Activation uses Fabric Loader's native mod-presence check. There is no activation JSON or resource-order contract.

The companion therefore changes world generation. It is not included in the normal development-pack dependency set and should be evaluated in fresh or disposable worlds until the correlated generator is promoted beyond experiment status.

The companion contains no geological catalog or copied experiment policy of its own. Core remains authoritative for target succession, provinces, lithologies, ownership boundary, host tag and vertical window. The actual-resource JUnit smoke test enforces that separation in CI.

Production Java in this subproject uses the same PMD cyclomatic and cognitive complexity ceiling as the core mod. It is a separate artifact for behavior/compatibility reasons, not a separate engineering standard.
