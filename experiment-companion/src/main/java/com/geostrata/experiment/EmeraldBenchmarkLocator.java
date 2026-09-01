package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.TerrainMorphologySample;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;

/** Temporary calibration helper for locating high-relief orogenic terrain on the benchmark seed. */
final class EmeraldBenchmarkLocator {
    private static final int SEARCH_RADIUS_CHUNKS = 2048;
    private static final int STEP_CHUNKS = 4;

    private EmeraldBenchmarkLocator() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> logBestSite(server.getOverworld()));
    }

    private static void logBestSite(ServerWorld world) {
        Site best = null;
        for (int chunk = -SEARCH_RADIUS_CHUNKS; chunk <= SEARCH_RADIUS_CHUNKS; chunk += STEP_CHUNKS) {
            int block = (chunk << 4) + 8;
            if (GeologyProvinceSampler.sample(world.getSeed(), block, block).province() != GeologyProvince.OROGENIC_BELT) {
                continue;
            }
            TerrainMorphologySample terrain = ChunkGeneratorTerrainMorphologySampler.sample(world, block, block);
            if (terrain.relief() < 24.0 || terrain.prominence() <= 0.0) {
                continue;
            }
            Site site = new Site(chunk, terrain.centerHeight(), terrain.relief(), terrain.prominence());
            if (best == null
                    || site.height() > best.height()
                    || (site.height() == best.height() && site.relief() > best.relief())) {
                best = site;
            }
        }
        if (best != null) {
            GeoStrata.LOGGER.info(
                    "GEOSTRATA_EMERALD_BENCHMARK_SITE minChunk={} centerChunk={} height={} relief={} prominence={}",
                    best.chunk() - 5,
                    best.chunk(),
                    best.height(),
                    best.relief(),
                    best.prominence()
            );
        }
    }

    private record Site(int chunk, double height, double relief, double prominence) {
    }
}
