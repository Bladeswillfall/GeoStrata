# GeoStrata development pack

This directory contains the **curated development/integration pack**, not the GeoStrata mod itself.

The mod source remains at repository root (`src/`, Gradle files and `src/main/resources`). GeoStrata core must continue to build and run without the mods listed by this pack.

## Layout

- `manifest.json` — CurseForge dependency manifest for the current Fabric 1.20.1 integration environment.
- `dependencies.json` — human-readable inventory paired to every numeric CurseForge project/file entry in the manifest.
- `artifact-locks.json` — cryptographic identity for verified integration jars that cannot yet be represented safely in the active CurseForge manifest.
- `overrides/config/` — intentionally maintained mod configuration.
- `instance.png` — development pack icon.
- `UPGRADE_NOTES.md` — dated dependency decisions that should not be lost inside raw file IDs.

## Validation

Run the pack contract check from repository root:

```text
python3 scripts/validate_pack_manifest.py
```

CI runs this before the Java build. `manifest.json` and `dependencies.json` must describe the same project IDs, file IDs and required flags. Artifact locks are also checked against the active manifest. This makes dependency changes reviewable instead of leaving unexplained numbers in the export.

## GeoStrata jar

The GeoStrata jar is deliberately **not committed** into this directory. Build it from the repository and inject the resulting release/dev jar into the assembled pack. This keeps the mod source authoritative and avoids binary drift.

Third-party mod jars are likewise not committed. A verified artifact may be recorded in `artifact-locks.json` while its distribution pin is unresolved, but a pending lock is not treated as an installed pack dependency.

## Compatibility rule

A mod being present in this development pack does not make it a GeoStrata core dependency. Integration-specific behavior belongs in tags, datapacks, guarded adapters, or separate compatibility artifacts.

Conquest Reforged is explicitly classified as integration content. Fabric API is the only CurseForge project in this pack inventory classified as a GeoStrata core dependency; Fabric Loader is declared separately by the manifest loader entry.

The current pack is a deliberately conservative integration baseline: Conquest has been advanced to the 1.7.0 Fabric line with its required visual-support surface, while unrelated performance/UI pins remain on the previously green 1.20.1 set until they are audited independently.
