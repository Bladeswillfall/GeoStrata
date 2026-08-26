# GeoStrata v0.1 (Bootstrap)

GeoStrata v0.1 is a **bootstrap Fabric mod shell** for Minecraft 1.20.1 that establishes the pack-level contract and ore/material policy scope without introducing invasive runtime logic yet.

## What the mod does right now

- Registers as a Fabric mod (`geostrata`) so it can ship and version integration policy as a mod artifact.
- Packages a machine-readable scope document (`v0_1_scope.json`) that defines layer ownership boundaries:
  - Tectonic = macro terrain
  - Terralith = biome shell
  - Conquest Reforged = visual language
  - Distant Horizons = long-range readability
  - GeoStrata = subsurface/ore authority
- Packages unification targets (`material_unification_targets.json`) for shared materials in v0.1:
  - coal, iron, copper, gold
  - canonical vanilla item families as the initial economy anchors

## What is intentionally deferred

- New geological block families and textures
- Grade-specific drops / XP scaling
- Create processing integration and mod-material activation

## Build artifact

After running `gradle clean jar`, the produced mod jar is:

- `build/libs/geostrata-0.1.0.jar`
