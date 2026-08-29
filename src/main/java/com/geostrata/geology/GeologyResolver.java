package com.geostrata.geology;

import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

/**
 * Read-only semantic query facade over GeoStrata's existing geological fields.
 *
 * <p>This class must not define geological geometry of its own. Strata, terrain
 * deformation, folding and metamorphism remain authoritative in their existing
 * planners/fields; the resolver only exposes their final semantic answer for a
 * coordinate so worldgen, diagnostics and compatibility adapters can agree on
 * the same geology.</p>
 */
public final class GeologyResolver {
    private GeologyResolver() {
    }

    /**
     * Resolves geology currently owned by a queryable GeoStrata field.
     *
     * <p>Fallback feature bodies are intentionally not inferred from placed
     * blocks here. Until a body has a deterministic semantic field, an
     * unowned coordinate returns empty rather than inventing a second geology
     * model.</p>
     */
    public static Optional<Result> resolve(ServerWorld world, int x, int y, int z) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!catalog.loaded()) {
            return Optional.empty();
        }
        return CorrelatedSedimentaryRuntime.resolve(world, x, z)
                .map(site -> resolve(world.getSeed(), x, y, z, site, catalog));
    }

    static Result resolve(
            long worldSeed,
            int x,
            int y,
            int z,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site,
            LithologyCatalog.Snapshot catalog
    ) {
        if (site == null || catalog == null || !catalog.loaded()) {
            throw new IllegalArgumentException("resolver site and loaded lithology catalog are required");
        }
        String parentLithology = site.sample(x, y, z).bed().lithology();
        String lithology = site.outputLithology(worldSeed, x, y, z, catalog);
        return new Result(
                lithology,
                parentLithology,
                site.ownership().province(),
                Source.CORRELATED_STRATIGRAPHY
        );
    }

    public enum Source {
        CORRELATED_STRATIGRAPHY
    }

    public record Result(
            String lithology,
            String parentLithology,
            GeologyProvince province,
            Source source
    ) {
        public Result {
            if (lithology == null || lithology.isBlank()
                    || parentLithology == null || parentLithology.isBlank()
                    || province == null || source == null) {
                throw new IllegalArgumentException("resolved geology must be complete");
            }
        }
    }
}
