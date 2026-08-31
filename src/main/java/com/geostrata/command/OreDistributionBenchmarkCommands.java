package com.geostrata.command;

import com.geostrata.GeoStrata;
import com.geostrata.block.GradedOreBlock;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreGrade;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Developer-only distribution scan for comparing vanilla and GeoStrata ore visibility. */
public final class OreDistributionBenchmarkCommands {
    private static final int GRID_CHUNKS = 10;
    private static final int MIN_CHUNK = -5;
    private static final int MAX_CHUNK = MIN_CHUNK + GRID_CHUNKS - 1;
    private static final int PLANE_LOCAL_X = 8;
    private static final List<String> MATERIALS = List.of(
            "coal",
            "iron",
            "copper",
            "gold",
            "emerald",
            "diamond",
            "redstone",
            "lapis"
    );

    private OreDistributionBenchmarkCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("ore")
                                .then(CommandManager.literal("benchmark")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(context -> run(context.getSource())))))
        );
    }

    private static int run(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        BenchmarkStats stats = scan(world);
        JsonObject result = stats.toJson(world);
        String json = result.toString();
        GeoStrata.LOGGER.info("GEOSTRATA_ORE_BENCHMARK {}", json);
        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata ore benchmark complete: "
                                + stats.totalOreBlocks() + " ore blocks, "
                                + stats.totalExposedBlocks() + " touching air; see server log for JSON."
                ),
                false
        );
        return 1;
    }

    private static BenchmarkStats scan(ServerWorld world) {
        BenchmarkStats stats = new BenchmarkStats(world.getBottomY(), world.getTopY());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();
        for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; chunkX++) {
            for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; chunkZ++) {
                scanChunk(world, chunkX, chunkZ, stats, pos, neighbor);
            }
        }
        return stats;
    }

    private static void scanChunk(
            ServerWorld world,
            int chunkX,
            int chunkZ,
            BenchmarkStats stats,
            BlockPos.Mutable pos,
            BlockPos.Mutable neighbor
    ) {
        var chunk = world.getChunk(chunkX, chunkZ);
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        stats.recordProvince(GeologyProvinceSampler.sample(world.getSeed(), startX + 8, startZ + 8).province().id());
        long chunkKey = chunkKey(chunkX, chunkZ);
        for (int localX = 0; localX < 16; localX++) {
            int x = startX + localX;
            boolean plane = localX == PLANE_LOCAL_X;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = startZ + localZ;
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    pos.set(x, y, z);
                    OreSample sample = classify(chunk.getBlockState(pos));
                    if (sample == null) {
                        continue;
                    }
                    int airFaces = airFaces(world, x, y, z, neighbor);
                    stats.record(sample, plane, airFaces, chunkKey, pos.asLong());
                }
            }
        }
    }

    private static OreSample classify(BlockState state) {
        if (state.getBlock() instanceof GradedOreBlock graded) {
            return new OreSample(graded.material(), graded.grade(), true);
        }
        if (state.isIn(BlockTags.COAL_ORES)) {
            return OreSample.vanilla("coal");
        }
        if (state.isIn(BlockTags.IRON_ORES)) {
            return OreSample.vanilla("iron");
        }
        if (state.isIn(BlockTags.COPPER_ORES)) {
            return OreSample.vanilla("copper");
        }
        if (state.isIn(BlockTags.GOLD_ORES)) {
            return OreSample.vanilla("gold");
        }
        if (state.isIn(BlockTags.EMERALD_ORES)) {
            return OreSample.vanilla("emerald");
        }
        if (state.isIn(BlockTags.DIAMOND_ORES)) {
            return OreSample.vanilla("diamond");
        }
        if (state.isIn(BlockTags.REDSTONE_ORES)) {
            return OreSample.vanilla("redstone");
        }
        if (state.isIn(BlockTags.LAPIS_ORES)) {
            return OreSample.vanilla("lapis");
        }
        return null;
    }

    private static int airFaces(ServerWorld world, int x, int y, int z, BlockPos.Mutable neighbor) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            neighbor.set(
                    x + direction.getOffsetX(),
                    y + direction.getOffsetY(),
                    z + direction.getOffsetZ()
            );
            if (world.getBlockState(neighbor).isAir()) {
                count++;
            }
        }
        return count;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int exposedClusters(Set<Long> exposedPositions) {
        Set<Long> remaining = new HashSet<>(exposedPositions);
        ArrayDeque<Long> queue = new ArrayDeque<>();
        int clusters = 0;
        while (!remaining.isEmpty()) {
            Long first = remaining.iterator().next();
            remaining.remove(first);
            queue.add(first);
            clusters++;
            consumeCluster(remaining, queue);
        }
        return clusters;
    }

    private static void consumeCluster(Set<Long> remaining, ArrayDeque<Long> queue) {
        while (!queue.isEmpty()) {
            BlockPos pos = BlockPos.fromLong(queue.removeFirst());
            for (Direction direction : Direction.values()) {
                long neighbor = pos.offset(direction).asLong();
                if (remaining.remove(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
    }

    private record OreSample(String material, OreGrade grade, boolean graded) {
        private static OreSample vanilla(String material) {
            return new OreSample(material, null, false);
        }
    }

    private static final class MaterialStats {
        private long total;
        private long exposed;
        private long airFaces;
        private long planeTotal;
        private long planeExposed;
        private long graded;
        private long vanillaTagged;
        private final EnumMap<OreGrade, Long> grades = new EnumMap<>(OreGrade.class);
        private final Set<Long> exposedChunks = new HashSet<>();
        private final Set<Long> exposedPositions = new HashSet<>();

        private void record(OreSample sample, boolean plane, int exposedFaces, long chunkKey, long pos) {
            total++;
            if (sample.graded()) {
                graded++;
                grades.merge(sample.grade(), 1L, Long::sum);
            } else {
                vanillaTagged++;
            }
            if (plane) {
                planeTotal++;
            }
            if (exposedFaces == 0) {
                return;
            }
            exposed++;
            airFaces += exposedFaces;
            exposedChunks.add(chunkKey);
            exposedPositions.add(pos);
            if (plane) {
                planeExposed++;
            }
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("total", total);
            json.addProperty("airExposed", exposed);
            json.addProperty("airExposurePercent", total == 0 ? 0.0 : exposed * 100.0 / total);
            json.addProperty("airFaces", airFaces);
            json.addProperty("chunksWithAirExposed", exposedChunks.size());
            json.addProperty("airExposedClusters", exposedClusters(exposedPositions));
            json.addProperty("planeTotal", planeTotal);
            json.addProperty("planeAirExposed", planeExposed);
            json.addProperty("gradedBlocks", graded);
            json.addProperty("vanillaTaggedBlocks", vanillaTagged);
            JsonObject gradeJson = new JsonObject();
            for (OreGrade grade : OreGrade.values()) {
                gradeJson.addProperty(grade.id(), grades.getOrDefault(grade, 0L));
            }
            json.add("grades", gradeJson);
            return json;
        }
    }

    private static final class BenchmarkStats {
        private final int bottomY;
        private final int topY;
        private final Map<String, MaterialStats> materials = new LinkedHashMap<>();
        private final Map<String, Integer> provinceChunks = new LinkedHashMap<>();

        private BenchmarkStats(int bottomY, int topY) {
            this.bottomY = bottomY;
            this.topY = topY;
            for (String material : MATERIALS) {
                materials.put(material, new MaterialStats());
            }
        }

        private void record(OreSample sample, boolean plane, int airFaces, long chunkKey, long pos) {
            materials.computeIfAbsent(sample.material(), ignored -> new MaterialStats())
                    .record(sample, plane, airFaces, chunkKey, pos);
        }

        private void recordProvince(String province) {
            provinceChunks.merge(province, 1, Integer::sum);
        }

        private long totalOreBlocks() {
            return materials.values().stream().mapToLong(stats -> stats.total).sum();
        }

        private long totalExposedBlocks() {
            return materials.values().stream().mapToLong(stats -> stats.exposed).sum();
        }

        private JsonObject toJson(ServerWorld world) {
            JsonObject root = new JsonObject();
            root.addProperty("mode", OreDepositExperiment.current().enabled() ? "geostrata_experiment" : "vanilla_baseline");
            root.addProperty("seed", world.getSeed());
            root.addProperty("chunkMin", MIN_CHUNK);
            root.addProperty("chunkMax", MAX_CHUNK);
            root.addProperty("chunkCount", GRID_CHUNKS * GRID_CHUNKS);
            root.addProperty("bottomY", bottomY);
            root.addProperty("topYExclusive", topY);
            root.addProperty("blocksScanned", (long) GRID_CHUNKS * GRID_CHUNKS * 16L * 16L * (topY - bottomY));
            root.addProperty("planeBlocksScanned", (long) GRID_CHUNKS * GRID_CHUNKS * 16L * (topY - bottomY));
            root.addProperty("totalOreBlocks", totalOreBlocks());
            root.addProperty("totalAirExposedOreBlocks", totalExposedBlocks());
            JsonObject provinceJson = new JsonObject();
            provinceChunks.forEach(provinceJson::addProperty);
            root.add("provinceChunks", provinceJson);
            JsonObject materialJson = new JsonObject();
            materials.forEach((material, stats) -> materialJson.add(material, stats.toJson()));
            root.add("materials", materialJson);
            return root;
        }
    }
}
