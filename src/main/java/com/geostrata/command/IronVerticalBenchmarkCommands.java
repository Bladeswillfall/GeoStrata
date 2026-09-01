package com.geostrata.command;

import com.geostrata.GeoStrata;
import com.geostrata.block.GradedOreBlock;
import com.geostrata.geology.OreDepositExperiment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Developer-only fixed-window scan for comparing vanilla and GeoStrata iron by Y. */
public final class IronVerticalBenchmarkCommands {
    private static final int GRID_CHUNKS = 10;
    private static final int MIN_CHUNK = -5;
    private static final int MAX_CHUNK = MIN_CHUNK + GRID_CHUNKS - 1;
    private static final int GENERATION_HALO_CHUNKS = 1;
    private static final int BAND_HEIGHT = 16;

    private IronVerticalBenchmarkCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("ore")
                                .then(CommandManager.literal("vertical-benchmark")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(context -> run(context.getSource())))))
        );
    }

    private static int run(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Stats stats = scan(world);
        JsonObject result = stats.toJson(world);
        GeoStrata.LOGGER.info("GEOSTRATA_IRON_VERTICAL_BENCHMARK {}", result);
        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata iron vertical benchmark complete: "
                                + stats.total + " iron blocks across " + stats.occupiedYLevels.size() + " Y-levels."
                ),
                false
        );
        return 1;
    }

    private static Stats scan(ServerWorld world) {
        for (int chunkX = MIN_CHUNK - GENERATION_HALO_CHUNKS;
                chunkX <= MAX_CHUNK + GENERATION_HALO_CHUNKS;
                chunkX++) {
            for (int chunkZ = MIN_CHUNK - GENERATION_HALO_CHUNKS;
                    chunkZ <= MAX_CHUNK + GENERATION_HALO_CHUNKS;
                    chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }

        Stats stats = new Stats();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; chunkX++) {
            for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; chunkZ++) {
                scanChunk(world, chunkX, chunkZ, stats, pos);
            }
        }
        return stats;
    }

    private static void scanChunk(
            ServerWorld world,
            int chunkX,
            int chunkZ,
            Stats stats,
            BlockPos.Mutable pos
    ) {
        var chunk = world.getChunk(chunkX, chunkZ);
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        for (int localX = 0; localX < 16; localX++) {
            int x = startX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = startZ + localZ;
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    pos.set(x, y, z);
                    Source oreSource = ironSource(chunk.getBlockState(pos));
                    if (oreSource != null) {
                        stats.record(y, oreSource);
                    }
                }
            }
        }
    }

    private static Source ironSource(BlockState state) {
        if (state.getBlock() instanceof GradedOreBlock graded && "iron".equals(graded.material())) {
            return Source.GRADED;
        }
        return state.isIn(BlockTags.IRON_ORES) ? Source.VANILLA_TAGGED : null;
    }

    private enum Source {
        GRADED,
        VANILLA_TAGGED
    }

    private static final class Stats {
        private long total;
        private long graded;
        private long vanillaTagged;
        private long ySum;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private final Set<Integer> occupiedYLevels = new HashSet<>();
        private final Map<Integer, Long> bands = new TreeMap<>();

        private void record(int y, Source source) {
            total++;
            if (source == Source.GRADED) {
                graded++;
            } else {
                vanillaTagged++;
            }
            ySum += y;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            occupiedYLevels.add(y);
            bands.merge(Math.floorDiv(y, BAND_HEIGHT) * BAND_HEIGHT, 1L, Long::sum);
        }

        private JsonObject toJson(ServerWorld world) {
            JsonObject root = new JsonObject();
            root.addProperty(
                    "mode",
                    OreDepositExperiment.current().enabled() ? "geostrata_experiment" : "vanilla_baseline"
            );
            root.addProperty("seed", world.getSeed());
            root.addProperty("chunkMin", MIN_CHUNK);
            root.addProperty("chunkMax", MAX_CHUNK);
            root.addProperty("chunkCount", GRID_CHUNKS * GRID_CHUNKS);
            root.addProperty("bandHeight", BAND_HEIGHT);
            root.addProperty("bottomY", world.getBottomY());
            root.addProperty("topYExclusive", world.getTopY());
            root.addProperty("totalIronBlocks", total);
            root.addProperty("gradedBlocks", graded);
            root.addProperty("vanillaTaggedBlocks", vanillaTagged);
            root.addProperty("blocksPerChunk", total / (double) (GRID_CHUNKS * GRID_CHUNKS));
            root.addProperty("minY", total == 0 ? null : minY);
            root.addProperty("maxY", total == 0 ? null : maxY);
            root.addProperty("meanY", total == 0 ? null : ySum / (double) total);
            root.addProperty("occupiedYLevels", occupiedYLevels.size());

            JsonArray verticalBands = new JsonArray();
            bands.forEach((startY, count) -> {
                JsonObject band = new JsonObject();
                band.addProperty("minY", startY);
                band.addProperty("maxY", startY + BAND_HEIGHT - 1);
                band.addProperty("count", count);
                band.addProperty("sharePercent", total == 0 ? 0.0 : count * 100.0 / total);
                verticalBands.add(band);
            });
            root.add("verticalBands", verticalBands);
            return root;
        }
    }
}
