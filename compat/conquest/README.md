# Conquest Reforged compatibility

This directory is an **optional integration workspace**, not part of GeoStrata's standalone runtime resources.

`material-palettes.json` is the curated semantic contract between GeoStrata lithologies and Conquest Reforged material families. It intentionally lives outside `src/main/resources`: Conquest registry IDs must never become required inputs when the GeoStrata core jar is loaded without Conquest.

The mapping separates four roles:

- `geology` — solid materials that can represent the actual lithology in a Conquest-aware integration;
- `weathered` — exposed/weathered surface variants;
- `rubble` — debris or loose-rock variants;
- `construction` — processed building families for structures/palettes, never natural geology replacement.

Mappings are conservative. `unmapped` is a valid and preferable state when Conquest has no defensible equivalent; `partial` means a useful exposure/building family exists but no suitable solid geological substitute exists. In particular, silty soil is not treated as siltstone and decorative fantasy blocks are not promoted into geology just because their names contain a rock term.

`reference/conquest_wp.csv` is the preserved Conquest registry/WorldPainter export used to audit registry IDs. CI parses that reference and rejects palette IDs that no longer exist. The reference is not a license to copy Conquest textures/models/assets into GeoStrata; this compatibility layer references registry IDs only.

A later pack-level adapter may consume this contract when Conquest is installed. Core Java must remain unaware of Conquest classes and registry IDs.
