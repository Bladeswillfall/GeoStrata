package com.geostrata.geology;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Samples the active terrain generator without loading neighboring chunks. */
public final class ChunkGeneratorTerrainMorphologySampler {
    public static final int DEFAULT_SAMPLE_SPACING_BLOCKS =
            TerrainAwareStructuralField.DEFAULT_GRID_SPACING_BLOCKS;
    private static final int MAX_CACHED_HEIGHT_SAMPLES_PER_WORLD = 4096;
    private static final Map<ServerWorld, HeightCache> WORLD_HEIGHT_CACHES = new WeakHashMap<>();

    private ChunkGeneratorTerrainMorphologySampler() {
    }

    public static TerrainMorphologySample sample(ServerWorld world, int x, int z) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }

        return sample(cachedHeightSource(world), x, z, DEFAULT_SAMPLE_SPACING_BLOCKS);
    }

    /** Returns the cached active-generator terrain height without loading a neighboring chunk. */
    public static double terrainHeight(ServerWorld world, int x, int z) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }
        return cachedHeightSource(world).heightAt(x, z);
    }

    public static TerrainAwareStructuralField.Field structuralField(
            ServerWorld world,
            int x,
            int z,
            GeologyProvince province,
            SedimentaryStratigraphicField.Field baseField
    ) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }
        if (baseField == null) {
            throw new IllegalArgumentException("base stratigraphic field must not be null");
        }

        HeightSource heights = cachedHeightSource(world);
        TerrainAwareStructuralField.TerrainPatch localPatch = TerrainAwareStructuralField.TerrainPatch.sample(
                heights::heightAt,
                x,
                z,
                DEFAULT_SAMPLE_SPACING_BLOCKS
        );
        TerrainAwareStructuralField.TerrainPatch anchorPatch = TerrainAwareStructuralField.TerrainPatch.sample(
                heights::heightAt,
                baseField.siteX(),
                baseField.siteZ(),
                DEFAULT_SAMPLE_SPACING_BLOCKS
        );
        double anchorHeight = anchorPatch.heightAt(baseField.siteX(), baseField.siteZ());
        TectonicStructuralField.Context tectonicField = TectonicStructuralField.forSite(
                world.getSeed(),
                province,
                baseField.siteX(),
                baseField.siteZ(),
                baseField.cycleThicknessBlocks()
        );
        return TerrainAwareStructuralField.apply(
                baseField,
                province,
                localPatch,
                anchorHeight,
                tectonicField
        );
    }

    private static HeightSource cachedHeightSource(ServerWorld world) {
        HeightCache cache;
        synchronized (WORLD_HEIGHT_CACHES) {
            cache = WORLD_HEIGHT_CACHES.computeIfAbsent(world, ignored -> new HeightCache());
        }
        HeightSource source = heightSource(world);
        return (x, z) -> cache.heightAt(source, x, z);
    }

    private static HeightSource heightSource(ServerWorld world) {
        ServerChunkManager chunkManager = world.getChunkManager();
        ChunkGenerator generator = chunkManager.getChunkGenerator();
        NoiseConfig noiseConfig = chunkManager.getNoiseConfig();
        return (sampleX, sampleZ) -> generator.getHeight(
                sampleX,
                sampleZ,
                Heightmap.Type.OCEAN_FLOOR_WG,
                world,
                noiseConfig
        );
    }

    private static long coordinateKey(int x, int z) {
        return ((long) x << Integer.SIZE) ^ (z & 0xFFFFFFFFL);
    }

    static TerrainMorphologySample sample(HeightSource heights, int x, int z, int spacing) {
        if (heights == null) {
            throw new IllegalArgumentException("terrain height source must not be null");
        }
        if (spacing <= 0) {
            throw new IllegalArgumentException("terrain sample spacing must be positive");
        }

        return TerrainMorphologySample.fromCardinalHeights(
                heights.heightAt(x, z),
                heights.heightAt(x - spacing, z),
                heights.heightAt(x + spacing, z),
                heights.heightAt(x, z - spacing),
                heights.heightAt(x, z + spacing),
                spacing
        );
    }

    private static final class HeightCache {
        private final ConcurrentHashMap<Long, Double> heights = new ConcurrentHashMap<>();

        private double heightAt(HeightSource source, int x, int z) {
            long key = coordinateKey(x, z);
            double height = heights.computeIfAbsent(key, ignored -> source.heightAt(x, z));
            if (heights.size() > MAX_CACHED_HEIGHT_SAMPLES_PER_WORLD) {
                evictOne(key);
            }
            return height;
        }

        private void evictOne(long keepKey) {
            for (Long candidate : heights.keySet()) {
                if (candidate.longValue() != keepKey) {
                    heights.remove(candidate);
                    return;
                }
            }
        }
    }

    @FunctionalInterface
    interface HeightSource {
        double heightAt(int x, int z);
    }
}
