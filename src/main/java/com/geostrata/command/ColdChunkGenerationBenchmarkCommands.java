package com.geostrata.command;

import com.geostrata.GeoStrata;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Developer-only cold generation timer for a remote 10x10 region plus one-chunk halo. */
public final class ColdChunkGenerationBenchmarkCommands {
    private static final int GRID_CHUNKS = 10;
    private static final int GENERATION_HALO_CHUNKS = 1;
    private static final int MAX_WORLD_CHUNK = 1_874_999;
    private static final int MIN_BENCHMARK_CHUNK = -MAX_WORLD_CHUNK + GENERATION_HALO_CHUNKS;
    private static final int MAX_BENCHMARK_CHUNK = MAX_WORLD_CHUNK - (GRID_CHUNKS - 1) - GENERATION_HALO_CHUNKS;
    private static final int DEFAULT_MIN_CHUNK = -44;

    private ColdChunkGenerationBenchmarkCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("coldgen")
                                .then(CommandManager.literal("benchmark")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(context -> run(context.getSource(), DEFAULT_MIN_CHUNK))
                                        .then(CommandManager.argument(
                                                "minChunk",
                                                IntegerArgumentType.integer(MIN_BENCHMARK_CHUNK, MAX_BENCHMARK_CHUNK)
                                        ).executes(context -> run(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "minChunk")
                                        ))))))
        );
    }

    private static int run(ServerCommandSource source, int minChunk) {
        ServerWorld world = source.getWorld();
        int maxChunk = minChunk + GRID_CHUNKS - 1;
        int minGeneratedChunk = minChunk - GENERATION_HALO_CHUNKS;
        int maxGeneratedChunk = maxChunk + GENERATION_HALO_CHUNKS;
        int generatedWidth = maxGeneratedChunk - minGeneratedChunk + 1;
        int generatedChunkRequests = generatedWidth * generatedWidth;

        long started = System.nanoTime();
        for (int chunkX = minGeneratedChunk; chunkX <= maxGeneratedChunk; chunkX++) {
            for (int chunkZ = minGeneratedChunk; chunkZ <= maxGeneratedChunk; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }
        long elapsedNanos = System.nanoTime() - started;

        JsonObject result = new JsonObject();
        result.addProperty("seed", world.getSeed());
        result.addProperty("chunkMin", minChunk);
        result.addProperty("chunkMax", maxChunk);
        result.addProperty("chunkCount", GRID_CHUNKS * GRID_CHUNKS);
        result.addProperty("generationHaloChunks", GENERATION_HALO_CHUNKS);
        result.addProperty("generatedChunkRequests", generatedChunkRequests);
        result.addProperty("durationNanos", elapsedNanos);
        result.addProperty("durationMillis", elapsedNanos / 1_000_000.0);
        GeoStrata.LOGGER.info("GEOSTRATA_COLDGEN_BENCHMARK {}", result);
        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata cold generation benchmark complete: "
                                + generatedChunkRequests + " chunk requests in "
                                + String.format(java.util.Locale.ROOT, "%.1f", elapsedNanos / 1_000_000.0)
                                + " ms; see server log for JSON."
                ),
                false
        );
        return 1;
    }
}
