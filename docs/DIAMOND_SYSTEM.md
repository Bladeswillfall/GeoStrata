# Diamond geology

GeoStrata keeps vanilla diamond generation intact by default. The geology-driven diamond system is an **experimental opt-in** while abundance, readability and progression are tested in fresh worlds. Installing the experiment companion promotes that prototype to runtime, but vanilla Overworld diamond generation remains enabled as a fallback until the replacement system has demonstrated adequate coverage.

The activation contract is `data/geostrata/geology/diamond_geology_experiment.json`. The bundled file ships with `enabled: false` and `nativeGenerationSuppression: not_implemented`, so installing GeoStrata alone does not remove or rebalance vanilla diamonds. The companion activates the experimental diamond routes without changing that suppression status; runtime diagnostics continue to report `nativeGenerationSuppression: not_implemented` while vanilla and experimental diamond generation coexist.

## Design goals

Diamond gameplay has two geological routes without turning every fault or every unusual rock into a treasure marker:

1. **Deep structural occurrences** are the commoner GeoStrata route. Small vanilla diamond-ore clusters follow selected ancient fault traces in old cratonic interiors.
2. **Kimberlite/lamproite intrusives** are very rare exploration events. A restrained tuff ring at the surface can indicate a narrow intrusive feeder extending downward into a potentially richer deep diamond halo.

The two new rocks are first-class blocks and lithologies:

- `geostrata:kimberlite`
- `geostrata:lamproite`

They are **event-only**. They do not receive ordinary `strata_lens` background generation and have no decorative block families or separate deep variants.

## Structural route

The structural route now reuses GeoStrata's authoritative tectonic fault field rather than maintaining a second diamond-only corridor model.

X/Z is still divided into deterministic 256-block candidate cells to control abundance. A candidate is accepted only when:

- the diamond experiment is enabled;
- its deterministic activation roll passes the configured chance;
- the candidate lies in a `cratonic_shield` province;
- it is at least 64 blocks inside the province boundary; and
- its sparse candidate anchor is close enough to one of the craton's actual `TectonicStructuralField` fault traces.

Accepted anchors are projected onto the nearest qualifying fault. Two or three compact diamond clusters are then distributed through the deepest part of the active dimension with small along-fault and across-fault jitter. Because the current first-pass faults are vertical planes, the resulting diamond occurrence is strongly vertical as well. The depth window remains relative to the active dimension bottom rather than hard-coded to vanilla Y values.

The bundled `structuralActivationChancePerCell` is currently 45%. This is intentionally higher than the old proxy-corridor value because the activation roll now occurs in addition to the real-fault proximity gate; most candidate cells are not close enough to a fault to qualify. The resulting world abundance therefore remains much lower than the raw 45% suggests.

This removes the old duplicated fault direction/tilt salts from `DiamondGeologyPlanner`: candidate cells own rarity, while `TectonicStructuralField` owns structural geometry. A visible faulted contact and a structural diamond occurrence can therefore refer to the same deterministic geological structure.

Most clusters are compact. A small minority use the larger prototype radius. The intent is vertical follow-up mining along geological structure rather than broad horizontal diamond blankets.

## Rare intrusive route

Pipe candidates use the same 768-block scale as geological province sites and are restricted to cratonic interiors at least 96 blocks from a province boundary.

Current experimental activation values per pipe cell are:

| Intrusive | Candidate chance |
| --- | ---: |
| Kimberlite | 6% |
| Lamproite | 2% |

Those probabilities apply **before** the cratonic/interior gates, so actual surface indicators are much rarer than the raw percentages imply.

An accepted body:

- starts near the active dimension bottom using a dimension-relative depth;
- rises as a narrow, slightly tilted feeder;
- widens modestly toward the generated `OCEAN_FLOOR_WG` surface;
- cross-cuts eligible vanilla stone/deepslate and GeoStrata natural rock;
- leaves caves, fluids, bedrock, structures and unrelated blocks alone; and
- may create a small tuff ring at the generated surface as a prospecting clue.

The surface ring is intentionally modest. It should read as unusual geology to a player who knows what to look for, not as a giant glowing "diamonds here" landmark.

## Barren pipes

Finding kimberlite or lamproite is evidence, not a guarantee.

The prototype currently makes approximately:

- 75% of accepted kimberlite bodies diamond-bearing;
- 55% of accepted lamproite bodies diamond-bearing.

A barren intrusive is therefore a valid result. Diamond-bearing bodies place several deterministic compact clusters in a deep halo around the feeder rather than filling the pipe itself with diamond ore.

These numbers are tuning values, not a promised final economy.

## Diamond blocks and deepslate

The experiment deliberately reuses vanilla diamond blocks rather than adding Poor/Medium/Rich/Massive diamond grades.

When an experimental cluster replaces vanilla stone substrate it places `minecraft:diamond_ore`; when it replaces deepslate substrate it places `minecraft:deepslate_diamond_ore`. This preserves Minecraft's familiar visual language and mining economy.

GeoStrata lithologies are not currently converted into a new host-aware diamond texture matrix. The diamond feature only writes vanilla diamond ore into vanilla stone/deepslate ore-replaceable substrate. Intrusive rock and existing GeoStrata strata therefore remain visually intact rather than receiving an incorrect vanilla-stone diamond texture.

## World-height compatibility

Neither diamond route assumes Y=-58 or another vanilla absolute depth.

Structural clusters and the rich intrusive halo are expressed as fractions/margins from the active dimension bottom. The intrusive feeder terminates against the actual generated terrain surface. A terrain mod can therefore provide deeper worlds or very tall mountains without multiplying bed thickness or requiring a terrain-mod-specific diamond implementation.

## Ownership boundary

Standalone GeoStrata remains conservative:

- vanilla diamond generation remains enabled;
- GeoStrata's diamond experiment is disabled by default;
- no vanilla/provider diamond feature is suppressed; and
- the existing bulk ore grade economy remains separate from diamond geology.

With the optional experiment companion installed:

- the diamond experiment is activated;
- vanilla Overworld diamond generation remains enabled as a fallback;
- structural and intrusive GeoStrata diamond geology run alongside vanilla diamonds for validation; and
- unrelated vanilla resources such as redstone and lapis remain under Minecraft generation ownership.

The temporary overlap is deliberate. Current paired-world evidence shows that the experimental routes are far too sparse to replace ordinary vanilla diamond availability safely. Native diamond suppression should only return once fresh-world tests demonstrate acceptable replacement coverage, progression and compatibility. This remains an experimental validation boundary, not a general provider-suppression framework or a change to normal GeoStrata installs.

## What this intentionally does not add

- diamonds on every fault;
- giant common kimberlite/lamproite columns;
- guaranteed diamonds under every surface indicator;
- Poor/Medium/Rich/Massive diamond ore;
- kimberlite bricks/slabs/stairs or deep variants;
- bespoke terrain-mod hooks.
