# Diamond geology

GeoStrata keeps vanilla diamond generation intact by default. The geology-driven diamond system is an **experimental opt-in** while abundance, readability and progression are tested in fresh worlds.

The activation contract is `data/geostrata/geology/diamond_geology_experiment.json`. The bundled file ships with `enabled: false` and `nativeGenerationSuppression: not_implemented`, so installing GeoStrata alone does not remove or rebalance vanilla diamonds.

## Design goals

Diamond gameplay has two geological routes without turning every fault or every unusual rock into a treasure marker:

1. **Deep structural occurrences** are the commoner GeoStrata route. Small vanilla diamond-ore clusters are aligned vertically along rare steep structural corridors in old cratonic interiors.
2. **Kimberlite/lamproite intrusives** are very rare exploration events. A restrained tuff ring at the surface can indicate a narrow intrusive feeder extending downward into a potentially richer deep diamond halo.

The two new rocks are first-class blocks and lithologies:

- `geostrata:kimberlite`
- `geostrata:lamproite`

They are **event-only**. They do not receive ordinary `strata_lens` background generation and have no decorative block families or separate deep variants.

## Structural route

The prototype divides X/Z into deterministic 256-block cells. A candidate is accepted only when:

- the diamond experiment is enabled;
- its deterministic activation roll passes the configured chance;
- the candidate lies in a `cratonic_shield` province; and
- it is at least 64 blocks inside the province boundary.

Accepted candidates define a steep, slightly tilted structural corridor with two or three small diamond clusters distributed through the deepest part of the active dimension. The depth window is relative to the dimension bottom rather than hard-coded to vanilla Y values.

The corridor itself has no new visible "fault block". It is currently a deterministic placement field. This keeps the feature small and lets a future explicit fault-plane model replace the proxy without changing the player-facing diamond rule.

Most clusters are compact. A small minority use the larger prototype radius. The intent is vertical follow-up mining rather than broad horizontal diamond blankets.

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

For now:

- vanilla diamond generation remains enabled;
- GeoStrata's diamond experiment is disabled by default;
- no vanilla/provider diamond feature is suppressed; and
- the existing bulk ore grade economy remains limited to coal, iron, copper and gold.

Native diamond suppression should only be considered after fresh-world tests demonstrate that the structural and intrusive routes provide acceptable progression, rarity and compatibility.

## What this intentionally does not add

- generic diamonds on every fault;
- giant common kimberlite/lamproite columns;
- guaranteed diamonds under every surface indicator;
- Poor/Medium/Rich/Massive diamond ore;
- kimberlite bricks/slabs/stairs or deep variants;
- bespoke terrain-mod hooks.
