package com.geostrata.worldgen.feature;

import com.geostrata.geology.DiamondGeologyExperiment;
import com.geostrata.geology.DiamondGeologyPlanner;
import com.geostrata.geology.FaultControlledOrePlanner;
import com.geostrata.geology.GeologyDeterminism;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.geostrata.geology.TectonicStructuralField;
import com.geostrata.geology.TerraneSuture;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Experimental common diamond route: small deep clusters following the same
 * ancient fault zones that displace GeoStrata geology in stable cratonic interiors.
 */
public final class DiamondStructuralFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final String STRUCTURAL_CONTINUITY = "regional";
    private static final double ALONG_FAULT_SPREAD = 12.0;
    private static final double ACROSS_FAULT_JITTER = 6.0;
    static final double STRUCTURAL_DEPTH_FRACTION = 0.08;
    static final double LARGE_CLUSTER_RADIUS_CHANCE = 0.65;
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
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!experiment.loaded() || !experiment.enabled() || !fieldProfiles.loaded()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;
        long seed = world.getSeed();
        double cycleThickness = fieldProfiles.parametersFor(STRUCTURAL_CONTINUITY).cycleThicknessBlocks();

        DiamondGeologyPlanner.StructuralCandidate candidate = DiamondGeologyPlanner.structural(
                seed,
                Math.floorDiv(startX, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE),
                Math.floorDiv(startZ, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE)
        );
        if (!GeologyDeterminism.passesChance(
                experiment.structuralActivationChancePerCell(),
                DiamondGeologyPlanner.structuralActivationRoll(seed, candidate)
        )) {
            return false;
        }

        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(
                seed,
                candidate.anchorX(),
                candidate.anchorZ()
        );
        if (province.province() != GeologyProvince.CRATONIC_SHIELD || TerraneSuture.canCross(province)) {
            return false;
        }

        TectonicStructuralField.Context tectonics = TectonicStructuralField.forSite(
                seed,
                province.province(),
                province.siteX(),
                province.siteZ(),
                cycleThickness
        );
        TectonicStructuralField.FaultTrace trace = tectonics.nearestFault(
                candidate.anchorX(),
                candidate.anchorZ()
        );
        if (trace.distanceToFault() > FaultControlledOrePlanner.CAPTURE_DISTANCE_BLOCKS) {
            return false;
        }

        Chunk chunk = world.getChunk(Math.floorDiv(startX, CHUNK_SIZE), Math.floorDiv(startZ, CHUNK_SIZE));
        List<BlockBox> protectedStructurePieces = StructurePieceProtection.forChunk(world, chunk);
        return placeCandidate(
                world,
                candidate,
                tectonics,
                trace,
                startX,
                endX,
                startZ,
                endZ,
                protectedStructurePieces
        ) > 0;
    }

    private static int placeCandidate(
            StructureWorldAccess world,
            DiamondGeologyPlanner.StructuralCandidate candidate,
            TectonicStructuralField.Context tectonics,
            TectonicStructuralField.FaultTrace trace,
            int startX,
            int endX,
            int startZ,
            int endZ,
            List<BlockBox> protectedStructurePieces
    ) {
        int worldHeight = world.getTopY() - world.getBottomY();
        int minY = world.getBottomY() + 5;
        int maxY = Math.min(
                world.getTopY() - 1,
                world.getBottomY() + Math.max(28, (int) Math.round(worldHeight * STRUCTURAL_DEPTH_FRACTION))
        );
        if (maxY <= minY) {
            return 0;
        }

        long seed = world.getSeed();
        double normalX = -tectonics.faultSin();
        double normalZ = tectonics.faultCos();
        int placed = 0;
        for (int cluster = 0; cluster < candidate.clusterCount(); cluster++) {
            double yRoll = DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_Y_SALT);
            int y = minY + (int) Math.floor(yRoll * yRoll * (maxY - minY + 1));
            double alongJitter = signed(
                    DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_X_SALT)
            ) * ALONG_FAULT_SPREAD;
            double acrossJitter = signed(
                    DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_Z_SALT)
            ) * ACROSS_FAULT_JITTER;
            double centerX = trace.x()
                    + tectonics.faultCos() * alongJitter
                    + normalX * acrossJitter;
            double centerZ = trace.z()
                    + tectonics.faultSin() * alongJitter
                    + normalZ * acrossJitter;
            int radius = DiamondGeologyPlanner.structuralClusterRoll(seed, candidate, cluster, CLUSTER_SIZE_SALT)
                    < LARGE_CLUSTER_RADIUS_CHANCE
                    ? 3
                    : 2;
            placed += DiamondPipeFeature.placeDiamondCluster(
                    world,
                    centerX,
                    y,
                    centerZ,
                    radius,
                    startX,
                    endX,
                    startZ,
                    endZ,
                    protectedStructurePieces
            );
        }
        return placed;
    }

    private static double signed(double roll) {
        return roll * 2.0 - 1.0;
    }
}
