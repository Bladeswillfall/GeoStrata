package com.geostrata.worldgen.feature;

import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.block.OreHost;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.FaultControlledOrePlanner;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.GeologyResolver;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.OreDepositCandidatePlanner;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreDepositGeometry;
import com.geostrata.geology.OreDiscoveryStringers;
import com.geostrata.geology.OreExposurePlacement;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import com.geostrata.geology.SedimentaryFieldProfiles;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Experimental chunk-local consumer of GeoStrata's deterministic ore bodies.
 *
 * <p>The feature is registered in normal worldgen but is a no-op while the
 * server-data experiment remains disabled. Active bodies never write across a
 * chunk boundary from another chunk's feature invocation: each chunk evaluates
 * the same nearby deterministic proposals and mutates only its own columns.</p>
 */
public final class OreDepositFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final String STRUCTURAL_CONTINUITY = "regional";

    public OreDepositFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        OreDepositExperiment.Snapshot experiment = OreDepositExperiment.current();
        OreOccurrenceCatalog.Snapshot occurrences = OreOccurrenceCatalog.current();
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!experiment.loaded()
                || !experiment.enabled()
                || !occurrences.loaded()
                || !lithologies.loaded()
                || !fieldProfiles.loaded()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;
        long worldSeed = world.toServerWorld().getSeed();
        double structuralCycleThickness = fieldProfiles
                .parametersFor(STRUCTURAL_CONTINUITY)
                .cycleThicknessBlocks();
        LazyHostResolver hosts = new LazyHostResolver(world, startX, startZ, lithologies);
        Chunk chunk = world.getChunk(Math.floorDiv(startX, CHUNK_SIZE), Math.floorDiv(startZ, CHUNK_SIZE));
        VerticalEnvelope occupied = occupiedEnvelope(chunk);
        if (occupied == null) {
            return false;
        }
        ProvinceSampleCache provinces = new ProvinceSampleCache(worldSeed);
        FormationContextCache formationContexts = new FormationContextCache(world);
        List<BlockBox> protectedStructurePieces = StructurePieceProtection.forChunk(world, chunk);

        int placed = 0;
        for (OreOccurrenceCatalog.Occurrence occurrence : occurrences.occurrences()) {
            placed += placeMaterial(
                    world,
                    worldSeed,
                    startX,
                    endX,
                    startZ,
                    endZ,
                    occurrence,
                    hosts,
                    structuralCycleThickness,
                    provinces,
                    formationContexts,
                    occupied,
                    protectedStructurePieces
            );
        }
        return placed > 0;
    }

    private static int placeMaterial(
            StructureWorldAccess world,
            long worldSeed,
            int startX,
            int endX,
            int startZ,
            int endZ,
            OreOccurrenceCatalog.Occurrence occurrence,
            LazyHostResolver hosts,
            double structuralCycleThickness,
            ProvinceSampleCache provinces,
            FormationContextCache formationContexts,
            VerticalEnvelope occupied,
            List<BlockBox> protectedStructurePieces
    ) {
        OreDepositCandidatePlanner.Frequency frequency = OreDepositCandidatePlanner.frequency(occurrence);
        boolean usesBodyStyleContext = occurrence.formationRoutes().stream()
                .anyMatch(OreOccurrenceCatalog.FormationRoute::requiresBodyStyle);
        int horizontalPadding = frequency.horizontalSearchPaddingBlocks();
        int verticalPadding = frequency.verticalSearchPaddingBlocks();
        int minCellX = Math.floorDiv(startX - horizontalPadding, frequency.horizontalCellSize());
        int maxCellX = Math.floorDiv(endX + horizontalPadding, frequency.horizontalCellSize());
        int minCellY = Math.floorDiv(occupied.minY() - verticalPadding, frequency.verticalCellSize());
        int maxCellY = Math.floorDiv(occupied.maxY() + verticalPadding, frequency.verticalCellSize());
        int minCellZ = Math.floorDiv(startZ - horizontalPadding, frequency.horizontalCellSize());
        int maxCellZ = Math.floorDiv(endZ + horizontalPadding, frequency.horizontalCellSize());

        int placed = 0;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                    OreDepositCandidatePlanner.Proposal proposal = OreDepositCandidatePlanner.proposeCell(
                            worldSeed,
                            cellX,
                            cellY,
                            cellZ,
                            occurrence
                    );
                    FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(
                            worldSeed,
                            proposal,
                            structuralCycleThickness,
                            provinces.contextFor(proposal.anchorX(), proposal.anchorZ())
                    );
                    proposal = binding.proposal();

                    GeologyProvince province = provinces.sample(proposal.anchorX(), proposal.anchorZ()).province();
                    List<String> routeHosts = routeHostsForCandidate(
                            occurrence,
                            proposal,
                            province,
                            formationContexts,
                            usesBodyStyleContext
                    );
                    if (routeHosts.isEmpty() || !activeCandidate(world, worldSeed, occurrence, proposal, province)) {
                        continue;
                    }
                    if (!qualifiesTerrain(world, occurrence, proposal)) {
                        continue;
                    }

                    OreDepositGeometry.Body body = binding.body(worldSeed);
                    OreDiscoveryStringers.Field discovery = OreDiscoveryStringers.forBody(body);
                    OreDepositGeometry.Bounds bounds = OreExposurePlacement.placementBounds(body, discovery);
                    if (!intersectsChunk(bounds, startX, endX, startZ, endZ, occupied)) {
                        continue;
                    }
                    placed += placeBody(
                            world,
                            startX,
                            endX,
                            startZ,
                            endZ,
                            occurrence,
                            body,
                            discovery,
                            bounds,
                            hosts,
                            Set.copyOf(routeHosts),
                            occupied,
                            protectedStructurePieces
                    );
                }
            }
        }
        return placed;
    }

    private static List<String> routeHostsForCandidate(
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal,
            GeologyProvince province,
            FormationContextCache formationContexts,
            boolean usesBodyStyleContext
    ) {
        if (!usesBodyStyleContext || !occurrence.requiresBodyStyleContext(proposal.depositStyle(), province)) {
            return occurrence.hostLithologiesFor(proposal.depositStyle(), province);
        }
        String bodyStyle = formationContexts.bodyStyle(
                proposal.anchorX(),
                proposal.anchorY(),
                proposal.anchorZ()
        ).orElse(null);
        return occurrence.hostLithologiesFor(proposal.depositStyle(), province, bodyStyle);
    }

    private static boolean activeCandidate(
            StructureWorldAccess world,
            long worldSeed,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal,
            GeologyProvince province
    ) {
        double affinityMultiplier = occurrence.generation().depthMultiplier(proposal.anchorY())
                * occurrence.generation().provinceMultiplier(province)
                * biomeMultiplier(world, occurrence, proposal);
        return OreDepositExperiment.active(worldSeed, proposal, affinityMultiplier);
    }

    private static double biomeMultiplier(
            StructureWorldAccess world,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal
    ) {
        if (occurrence.generation().biomeMultipliers().isEmpty()) {
            return 1.0;
        }
        var serverWorld = world.toServerWorld();
        var chunkManager = serverWorld.getChunkManager();
        int surfaceY = Math.max(
                serverWorld.getSeaLevel(),
                (int) Math.floor(ChunkGeneratorTerrainMorphologySampler.terrainHeight(
                        serverWorld,
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

    private static boolean qualifiesTerrain(
            StructureWorldAccess world,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal
    ) {
        OreOccurrenceCatalog.TerrainFilter terrainFilter = occurrence.terrainFilter();
        if (terrainFilter.minimumReliefBlocks() == 0 && !terrainFilter.requirePositiveProminence()) {
            return true;
        }
        return terrainFilter.matches(ChunkGeneratorTerrainMorphologySampler.sample(
                world.toServerWorld(),
                proposal.anchorX(),
                proposal.anchorZ()
        ));
    }

    private static boolean intersectsChunk(
            OreDepositGeometry.Bounds bounds,
            int startX,
            int endX,
            int startZ,
            int endZ,
            VerticalEnvelope occupied
    ) {
        return bounds.maxX() >= startX && bounds.minX() <= endX
                && bounds.maxZ() >= startZ && bounds.minZ() <= endZ
                && bounds.maxY() >= occupied.minY() && bounds.minY() <= occupied.maxY();
    }

    private static int placeBody(
            StructureWorldAccess world,
            int startX,
            int endX,
            int startZ,
            int endZ,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositGeometry.Body body,
            OreDiscoveryStringers.Field discovery,
            OreDepositGeometry.Bounds bounds,
            LazyHostResolver hosts,
            Set<String> validHosts,
            VerticalEnvelope occupied,
            List<BlockBox> protectedStructurePieces
    ) {
        int minX = Math.max(startX, bounds.minX());
        int maxX = Math.min(endX, bounds.maxX());
        int minY = Math.max(occupied.minY(), bounds.minY());
        int maxY = Math.min(occupied.maxY(), bounds.maxY());
        int minZ = Math.max(startZ, bounds.minZ());
        int maxZ = Math.min(endZ, bounds.maxZ());
        OreDepositGeometry.Sampler sampler = body.sampler();
        OreDiscoveryStringers.Sampler discoverySampler = discovery.sampler();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();

        int placed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int columnMaxY = Math.min(maxY, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1);
                for (int y = minY; y <= columnMaxY; y++) {
                    if (placeVoxel(
                            world,
                            occurrence,
                            sampler,
                            discoverySampler,
                            hosts,
                            protectedStructurePieces,
                            validHosts,
                            mutable,
                            neighbor,
                            x,
                            y,
                            z
                    )) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean placeVoxel(
            StructureWorldAccess world,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositGeometry.Sampler sampler,
            OreDiscoveryStringers.Sampler discoverySampler,
            LazyHostResolver hosts,
            List<BlockBox> protectedStructurePieces,
            Set<String> validHosts,
            BlockPos.Mutable mutable,
            BlockPos.Mutable neighbor,
            int x,
            int y,
            int z
    ) {
        if (StructurePieceProtection.contains(protectedStructurePieces, x, y, z)) {
            return false;
        }
        OreDepositGeometry.Sample sample = sampler.sample(x, y, z);
        String host = null;
        boolean parentHost = false;
        if (sample.economic()) {
            host = supportedHost(hosts, mutable, x, y, z);
            if (host == null) {
                return false;
            }
            parentHost = validHosts.contains(host);
        }

        OreDiscoveryStringers.Proximity proximity = OreDiscoveryStringers.Proximity.OUTSIDE;
        if (!sample.economic() || !parentHost) {
            proximity = discoverySampler.proximity(x, y, z);
        }
        boolean stringer = proximity == OreDiscoveryStringers.Proximity.STRINGER;
        boolean exposedFringe = proximity == OreDiscoveryStringers.Proximity.NEAR_STRINGER
                && touchesAir(world, neighbor, x, y, z);
        if (!sample.economic() && !sample.trace() && !stringer && !exposedFringe) {
            return false;
        }

        if (host == null) {
            host = supportedHost(hosts, mutable, x, y, z);
            if (host == null) {
                return false;
            }
            parentHost = validHosts.contains(host);
        }
        boolean discoveryOre = stringer || exposedFringe;
        boolean exposedTrace = sample.trace()
                && (exposedFringe || touchesAir(world, neighbor, x, y, z));
        OreGrade grade = parentHost
                ? OreExposurePlacement.placementGrade(sample, exposedTrace, discoveryOre)
                : discoveryOre ? OreGrade.POOR : null;
        if (grade == null) {
            return false;
        }
        world.setBlockState(
                mutable,
                GeoStrataBlocks.oreState(occurrence.id(), occurrence.capNaturalGrade(grade), host),
                Block.NOTIFY_LISTENERS
        );
        return true;
    }

    private static String supportedHost(
            LazyHostResolver hosts,
            BlockPos.Mutable mutable,
            int x,
            int y,
            int z
    ) {
        mutable.set(x, y, z);
        String host = hosts.resolve(mutable);
        return host != null && OreHost.supports(host) ? host : null;
    }

    private static VerticalEnvelope occupiedEnvelope(Chunk chunk) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) {
                continue;
            }
            int sectionBottomY = chunk.sectionIndexToCoord(sectionIndex) * SECTION_SIZE;
            minY = Math.min(minY, sectionBottomY);
            maxY = Math.max(maxY, sectionBottomY + SECTION_SIZE - 1);
        }
        return minY <= maxY ? new VerticalEnvelope(minY, maxY) : null;
    }

    private static boolean touchesAir(
            StructureWorldAccess world,
            BlockPos.Mutable neighbor,
            int x,
            int y,
            int z
    ) {
        return isAir(world, neighbor, x + 1, y, z)
                || isAir(world, neighbor, x - 1, y, z)
                || isAir(world, neighbor, x, y + 1, z)
                || isAir(world, neighbor, x, y - 1, z)
                || isAir(world, neighbor, x, y, z + 1)
                || isAir(world, neighbor, x, y, z - 1);
    }

    private static boolean isAir(
            StructureWorldAccess world,
            BlockPos.Mutable pos,
            int x,
            int y,
            int z
    ) {
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }
        pos.set(x, y, z);
        return world.getBlockState(pos).isAir();
    }

    private static Map<Block, String> hostBlocks(LithologyCatalog.Snapshot lithologies) {
        Map<Block, String> result = new HashMap<>();
        for (LithologyCatalog.Entry entry : lithologies.entries()) {
            Identifier blockId = Identifier.tryParse(entry.block());
            if (blockId == null) {
                throw new IllegalStateException("Invalid lithology block id: " + entry.block());
            }
            Block block = Registries.BLOCK.getOrEmpty(blockId)
                    .orElseThrow(() -> new IllegalStateException("Missing lithology block: " + blockId));
            result.put(block, entry.id());
        }
        return Map.copyOf(result);
    }

    private static TagKey<Block> blockTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid block tag id: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BLOCK, id);
    }

    private static TagKey<Biome> biomeTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid biome tag id: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BIOME, id);
    }

    private record VerticalEnvelope(int minY, int maxY) {
    }

    private static final class ProvinceSampleCache {
        private final long worldSeed;
        private final Map<Long, GeologyProvinceSampler.Context> contexts = new HashMap<>();

        private ProvinceSampleCache(long worldSeed) {
            this.worldSeed = worldSeed;
        }

        private GeologyProvinceSampler.Sample sample(int x, int z) {
            return contextFor(x, z).sample(x, z);
        }

        private GeologyProvinceSampler.Context contextFor(int x, int z) {
            int cellX = Math.floorDiv(x, GeologyProvinceSampler.CELL_SIZE);
            int cellZ = Math.floorDiv(z, GeologyProvinceSampler.CELL_SIZE);
            long key = ((long) cellX << 32) ^ Integer.toUnsignedLong(cellZ);
            return contexts.computeIfAbsent(key, ignored -> {
                int minX = cellX * GeologyProvinceSampler.CELL_SIZE;
                int minZ = cellZ * GeologyProvinceSampler.CELL_SIZE;
                return GeologyProvinceSampler.context(
                        worldSeed,
                        minX,
                        minZ,
                        minX + GeologyProvinceSampler.CELL_SIZE - 1,
                        minZ + GeologyProvinceSampler.CELL_SIZE - 1
                );
            });
        }
    }

    private static final class FormationContextCache {
        private final StructureWorldAccess world;
        private Map<Long, Optional<GeologyResolver.PreparedChunk>> chunks;

        private FormationContextCache(StructureWorldAccess world) {
            this.world = world;
        }

        private Optional<String> bodyStyle(int x, int y, int z) {
            return preparedChunk(x, z)
                    .flatMap(chunk -> chunk.resolve(x, y, z))
                    .flatMap(GeologyResolver.Result::bodyStyle);
        }

        private Optional<GeologyResolver.PreparedChunk> preparedChunk(int x, int z) {
            if (chunks == null) {
                chunks = new HashMap<>();
            }
            int chunkX = Math.floorDiv(x, CHUNK_SIZE);
            int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
            long key = ((long) chunkX << 32) ^ Integer.toUnsignedLong(chunkZ);
            return chunks.computeIfAbsent(
                    key,
                    ignored -> GeologyResolver.prepareChunk(world.toServerWorld(), x, z)
            );
        }
    }

    private static final class LazyHostResolver {
        private final StructureWorldAccess world;
        private final int startX;
        private final int startZ;
        private final LithologyCatalog.Snapshot lithologies;
        private HostResolver delegate;

        private LazyHostResolver(
                StructureWorldAccess world,
                int startX,
                int startZ,
                LithologyCatalog.Snapshot lithologies
        ) {
            this.world = world;
            this.startX = startX;
            this.startZ = startZ;
            this.lithologies = lithologies;
        }

        private String resolve(BlockPos pos) {
            if (delegate == null) {
                delegate = HostResolver.forChunk(world, startX, startZ, lithologies);
            }
            return delegate.resolve(pos);
        }
    }

    private record HostResolver(
            StructureWorldAccess world,
            Map<Block, String> directHosts,
            Optional<GeologyResolver.PreparedChunk> semanticGeology,
            Optional<TagKey<Block>> virtualHostTag,
            int virtualMinY,
            int virtualMaxY
    ) {
        private static HostResolver forChunk(
                StructureWorldAccess world,
                int startX,
                int startZ,
                LithologyCatalog.Snapshot lithologies
        ) {
            Optional<GeologyResolver.PreparedChunk> semanticGeology = GeologyResolver.prepareChunk(
                    world.toServerWorld(),
                    startX + CHUNK_SIZE / 2,
                    startZ + CHUNK_SIZE / 2
            );
            if (semanticGeology.isEmpty()) {
                return new HostResolver(
                        world,
                        hostBlocks(lithologies),
                        Optional.empty(),
                        Optional.empty(),
                        1,
                        0
                );
            }

            CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
            int minY = Math.max(
                    world.getBottomY(),
                    world.getSeaLevel() + experiment.verticalWindow().minOffsetBlocks()
            );
            int maxY = Math.min(
                    world.getTopY() - 1,
                    world.getSeaLevel() + experiment.verticalWindow().maxOffsetBlocks()
            );
            return new HostResolver(
                    world,
                    hostBlocks(lithologies),
                    semanticGeology,
                    Optional.of(blockTag(experiment.hostBlockTag())),
                    minY,
                    maxY
            );
        }

        private String resolve(BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            String direct = directHosts.get(state.getBlock());
            if (direct != null) {
                return direct;
            }
            if (pos.getY() < virtualMinY || pos.getY() > virtualMaxY) {
                return null;
            }
            if (virtualHostTag.isEmpty() || !state.isIn(virtualHostTag.get())) {
                return null;
            }
            return semanticGeology
                    .flatMap(value -> value.resolve(pos.getX(), pos.getY(), pos.getZ()))
                    .map(GeologyResolver.Result::lithology)
                    .orElse(null);
        }
    }
}
