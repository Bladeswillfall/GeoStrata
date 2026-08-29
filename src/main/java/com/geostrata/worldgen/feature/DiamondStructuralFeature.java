package com.geostrata.worldgen.feature;

import com.geostrata.geology.DiamondGeologyExperiment;
import com.geostrata.geology.DiamondGeologyPlanner;
import com.geostrata.geology.GeologyDeterminism;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Experimental common diamond route: small deep clusters aligned to rare,
 * steep structural corridors in stable cratonic interiors.
 *
 * <p>The corridor is intentionally not rendered as a new fault block. It is a
 * deterministic placement field that can later be replaced by an explicit
 * fault-plane model without changing the diamond gameplay contract.</p>
 */
public final class DiamondStructuralFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SEARCH_PADDING = 20;
    private static final long CLUSTER_Y_SALT = 0x8CB92BA72F3D8DD7L;
    private static final long CLUSTER_X_SALT = 0x58F38DED09D2C7A9L;
    private static final long CLUSTER_Z_SALT = 0xA24BAED4963EE407L;
    private static final long CLUSTER_SIZE_SALT = 0x9FB21C651E98DF25L;

    public DiamondStructuralFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        DiamondGeologyExperiment.Snapshot experiment = DiamondGeologyExperiment.current();
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;
        long seed = world.getSeed();

        int minCellX = Math.floorDiv(startX - SEARCH_PADDING, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE);
        int maxCellX = Math.floorDiv(endX + SEARCH_PADDING, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE);
        int minCellZ = Math.floorDiv(startZ - SEARCH_PADDING, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE);
        int maxCellZ = Math.floorDiv(endZ + SEARCH_PADDING, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE);

        int placed = 0;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                DiamondGeologyPlanner.StructuralCandidate candidate = DiamondGeologyPlanner.structural(seed, cellX, cellZ);
                if (!GeologyDeterminism.passesChance(
                        experiment.structuralActivationChancePerCell(),
                        DiamondGeologyPlanner.structuralActivationRoll(seed, candidate)
                )) {
                    continue;
                }
                GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(seed, candidate.anchorX(), candidate.anchorZ());
                if (province.province() != GeologyProvince.CRATONIC_SHIELD || province.distanceToBoundary() < 64.0) {
                    continue;
                }
                placed += placeCandidate(world, candidate, startX, endX, startZ, endZ);
            }
        }
        return placed > 0;
    }

    private static int placeCandidate(
            StructureWorldAccess world,
            DiamondGeologyPlanner.StructuralCandidate candidate,
            int startX,
            int endX,
            int startZ,
            int endZ
    ) {
        int worldHeight = world.getTopY() - world.getBottomY();
        int minY = world.getBottomY() + 5;
        int maxY = Math.min(
                world.getTopY() - 1,
                world.getBottomY() + Math.max(28, (int) Math.round(worldHeight * 0.22))
        );
        if (maxY <= minY) {
            return 0;
        }

        long seed = world.getSeed();
        int placed = 0;
        for (int cluster = 0; cluster < candidate.clusterCount(); cluster++) {
            double yRoll = DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_Y_SALT);
            int y = minY + (int) Math.floor(yRoll * (maxY - minY + 1));
            double along = y - minY;
            double xJitter = signed(DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_X_SALT)) * 2.5;
            double zJitter = signed(DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_Z_SALT)) * 2.5;
            double centerX = candidate.anchorX() + candidate.tiltX() * along + xJitter;
            double centerZ = candidate.anchorZ() + candidate.tiltZ() * along + zJitter;
            int radius = DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_SIZE_SALT) < 0.12 ? 2 : 1;
            placed += DiamondPipeFeature.placeDiamondCluster(
                    world,
                    centerX,
                    y,
                    centerZ,
                    radius,
                    startX,
                    endX,
                    startZ,
                    endZ
            );
        }
        return placed;
    }

    private static double signed(double roll) {
        return roll * 2.0 - 1.0;
    }
}
