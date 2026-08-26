# Development-pack dependency upgrade notes

This file records dependency decisions that are intentionally not obvious from the numeric CurseForge manifest.

## 2026-08-26 — Conquest Reforged

The checked-in development pack currently pins Conquest Reforged Fabric 1.20.1 **1.4.1.4** (`projectID: 250077`, `fileID: 7208296`).

As of 2026-08-26, CurseForge lists **ConquestReforged-fabric-1.20.1-1.7.0** (`fileID: 8702617`) as the current Fabric 1.20.1 release. The current project dependency page lists six required projects: Entity Model Features (EMF), Entity Texture Features (ETF), BetterGrassify, Continuity, ForgeSkyboxes and Polytone.

The inherited GeoStrata manifest already contains EMF, ETF and Polytone, but it does not currently contain BetterGrassify, Continuity or ForgeSkyboxes. Therefore the Conquest file is **not upgraded in isolation**. The upgrade should happen as one tested dependency-set change so the development pack cannot become formally incomplete.

Sources used for this dated audit:

- https://www.curseforge.com/minecraft/mc-mods/conquest-reforged/files/8702617
- https://www.curseforge.com/minecraft/mc-mods/conquest-reforged/relations/dependencies

When this upgrade is performed, update both `manifest.json` and `dependencies.json`; CI will reject a one-sided edit.
