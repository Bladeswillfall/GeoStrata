package com.geostrata.experiment;

import com.geostrata.block.GradedOreBlock;
import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.FaultControlledOrePlanner;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.OreDepositCandidatePlanner;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreDepositGeometry;
import com.geostrata.geology.OreOccurrenceCatalog;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Optional;

/** Companion-only command for locating an ore voxel that worldgen actually placed. */
final class OreDebugCommands {
    private static final int SEARCH_RADIUS_CELLS = 4;
    private static final int STRUCTURAL_SCAN_STRIDE = 2;

    private OreDebugCommands() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("ore")
                                .then(CommandManager.argument("material", StringArgumentType.word())
                                        .then(CommandManager.literal("locate")
                                                .executes(context -> locate(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "material")
                                                ))))))
        );
    }

    private static int locate(ServerCommandSource source, String material) {
        OreOccurrenceCatalog.Snapshot occurrences = OreOccurrenceCatalog.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        OreDepositExperiment.Snapshot experiment = OreDepositExperiment.current();
        if (!occurrences.loaded() || !fieldProfiles.loaded() || !experiment.loaded() || !experiment.enabled()) {
            source.sendError(Text.literal("GeoStrata ore experiment is not ready/enabled."));
            return 0;
        }

        OreOccurrenceCatalog.Occurrence occurrence;
        try {
            occurrence = occurrences.require(material);
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }

        ServerWorld world = source.getWorld();
        long seed = world.getSeed();
        int originX = MathHelper.floor(source.getPosition().x);
        int originZ = MathHelper.floor(source.getPosition().z);
        int originCellX = Math.floorDiv(originX, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE);
        int originCellZ = Math.floorDiv(originZ, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE);
        int minCellY = Math.floorDiv(world.getBottomY(), OreDepositCandidatePlanner.VERTICAL_CELL_SIZE);
        int maxCellY = Math.floorDiv(world.getTopY() - 1, OreDepositCandidatePlanner.VERTICAL_CELL_SIZE);
        double structuralCycle = fieldProfiles.parametersFor("regional").cycleThicknessBlocks();

        for (int ring = 0; ring <= SEARCH_RADIUS_CELLS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                        Optional<LocatedOre> found = inspectCandidate(
                                world,
                                seed,
                                originCellX + dx,
                                cellY,
                                originCellZ + dz,
                                occurrence,
                                structuralCycle
                        );
                        if (found.isPresent()) {
                            LocatedOre ore = found.get();
                            BlockPos pos = ore.pos();
                            source.sendFeedback(
                                    () -> Text.literal(
                                            "GeoStrata ore locate " + material
                                                    + ": FOUND " + ore.grade()
                                                    + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                                    + " | actual placed block"
                                                    + " | teleport: /tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                    ),
                                    false
                            );
                            return 1;
                        }
                    }
                }
            }
        }

        source.sendError(Text.literal(
                "No placed GeoStrata " + material + " ore found within ~"
                        + (SEARCH_RADIUS_CELLS * OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE)
                        + " blocks. Move ~512 blocks and run the locate command again."
        ));
        return 0;
    }

    private static Optional<LocatedOre> inspectCandidate(
            ServerWorld world,
            long seed,
            int cellX,
            int cellY,
            int cellZ,
            OreOccurrenceCatalog.Occurrence occurrence,
            double structuralCycle
    ) {
        OreDepositCandidatePlanner.Proposal proposal = OreDepositCandidatePlanner.propose(
                seed,
                cellX * OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE,
                cellY * OreDepositCandidatePlanner.VERTICAL_CELL_SIZE,
                cellZ * OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE,
                occurrence
        );
        if (!OreDepositExperiment.active(seed, proposal)) {
            return Optional.empty();
        }

        FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(seed, proposal, structuralCycle);
        proposal = binding.proposal();
        GeologyProvince province = GeologyProvinceSampler.sample(seed, proposal.anchorX(), proposal.anchorZ()).province();
        if (!occurrence.provinceContexts().contains(province)
                || !occurrence.terrainFilter().matches(ChunkGeneratorTerrainMorphologySampler.sample(
                        world,
                        proposal.anchorX(),
                        proposal.anchorZ()
                ))) {
            return Optional.empty();
        }

        OreDepositGeometry.Body body = binding.body(seed);
        Optional<LocatedOre> coarse = findPlacedOre(world, body, occurrence.id(), STRUCTURAL_SCAN_STRIDE);
        return coarse.isPresent() ? coarse : findPlacedOre(world, body, occurrence.id(), 1);
    }

    private static Optional<LocatedOre> findPlacedOre(
            ServerWorld world,
            OreDepositGeometry.Body body,
            String material,
            int stride
    ) {
        OreDepositGeometry.Bounds bounds = body.bounds();
        int minY = Math.max(world.getBottomY(), bounds.minY());
        int maxY = Math.min(world.getTopY() - 1, bounds.maxY());
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = bounds.minX(); x <= bounds.maxX(); x += stride) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += stride) {
                for (int y = minY; y <= maxY; y += stride) {
                    if (!body.sample(x, y, z).economic()) {
                        continue;
                    }
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.getBlock() instanceof GradedOreBlock ore && ore.material().equals(material)) {
                        return Optional.of(new LocatedOre(mutable.toImmutable(), ore.grade().id()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private record LocatedOre(BlockPos pos, String grade) {
    }
}
