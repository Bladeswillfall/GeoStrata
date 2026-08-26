# GeoStrata development pack

This directory contains the **curated development/integration pack**, not the GeoStrata mod itself.

The mod source remains at repository root (`src/`, Gradle files and `src/main/resources`). GeoStrata core must continue to build and run without the mods listed by this pack.

## Layout

- `manifest.json` — CurseForge dependency manifest for the current Fabric 1.20.1 integration environment.
- `overrides/config/` — intentionally maintained mod configuration.
- `overrides/kubejs/` — intentionally maintained KubeJS integration/customization.
- `instance.png` — development pack icon.
- `reference/` — generated/reference exports that are useful while auditing the pack but are not runtime overrides.

## GeoStrata jar

The GeoStrata jar is deliberately **not committed** into this directory. Build it from the repository and inject the resulting release/dev jar into the assembled pack. This keeps the mod source authoritative and avoids binary drift.

## Compatibility rule

A mod being present in this development pack does not make it a GeoStrata core dependency. Integration-specific behavior belongs in tags, datapacks, guarded adapters, or separate compatibility artifacts.

The current manifest was inherited from the previous Conquest-based instance and still needs a dependency-by-dependency audit. Its location and override structure are now deterministic; its contents should not yet be treated as the final recommended pack.
