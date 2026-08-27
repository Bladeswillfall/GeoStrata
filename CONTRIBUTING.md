# Contributing to GeoStrata

GeoStrata is intentionally compatibility-first. Keep changes small enough to review and preserve the standalone core.

## Before opening a pull request

- Build the mod with `gradle clean build`.
- Run `gradle pmdMain` when changing production Java; `gradle check` and `gradle build` run it automatically.
- Do not add a hard dependency on another content/worldgen mod without an architectural reason.
- Prefer tags/data over Java integration when Minecraft's data system can express the behavior.
- Guard optional Java integrations so the external mod can be absent.
- Do not commit launcher caches, downloaded mods, saves, logs or generated build output.
- Treat registry IDs and public GeoStrata tags as compatibility contracts; rename/remove them only with migration consideration.
- Match vanilla behavior for ordinary block mechanics (loot, recipes, mining tags, slabs/stairs/walls) unless deviation is deliberate and documented.

## Complexity guardrails

Production Java is checked with PMD for both cyclomatic and cognitive complexity. A method may have a score up to 20; a score above 20 fails `pmdMain` and therefore fails `check`/CI. Test sources are intentionally excluded from this guardrail.

Treat the metric as a review signal, not a refactoring objective by itself. Prefer extracting cohesive responsibilities or simplifying control flow. If a method is genuinely clearer above the threshold, use the narrowest rule-specific PMD suppression available and document why the exception preserves locality or correctness. Do not add blanket PMD suppressions or split a method solely to make the number smaller.

Existing suppressions are technical debt, not precedent. New code should normally satisfy the threshold, and a suppression should be reconsidered when the surrounding code is substantially changed.

## Pull request scope

One PR should normally do one thing: build/tooling recovery, worldgen tuning, a compatibility adapter, a block family, repository cleanup, and so on. Large mixed PRs are difficult to validate and are more likely to create worldgen regressions.

## Compatibility review

For any feature involving another mod, state:

1. what happens when the other mod is absent;
2. what activates when it is present;
3. whether the integration can be implemented as data instead of code;
4. whether it belongs in core or a separate compatibility artifact.
