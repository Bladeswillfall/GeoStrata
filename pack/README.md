# GeoStrata development pack

This directory contains the **curated development/integration pack**, not the GeoStrata mod itself.

The mod source remains at repository root (`src/`, Gradle files and `src/main/resources`). GeoStrata core must continue to build and run without the mods listed by this pack.

## Layout

- `manifest.json` — CurseForge dependency manifest for the current Fabric 1.20.1 integration environment.
- `dependencies.json` — human-readable inventory paired to every numeric CurseForge project/file entry in the manifest.
- `overrides/config/` — intentionally maintained mod configuration.
- `overrides/kubejs/` — intentionally maintained KubeJS integration/customization.
- `instance.png` — development pack icon.
- `reference/` — generated/reference exports that are useful while auditing the pack but are not runtime overrides.
- `UPGRADE_NOTES.md` — dated dependency decisions that should not be lost inside raw file IDs.

## Validation

Run the pack contract check from repository root:

```text
python3 scripts/validate_pack_manifest.py
```

CI runs this before the Java build. `manifest.json` and `dependencies.json` must describe the same project IDs, file IDs and required flags. This makes dependency changes reviewable instead of leaving unexplained numbers in the export.

## GeoStrata jar

The GeoStrata jar is deliberately **not committed** into this directory. Build it from the repository and inject the resulting release/dev jar into the assembled pack. This keeps the mod source authoritative and avoids binary drift.

## Compatibility rule

A mod being present in this development pack does not make it a GeoStrata core dependency. Integration-specific behavior belongs in tags, datapacks, guarded adapters, or separate compatibility artifacts.

Conquest Reforged is explicitly classified as integration content. Fabric API is the only CurseForge project in this pack inventory classified as a GeoStrata core dependency; Fabric Loader is declared separately by the manifest loader entry.

The current manifest was inherited from the previous Conquest-based instance and still needs a dependency-by-dependency freshness/config audit. Its structure is deterministic and now machine-validated, but its current pins should not yet be treated as the final recommended public pack.
