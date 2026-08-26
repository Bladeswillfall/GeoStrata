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

GeoStrata core already carries a data-only Streams Reflowing bank-style/tag bridge. That bridge remains inert when Streams Reflowing is absent and introduces no Java dependency.

Current public listing:

- https://www.curseforge.com/minecraft/mc-mods/streams-reflowing/files/all

## 2026-08-26 — Conquest Reforged 1.7.0 Fabric baseline

The development pack now pins **ConquestReforged-fabric-1.20.1-1.7.0** (`projectID: 250077`, `fileID: 8702617`). Conquest's official Fabric modpack release for the same version is `CR-Modpack-fabric-1.20.1-1.7.0` (`projectID: 256916`, `fileID: 8702688`), released 2026-08-21.

The official Fabric pack relation list contains 28 CurseForge projects. Its visible first page matches the existing GeoStrata pack baseline through Nuit. Conquest's own 1.5.x changelog states that **Puzzle was added to both Fabric and Forge modpacks**, so GeoStrata adds the current stable Fabric 1.20.1 Puzzle release (`projectID: 563977`, `fileID: 7394086`).

Continuity is handled deliberately rather than copied from the generic Conquest project relation page. The Fabric pack explicitly advertises Continuity support, and historical Fabric releases shipped a custom Continuity build while upstream support was still evolving. The development pack now uses the official stable Fabric/Quilt 1.20.1 release **Continuity 3.0.0+1.20.1** (`projectID: 531351`, `fileID: 5962874`) instead of relying on an opaque custom jar.

Do **not** copy the generic Conquest mod relation list literally into this Fabric pack. That page combines loader-specific alternatives and currently names ForgeSkyboxes, which is a Forge-side project. The Fabric pack already uses Nuit/FabricSkyBoxes and ArdaGrass for the equivalent Fabric visual stack. `validate_pack_manifest.py` rejects ForgeSkyboxes in this Fabric pack and requires the Fabric-side Conquest support projects while 1.7.0 is pinned.

This change intentionally does not refresh every unrelated performance/UI mod to its newest release. The purpose is to establish a coherent Conquest 1.7.0 integration baseline while keeping the rest of the previously green 1.20.1 environment stable.

Sources used for this dated audit:

- https://www.curseforge.com/minecraft/modpacks/conquest-reforged-official-modpack/files/8702688
- https://www.curseforge.com/minecraft/modpacks/conquest-reforged-official-modpack/relations/dependencies
- https://www.curseforge.com/minecraft/mc-mods/conquest-reforged/files/8702617
- https://www.curseforge.com/minecraft/mc-mods/continuity/files/5962874
- https://www.curseforge.com/minecraft/mc-mods/puzzle/files/7394086
- https://www.conquestreforged.com/news

When changing this stack again, update `manifest.json`, `dependencies.json` and this note together. CI rejects one-sided manifest/inventory edits.
