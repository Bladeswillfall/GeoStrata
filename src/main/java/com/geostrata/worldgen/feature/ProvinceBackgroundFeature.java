package com.geostrata.worldgen.feature;

import com.geostrata.geology.CorrelatedExperimentChunkOwnership;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.ProvinceBackgroundRuntime;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Late companion fill for eligible natural host stone using the shared semantic background runtime. */
public final class ProvinceBackgroundFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;

    public ProvinceBackgroundFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!experiment.enabled() || !catalog.loaded()) {
            return false;
        }

        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        if (CorrelatedExperimentChunkOwnership.ownershipForChunk(world.getSeed(), startX, startZ).owned()) {
            return false;
        }
        Optional<ProvinceBackgroundRuntime.Chunk> geology = ProvinceBackgroundRuntime.resolve(
                world.toServerWorld(),
                startX,
                startZ
        );
        if (geology.isEmpty()) {
            return false;
        }

        int minY = Math.max(
                world.getBottomY(),
                world.getSeaLevel() + experiment.verticalWindow().minOffsetBlocks()
        );
        int maxY = Math.min(
                world.getTopY() - 1,
                world.getSeaLevel() + experiment.verticalWindow().maxOffsetBlocks()
        );
        if (minY > maxY) {
            return false;
        }

        return replaceChunk(
                world,
                startX,
                startZ,
                minY,
                maxY,
                hostTag(experiment.hostBlockTag()),
                geology.get(),
                outputStates(catalog)
        ) > 0;
    }

    private static int replaceChunk(
            StructureWorldAccess world,
            int startX,
            int startZ,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            ProvinceBackgroundRuntime.Chunk geology,
            Map<String, BlockState> outputStates
    ) {
        Chunk chunk = world.getChunk(
                Math.floorDiv(startX, CHUNK_SIZE),
                Math.floorDiv(startZ, CHUNK_SIZE)
        );
        List<BlockBox> protectedStructurePieces = StructurePieceProtection.forChunk(world, chunk);

        int placed = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) {
                continue;
            }

            int sectionBottomY = chunk.sectionIndexToCoord(sectionIndex) * SECTION_SIZE;
            int sectionMinY = Math.max(minY, sectionBottomY);
            int sectionMaxY = Math.min(maxY, sectionBottomY + SECTION_SIZE - 1);
            if (sectionMinY > sectionMaxY || !section.hasAny(state -> state.isIn(hostTag))) {
                continue;
            }

            placed += replaceSection(
                    section,
                    startX,
                    startZ,
                    sectionBottomY,
                    sectionMinY,
                    sectionMaxY,
                    hostTag,
                    geology,
                    outputStates,
                    protectedStructurePieces
            );
        }
        if (placed > 0) {
            chunk.setNeedsSaving(true);
        }
        return placed;
    }

    private static int replaceSection(
            ChunkSection section,
            int startX,
            int startZ,
            int sectionBottomY,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            ProvinceBackgroundRuntime.Chunk geology,
            Map<String, BlockState> outputStates,
            List<BlockBox> protectedStructurePieces
    ) {
        SectionReplaceContext replacement = new SectionReplaceContext(
                section.getBlockStateContainer(),
                sectionBottomY,
                minY - sectionBottomY,
                maxY - sectionBottomY,
                hostTag,
                geology,
                outputStates,
                protectedStructurePieces
        );
        int placed = 0;
        section.lock();
        try {
            for (int localX = 0; localX < SECTION_SIZE; localX++) {
                int worldX = startX + localX;
                for (int localZ = 0; localZ < SECTION_SIZE; localZ++) {
                    placed += replaceColumn(replacement, localX, localZ, worldX, startZ + localZ);
                }
            }
            if (placed > 0) {
                section.calculateCounts();
            }
        } finally {
            section.unlock();
        }
        return placed;
    }

    private static int replaceColumn(
            SectionReplaceContext context,
            int localX,
            int localZ,
            int worldX,
            int worldZ
    ) {
        int placed = 0;
        for (int localY = context.minLocalY(); localY <= context.maxLocalY(); localY++) {
            BlockState existing = context.states().get(localX, localY, localZ);
            if (!existing.isIn(context.hostTag())) {
                continue;
            }
            int worldY = context.sectionBottomY() + localY;
            if (StructurePieceProtection.contains(context.protectedStructurePieces(), worldX, worldY, worldZ)) {
                continue;
            }

            String lithology = context.geology().lithologyAt(worldX, worldY, worldZ);
            BlockState replacement = context.outputStates().get(lithology);
            if (replacement != null && !existing.equals(replacement)) {
                context.states().swapUnsafe(localX, localY, localZ, replacement);
                placed++;
            }
        }
        return placed;
    }

    private static Map<String, BlockState> outputStates(LithologyCatalog.Snapshot catalog) {
        Map<String, BlockState> states = new HashMap<>();
        for (LithologyCatalog.Entry entry : catalog.entries()) {
            Identifier blockId = Identifier.tryParse(entry.block());
            if (blockId == null) {
                throw new IllegalStateException("Invalid background lithology block id: " + entry.block());
            }
            Block block = Registries.BLOCK.getOrEmpty(blockId)
                    .orElseThrow(() -> new IllegalStateException("Missing background lithology block: " + blockId));
            states.put(entry.id(), block.getDefaultState());
        }
        return Map.copyOf(states);
    }

    private static TagKey<Block> hostTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid background geology host block tag: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BLOCK, id);
    }

    private record SectionReplaceContext(
            PalettedContainer<BlockState> states,
            int sectionBottomY,
            int minLocalY,
            int maxLocalY,
            TagKey<Block> hostTag,
            ProvinceBackgroundRuntime.Chunk geology,
            Map<String, BlockState> outputStates,
            List<BlockBox> protectedStructurePieces
    ) {
    }
}
