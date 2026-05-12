# Runtime Development Notes

GeoStrata runtime development should use the normal Fabric Loom source path.

- Build with `gradle build`.
- Runtime blocks are registered in `src/main/java/com/geostrata/block/GeoStrataBlocks.java`.
- Placeholder textures live in `src/main/resources/assets/geostrata/textures/block`.
- Do not maintain the reflective bootstrap jar path except as an emergency smoke-test artifact.
- Keep Conquest Reforged optional. Base GeoStrata block IDs must exist without CR installed.
