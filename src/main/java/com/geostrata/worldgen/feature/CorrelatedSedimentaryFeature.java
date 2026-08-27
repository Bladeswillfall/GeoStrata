package com.geostrata.worldgen.feature;

import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.CorrelatedSedimentaryRuntime;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.SedimentaryContactPlanner;
import com.geostrata.geology.SedimentaryStratigraphicField;
import com.geostrata.geology.SedimentarySuccessions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Experimental chunk-local consumer of the correlated sedimentary field. */
public final class CorrelatedSedimentaryFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;

    public CorrelatedSedimentaryFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Optional<CorrelatedSedimentaryRuntime.Site> resolved = CorrelatedSedimentaryRuntime.resolve(
                world.getSeed(),
                origin.getX(),
                origin.getZ()
        );
        if (resolved.isEmpty()) {
            return false;
        }

        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!catalog.loaded()) {
            return false;
        }

        CorrelatedSedimentaryRuntime.Site site = resolved.get();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        TagKey<Block> hostTag = hostTag(experiment.hostBlockTag());
        Map<String, BlockState> outputStates = outputStates(site.succession(), catalog);

        int seaLevel = world.getSeaLevel();
        int minY = Math.max(world.getBottomY(), seaLevel + experiment.verticalWindow().minOffsetBlocks());
        int maxY = Math.min(world.getTopY() - 1, seaLevel + experiment.verticalWindow().maxOffsetBlocks());
        if (minY > maxY) {
            return false;
        }

        return replaceChunk(
                world,
                startX,
                startZ,
                minY,
                maxY,
                hostTag,
                site.field(),
                site.plan(),
                outputStates
        ) > 0;
    }

    private static int replaceChunk(
            StructureWorldAccess world,
            int startX,
            int startZ,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            SedimentaryStratigraphicField.Field field,
            SedimentaryContactPlanner.Plan plan,
            Map<String, BlockState> outputStates
    ) {
        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = startX; x < startX + CHUNK_SIZE; x++) {
            for (int z = startZ; z < startZ + CHUNK_SIZE; z++) {
                placed += replaceColumn(world, x, z, minY, maxY, hostTag, field, plan, outputStates, mutable);
            }
        }
        return placed;
    }

    private static int replaceColumn(
            StructureWorldAccess world,
            int x,
            int z,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            SedimentaryStratigraphicField.Field field,
            SedimentaryContactPlanner.Plan plan,
            Map<String, BlockState> outputStates,
            BlockPos.Mutable mutable
    ) {
        int placed = 0;
        for (int y = minY; y <= maxY; y++) {
            mutable.set(x, y, z);
            if (!world.getBlockState(mutable).isIn(hostTag)) {
                continue;
            }

            SedimentaryStratigraphicField.Sample sample = field.sample(x, y, z, plan);
            BlockState replacement = outputStates.get(sample.bed().lithology());
            if (replacement == null) {
                throw new IllegalStateException(
                        "No resolved block state for correlated lithology " + sample.bed().lithology()
                );
            }
            world.setBlockState(mutable, replacement, Block.NOTIFY_LISTENERS);
            placed++;
        }
        return placed;
    }

    private static TagKey<Block> hostTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid correlated experiment host block tag: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BLOCK, id);
    }

    private static Map<String, BlockState> outputStates(
            SedimentarySuccessions.Succession succession,
            LithologyCatalog.Snapshot catalog
    ) {
        Set<String> lithologies = new LinkedHashSet<>();
        for (SedimentarySuccessions.Bed bed : succession.beds()) {
            lithologies.add(bed.lithology());
        }

        Map<String, BlockState> states = new HashMap<>();
        for (String lithology : lithologies) {
            LithologyCatalog.Entry entry = catalog.require(lithology);
            Identifier blockId = Identifier.tryParse(entry.block());
            if (blockId == null) {
                throw new IllegalStateException("Invalid correlated lithology block id: " + entry.block());
            }
            Block block = Registries.BLOCK.getOrEmpty(blockId)
                    .orElseThrow(() -> new IllegalStateException("Missing correlated lithology block: " + blockId));
            states.put(lithology, block.getDefaultState());
        }
        return states;
    }
}
