package com.geostrata.command;

import com.geostrata.GeoStrata;
import com.geostrata.block.GradedOreBlock;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Developer-only iron Y-distribution scan for paired vanilla/GeoStrata benchmarks. */
public final class OreVerticalDistributionCommands {
    private static final int GRID_CHUNKS = 10;
    private static final int MIN_CHUNK = -5;
    private static final int MAX_CHUNK = MIN_CHUNK + GRID_CHUNKS - 1;
    private static final int BAND_HEIGHT = 16;

    private OreVerticalDistributionCommands() {
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
                        "Iron vertical benchmark complete: " + stats.total
                                + " blocks; see server log for 16-block Y bands."
                ),
                false
        );
        return 1;
    }

    private static Stats scan(ServerWorld world) {
        Stats stats = new Stats();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; chunkX++) {
            for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; chunkZ++) {
                scanChunk(world, chunkX, chunkZ, pos, stats);
            }
        }
        return stats;
    }

    private static void scanChunk(
            ServerWorld world,
            int chunkX,
            int chunkZ,
            BlockPos.Mutable pos,
            Stats stats
    ) {
        var chunk = world.getChunk(chunkX, chunkZ);
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                scanColumn(world, chunk, startX + localX, startZ + localZ, pos, stats);
            }
        }
    }

    private static void scanColumn(
            ServerWorld world,
            net.minecraft.world.chunk.Chunk chunk,
            int x,
            int z,
            BlockPos.Mutable pos,
            Stats stats
    ) {
        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            pos.set(x, y, z);
            if (isIron(chunk.getBlockState(pos))) {
                stats.record(y);
            }
        }
    }

    private static boolean isIron(BlockState state) {
        return state.isIn(BlockTags.IRON_ORES)
                || state.getBlock() instanceof GradedOreBlock graded && "iron".equals(graded.material());
    }

    private static final class Stats {
        private final Map<Integer, Long> bands = new LinkedHashMap<>();
        private long total;
        private long ySum;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private void record(int y) {
            total++;
            ySum += y;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            bands.merge(Math.floorDiv(y, BAND_HEIGHT) * BAND_HEIGHT, 1L, Long::sum);
        }

        private JsonObject toJson(ServerWorld world) {
            JsonObject result = new JsonObject();
            result.addProperty("seed", world.getSeed());
            result.addProperty("chunkMin", MIN_CHUNK);
            result.addProperty("chunkMax", MAX_CHUNK);
            result.addProperty("chunkCount", GRID_CHUNKS * GRID_CHUNKS);
            result.addProperty("bandHeight", BAND_HEIGHT);
            result.addProperty("ironBlocks", total);
            result.addProperty("minY", total == 0 ? null : minY);
            result.addProperty("maxY", total == 0 ? null : maxY);
            result.addProperty("meanY", total == 0 ? null : ySum / (double) total);
            JsonObject bandJson = new JsonObject();
            bands.forEach((startY, count) -> bandJson.add(
                    startY + ".." + (startY + BAND_HEIGHT - 1),
                    band(count)
            ));
            result.add("yBands", bandJson);
            return result;
        }

        private JsonObject band(long count) {
            JsonObject band = new JsonObject();
            band.addProperty("blocks", count);
            band.addProperty("share", total == 0 ? 0.0 : count / (double) total);
            return band;
        }
    }
}
