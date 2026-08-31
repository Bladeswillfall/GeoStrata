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
    private static final int CHUNK_SIZE = 16;

    private GeologyResolver() {
    }

    /** Resolves the authoritative queryable geology at one coordinate. */
    public static Optional<Result> resolve(ServerWorld world, int x, int y, int z) {
        return prepareChunk(world, x, z).flatMap(chunk -> chunk.resolve(x, y, z));
    }

    /**
     * Prepares the semantic geology authority for one chunk.
     *
     * <p>Worldgen consumers that query many blocks should prepare once and
     * reuse the returned context instead of rebuilding terrain/province state
     * for every coordinate.</p>
     */
    public static Optional<PreparedChunk> prepareChunk(ServerWorld world, int blockX, int blockZ) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!catalog.loaded()) {
            return Optional.empty();
        }

        int startX = Math.floorDiv(blockX, CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(blockZ, CHUNK_SIZE) * CHUNK_SIZE;
        Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated = CorrelatedSedimentaryRuntime.resolve(
                world,
                blockX,
                blockZ
        );
        if (correlated.isPresent()) {
            return Optional.of(new PreparedChunk(
                    startX,
                    startZ,
                    world.getSeed(),
                    catalog,
                    correlated,
                    Optional.empty()
            ));
        }

        Optional<ProvinceBackgroundRuntime.Chunk> background = ProvinceBackgroundRuntime.resolve(
                world,
                startX,
                startZ
        );
        return background.map(value -> new PreparedChunk(
                startX,
                startZ,
                world.getSeed(),
                catalog,
                Optional.empty(),
                Optional.of(value)
        ));
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
                Optional.of(parentLithology),
                site.ownership().province(),
                Source.CORRELATED_STRATIGRAPHY
        );
    }

    static Result resolve(int x, int y, int z, ProvinceBackgroundRuntime.Chunk background) {
        if (background == null) {
            throw new IllegalArgumentException("province background must not be null");
        }
        return new Result(
                background.lithologyAt(x, y, z),
                Optional.empty(),
                background.provinceAt(x, y, z),
                Source.PROVINCE_BACKGROUND
        );
    }

    static Optional<Result> resolve(
            long worldSeed,
            int x,
            int y,
            int z,
            Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated,
            Optional<ProvinceBackgroundRuntime.Chunk> background,
            LithologyCatalog.Snapshot catalog
    ) {
        if (correlated == null || background == null || catalog == null || !catalog.loaded()) {
            throw new IllegalArgumentException("prepared geology sources and loaded catalog are required");
        }
        if (correlated.isPresent()) {
            return Optional.of(resolve(worldSeed, x, y, z, correlated.get(), catalog));
        }
        return background.map(value -> resolve(x, y, z, value));
    }

    public enum Source {
        CORRELATED_STRATIGRAPHY,
        PROVINCE_BACKGROUND
    }

    public record Result(
            String lithology,
            Optional<String> parentLithology,
            GeologyProvince province,
            Source source
    ) {
        public Result {
            if (lithology == null || lithology.isBlank()
                    || parentLithology == null
                    || parentLithology.map(String::isBlank).orElse(false)
                    || province == null || source == null) {
                throw new IllegalArgumentException("resolved geology must be complete");
            }
        }
    }

    /** Cached semantic authority for repeated queries inside one chunk. */
    public static final class PreparedChunk {
        private final int startX;
        private final int startZ;
        private final long worldSeed;
        private final LithologyCatalog.Snapshot catalog;
        private final Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated;
        private final Optional<ProvinceBackgroundRuntime.Chunk> background;

        private PreparedChunk(
                int startX,
                int startZ,
                long worldSeed,
                LithologyCatalog.Snapshot catalog,
                Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated,
                Optional<ProvinceBackgroundRuntime.Chunk> background
        ) {
            this.startX = startX;
            this.startZ = startZ;
            this.worldSeed = worldSeed;
            this.catalog = catalog;
            this.correlated = correlated;
            this.background = background;
        }

        public Optional<Result> resolve(int x, int y, int z) {
            if (x < startX || x >= startX + CHUNK_SIZE || z < startZ || z >= startZ + CHUNK_SIZE) {
                throw new IllegalArgumentException("coordinate is outside prepared geology chunk");
            }
            return GeologyResolver.resolve(worldSeed, x, y, z, correlated, background, catalog);
        }
    }
}
