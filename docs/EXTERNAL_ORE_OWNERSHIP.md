# External ore ownership

GeoStrata owns **natural geological occurrence**, not another mod's processing economy.

For an external material, the runtime contract is:

1. GeoStrata defines one canonical geological occurrence for the material.
2. Provider availability is resolved once during geology-data reload.
3. An occurrence may use the existing `providerMod` + `outputItem` pair, or an ordered `providers` list when several mods can supply the same material.
4. For an ordered list, the first registered output wins. If none are registered, the optional occurrence is omitted.
5. The resolved occurrence then uses the normal GeoStrata candidate, formation-route, deposit-style and grade pipeline. Chunk generation does not scan mods or choose providers.
6. Graded external ore loot is evaluated normally first, preserving Silk Touch, Fortune, grade yield and explosion decay. Non-Silk provider drops are then normalized to the occurrence's resolved `outputItem`, so several installed providers cannot make the same GeoStrata ore drop an arbitrary resource.
7. Provider-native natural generation is suppressed only after that provider's exact 1.20.1 registry/output/worldgen contract has been verified and GeoStrata replacement generation is active.
8. Provider mods retain their items, recipes and processing chains.

## Almost Unified boundary

Almost Unified is intentionally **not a GeoStrata dependency**.

GeoStrata must be correct without it: one canonical geological occurrence, provider-gated activation, deterministic provider output, verified duplicate-worldgen suppression, valid common tags, and usable provider outputs.

A modpack may then use Almost Unified to choose dominant tagged resources and rewrite/merge recipes. That is a downstream item/recipe-normalisation concern; Almost Unified does not replace GeoStrata's geology or duplicate-worldgen ownership.

The pack's Almost Unified priority order should be kept consistent with any ordered GeoStrata provider list for a material. GeoStrata does not read Almost Unified configuration or call its API.

## Multi-provider promotion rule

A multi-provider occurrence may use an aggregate optional provider-output tag for datapack compatibility, but the tag itself does not decide which installed mod owns the mined output. `GradedOreBlock` normalizes non-Silk drops to the provider already selected during geology reload.

The provider order therefore has one meaning across the runtime: the first registered output owns both the geological occurrence and the normal mined resource. Silk Touch continues to return the GeoStrata graded block itself.

This keeps the compatibility system incremental: no new worldgen pass, no provider polling in chunk generation, no dynamic tag mutation, and no Almost Unified coupling.
