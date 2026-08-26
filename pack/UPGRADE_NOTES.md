# Development-pack dependency upgrade notes

This file records dependency decisions that are intentionally not obvious from the numeric CurseForge manifest.

## 2026-08-26 — Streams Reflowing

The intended hydrology integration is **StreamsReflowing-1.20.1-fabric-2.12.8.jar** from CurseForge project `1581408`. The artifact has been inspected directly and is a Fabric 1.20.1 build of Streams Reflowing 2.12.8. Its own metadata requires Fabric Loader `>=0.16.9`, Fabric API, Minecraft 1.20.1 and Java 17+; the development pack currently declares Fabric Loader 0.16.11.

Verified artifact identity:

- filename: `StreamsReflowing-1.20.1-fabric-2.12.8.jar`
- size: `2591301` bytes
- SHA-256: `23af39c62723cf8c2fd63231c0afc71d3c27594230d84fe328334e79be7ee678`
- mod ID: `streamsreflowing`
- version: `2.12.8`

CurseForge's public file listing identifies this exact 1.20.1 Fabric release but does not currently expose its numeric file ID through the indexed listing available to the repository tooling. The project is therefore **not added to `manifest.json` with a guessed ID**. `artifact-locks.json` records the verified binary until the file ID can be independently confirmed, after which the lock and active manifest/inventory must be updated together.

GeoStrata core already carries a data-only Streams Reflowing bank-style bridge. That bridge remains inert when Streams Reflowing is absent and introduces no Java dependency.

Current public listing:

- https://www.curseforge.com/minecraft/mc-mods/streams-reflowing/files/all

## 2026-08-26 — Conquest Reforged

The checked-in development pack currently pins Conquest Reforged Fabric 1.20.1 **1.4.1.4** (`projectID: 250077`, `fileID: 7208296`).

As of 2026-08-26, CurseForge lists **ConquestReforged-fabric-1.20.1-1.7.0** (`fileID: 8702617`) as the current Fabric 1.20.1 release. The current project dependency page lists six required projects: Entity Model Features (EMF), Entity Texture Features (ETF), BetterGrassify, Continuity, ForgeSkyboxes and Polytone.

The inherited GeoStrata manifest already contains EMF, ETF and Polytone, but it does not currently contain BetterGrassify, Continuity or ForgeSkyboxes. Therefore the Conquest file is **not upgraded in isolation**. The upgrade should happen as one tested dependency-set change so the development pack cannot become formally incomplete.

Sources used for this dated audit:

- https://www.curseforge.com/minecraft/mc-mods/conquest-reforged/files/8702617
- https://www.curseforge.com/minecraft/mc-mods/conquest-reforged/relations/dependencies

When this upgrade is performed, update both `manifest.json` and `dependencies.json`; CI will reject a one-sided edit.
