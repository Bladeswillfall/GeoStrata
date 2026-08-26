# Contributing to GeoStrata

GeoStrata is intentionally compatibility-first. Keep changes small enough to review and preserve the standalone core.

## Before opening a pull request

- Build the mod with `gradle clean build`.
- Do not add a hard dependency on another content/worldgen mod without an architectural reason.
- Prefer tags/data over Java integration when Minecraft's data system can express the behavior.
- Guard optional Java integrations so the external mod can be absent.
- Do not commit launcher caches, downloaded mods, saves, logs or generated build output.
- Treat registry IDs and public GeoStrata tags as compatibility contracts; rename/remove them only with migration consideration.
- Match vanilla behavior for ordinary block mechanics (loot, recipes, mining tags, slabs/stairs/walls) unless deviation is deliberate and documented.

## Pull request scope

One PR should normally do one thing: build/tooling recovery, worldgen tuning, a compatibility adapter, a block family, repository cleanup, and so on. Large mixed PRs are difficult to validate and are more likely to create worldgen regressions.

## Compatibility review

For any feature involving another mod, state:

1. what happens when the other mod is absent;
2. what activates when it is present;
3. whether the integration can be implemented as data instead of code;
4. whether it belongs in core or a separate compatibility artifact.
