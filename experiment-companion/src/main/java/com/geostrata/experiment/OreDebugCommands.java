package com.geostrata.experiment;

import com.geostrata.block.GradedOreBlock;
import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.FaultControlledOrePlanner;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.OreDepositCandidatePlanner;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreDepositGeometry;
import com.geostrata.geology.OreExposurePlacement;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Companion-only command for locating ore voxels that worldgen actually placed. */
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
                                                        StringArgumentType.getString(context, "material"),
                                                        null
                                                ))
                                                .then(CommandManager.argument("style", StringArgumentType.word())
                                                        .executes(context -> locate(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "material"),
                                                                StringArgumentType.getString(context, "style")
                                                        )))))))
        );
    }

    private static int locate(ServerCommandSource source, String material, String requestedStyle) {
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
        if (requestedStyle != null && !occurrence.depositStyles().contains(requestedStyle)) {
            source.sendError(Text.literal(
                    "GeoStrata " + material + " does not use deposit style " + requestedStyle
                            + "; allowed: " + String.join(",", occurrence.depositStyles())
            ));
            return 0;
        }

        Optional<LocatedOre> found = findNearby(source, occurrence, fieldProfiles, requestedStyle);
        if (found.isEmpty()) {
            int cellSize = OreDepositCandidatePlanner.frequency(occurrence).horizontalCellSize();
            source.sendError(Text.literal(
                    "No placed GeoStrata " + material
                            + (requestedStyle == null ? "" : " " + requestedStyle)
                            + " ore found within ~" + (SEARCH_RADIUS_CELLS * cellSize)
                            + " blocks. Move several candidate cells and run the locate command again."
            ));
            return 0;
        }

        LocatedOre ore = found.get();
        BlockPos pos = ore.pos();
        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata ore locate " + material
                                + ": FOUND " + ore.style()
                                + " | richest " + ore.grade()
                                + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                + " | grades " + gradeSummary(ore.counts())
                                + " | total " + ore.counts().values().stream().mapToInt(Integer::intValue).sum()
                                + " | teleport: /tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                ),
                false
        );
        return 1;
    }

    private static Optional<LocatedOre> findNearby(
            ServerCommandSource source,
            OreOccurrenceCatalog.Occurrence occurrence,
            SedimentaryFieldProfiles.Snapshot fieldProfiles,
            String requestedStyle
    ) {
        ServerWorld world = source.getWorld();
        long seed = world.getSeed();
        OreDepositCandidatePlanner.Frequency frequency = OreDepositCandidatePlanner.frequency(occurrence);
        int originX = MathHelper.floor(source.getPosition().x);
        int originZ = MathHelper.floor(source.getPosition().z);
        int originCellX = Math.floorDiv(originX, frequency.horizontalCellSize());
        int originCellZ = Math.floorDiv(originZ, frequency.horizontalCellSize());
        int minCellY = Math.floorDiv(world.getBottomY(), frequency.verticalCellSize());
        int maxCellY = Math.floorDiv(world.getTopY() - 1, frequency.verticalCellSize());
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
                                structuralCycle,
                                requestedStyle
                        );
                        if (found.isPresent()) {
                            return found;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<LocatedOre> inspectCandidate(
            ServerWorld world,
            long seed,
            int cellX,
            int cellY,
            int cellZ,
            OreOccurrenceCatalog.Occurrence occurrence,
            double structuralCycle,
            String requestedStyle
    ) {
        OreDepositCandidatePlanner.Proposal proposal = OreDepositCandidatePlanner.proposeCell(
                seed,
                cellX,
                cellY,
                cellZ,
                occurrence
        );
        if (requestedStyle != null && !requestedStyle.equals(proposal.depositStyle())) {
            return Optional.empty();
        }

        FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(seed, proposal, structuralCycle);
        proposal = binding.proposal();
        GeologyProvince province = GeologyProvinceSampler.sample(seed, proposal.anchorX(), proposal.anchorZ()).province();
        if (!occurrence.provinceContexts().contains(province)) {
            return Optional.empty();
        }
        double affinityMultiplier = occurrence.generation().depthMultiplier(proposal.anchorY())
                * occurrence.generation().provinceMultiplier(province)
                * surfaceBiomeMultiplier(world, occurrence, proposal);
        if (!OreDepositExperiment.active(seed, proposal, affinityMultiplier)
                || !occurrence.terrainFilter().matches(ChunkGeneratorTerrainMorphologySampler.sample(
                        world,
                        proposal.anchorX(),
                        proposal.anchorZ()
                ))) {
            return Optional.empty();
        }

        OreDepositGeometry.Body body = binding.body(seed);
        if (scanPlacedOre(world, body, occurrence.id(), STRUCTURAL_SCAN_STRIDE).isEmpty()) {
            return Optional.empty();
        }
        return scanPlacedOre(world, body, occurrence.id(), 1);
    }

    private static double surfaceBiomeMultiplier(
            ServerWorld world,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal
    ) {
        if (occurrence.generation().biomeMultipliers().isEmpty()) {
            return 1.0;
        }
        var chunkManager = world.getChunkManager();
        int surfaceY = Math.max(
                world.getSeaLevel(),
                (int) Math.floor(ChunkGeneratorTerrainMorphologySampler.terrainHeight(
                        world,
                        proposal.anchorX(),
                        proposal.anchorZ()
                ))
        );
        RegistryEntry<Biome> biome = chunkManager.getChunkGenerator().getBiomeSource().getBiome(
                BiomeCoords.fromBlock(proposal.anchorX()),
                BiomeCoords.fromBlock(surfaceY),
                BiomeCoords.fromBlock(proposal.anchorZ()),
                chunkManager.getNoiseConfig().getMultiNoiseSampler()
        );
        return occurrence.generation().biomeMultiplier(tag -> biome.isIn(biomeTag(tag)));
    }

    private static Optional<LocatedOre> scanPlacedOre(
            ServerWorld world,
            OreDepositGeometry.Body body,
            String material,
            int stride
    ) {
        OreDepositGeometry.Bounds bounds = OreExposurePlacement.placementBounds(body);
        int minY = Math.max(world.getBottomY(), bounds.minY());
        int maxY = Math.min(world.getTopY() - 1, bounds.maxY());
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        EnumMap<OreGrade, Integer> counts = new EnumMap<>(OreGrade.class);
        BlockPos richestPos = null;
        OreGrade richestGrade = null;

        for (int x = bounds.minX(); x <= bounds.maxX(); x += stride) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += stride) {
                for (int y = minY; y <= maxY; y += stride) {
                    OreGrade grade = placedGrade(world, body, material, mutable, x, y, z);
                    if (grade == null) {
                        continue;
                    }
                    counts.merge(grade, 1, Integer::sum);
                    if (richestGrade == null || grade.ordinal() > richestGrade.ordinal()) {
                        richestGrade = grade;
                        richestPos = mutable.toImmutable();
                    }
                }
            }
        }
        if (richestPos == null) {
            return Optional.empty();
        }
        return Optional.of(new LocatedOre(
                richestPos,
                richestGrade.id(),
                body.style(),
                Map.copyOf(counts)
        ));
    }

    private static OreGrade placedGrade(
            ServerWorld world,
            OreDepositGeometry.Body body,
            String material,
            BlockPos.Mutable mutable,
            int x,
            int y,
            int z
    ) {
        OreDepositGeometry.Sample sample = body.sample(x, y, z);
        if (!sample.economic() && !sample.trace()) {
            return null;
        }
        mutable.set(x, y, z);
        BlockState state = world.getBlockState(mutable);
        if (state.getBlock() instanceof GradedOreBlock ore && ore.material().equals(material)) {
            return ore.grade();
        }
        return null;
    }

    private static TagKey<Biome> biomeTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid biome tag id: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BIOME, id);
    }

    private static String gradeSummary(Map<OreGrade, Integer> counts) {
        return "poor " + counts.getOrDefault(OreGrade.POOR, 0)
                + ", medium " + counts.getOrDefault(OreGrade.MEDIUM, 0)
                + ", rich " + counts.getOrDefault(OreGrade.RICH, 0)
                + ", massive " + counts.getOrDefault(OreGrade.MASSIVE, 0);
    }

    private record LocatedOre(BlockPos pos, String grade, String style, Map<OreGrade, Integer> counts) {
    }
}
