package com.geostrata.command;

import com.geostrata.GeoStrata;
import com.geostrata.block.GradedOreBlock;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
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
    private static final int GENERATION_HALO_CHUNKS = 1;
    private static final int MAX_WORLD_CHUNK = 1_874_999;
    private static final int MIN_BENCHMARK_CHUNK = -MAX_WORLD_CHUNK + GENERATION_HALO_CHUNKS;
    private static final int MAX_BENCHMARK_CHUNK = MAX_WORLD_CHUNK - (GRID_CHUNKS - 1) - GENERATION_HALO_CHUNKS;
    private static final int DEFAULT_MIN_CHUNK = -44;
    private static final int AIR_PROXIMITY_RADIUS = 12;
    private static final int PLANE_LOCAL_X = 8;
    private static final int Y_BAND_HEIGHT = 16;
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
        BenchmarkStats stats = scan(world, minChunk, maxChunk);
        JsonObject result = stats.toJson(world, minChunk, maxChunk);
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

    private static BenchmarkStats scan(ServerWorld world, int minChunk, int maxChunk) {
        for (int chunkX = minChunk - GENERATION_HALO_CHUNKS;
                chunkX <= maxChunk + GENERATION_HALO_CHUNKS;
                chunkX++) {
            for (int chunkZ = minChunk - GENERATION_HALO_CHUNKS;
                    chunkZ <= maxChunk + GENERATION_HALO_CHUNKS;
                    chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }

        BenchmarkStats stats = new BenchmarkStats(world.getBottomY(), world.getTopY());
        Map<Block, OreSample> naturalOverrides = naturalOverrideSamples();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();
        for (int chunkX = minChunk; chunkX <= maxChunk; chunkX++) {
            for (int chunkZ = minChunk; chunkZ <= maxChunk; chunkZ++) {
                scanChunk(world, chunkX, chunkZ, stats, naturalOverrides, pos, neighbor);
            }
        }
        return stats;
    }

    private static void scanChunk(
            ServerWorld world,
            int chunkX,
            int chunkZ,
            BenchmarkStats stats,
            Map<Block, OreSample> naturalOverrides,
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
                    OreSample sample = classify(chunk.getBlockState(pos), naturalOverrides);
                    if (sample == null) {
                        continue;
                    }
                    int airFaces = airFaces(world, x, y, z, neighbor);
                    int nearestAir = sample.graded()
                            ? nearestAirDistance(world, x, y, z, neighbor)
                            : AIR_PROXIMITY_RADIUS + 1;
                    stats.record(sample, plane, airFaces, chunkKey, pos.asLong(), y, nearestAir);
                }
            }
        }
    }

    private static Map<Block, OreSample> naturalOverrideSamples() {
        Map<Block, OreSample> overrides = new HashMap<>();
        for (OreOccurrenceCatalog.Occurrence occurrence : OreOccurrenceCatalog.current().occurrences()) {
            for (Map.Entry<OreGrade, String> entry : occurrence.naturalBlockOverrides().entrySet()) {
                Block block = Registries.BLOCK.get(new net.minecraft.util.Identifier(entry.getValue()));
                overrides.put(block, new OreSample(occurrence.id(), entry.getKey(), true, "natural_block"));
            }
        }
        return Map.copyOf(overrides);
    }

    private static OreSample classify(BlockState state, Map<Block, OreSample> naturalOverrides) {
        if (state.getBlock() instanceof GradedOreBlock graded) {
            return new OreSample(
                    graded.material(),
                    graded.grade(),
                    true,
                    state.get(GradedOreBlock.HOST).asString()
            );
        }
        OreSample naturalOverride = naturalOverrides.get(state.getBlock());
        if (naturalOverride != null) {
            return naturalOverride;
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

    private static int nearestAirDistance(
            ServerWorld world,
            int x,
            int y,
            int z,
            BlockPos.Mutable neighbor
    ) {
        for (int distance = 1; distance <= AIR_PROXIMITY_RADIUS; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int remainingAfterX = distance - Math.abs(dx);
                for (int dy = -remainingAfterX; dy <= remainingAfterX; dy++) {
                    int sampleY = y + dy;
                    if (sampleY < world.getBottomY() || sampleY >= world.getTopY()) {
                        continue;
                    }
                    int dz = remainingAfterX - Math.abs(dy);
                    if (isAir(world, neighbor, x + dx, sampleY, z + dz)
                            || (dz != 0 && isAir(world, neighbor, x + dx, sampleY, z - dz))) {
                        return distance;
                    }
                }
            }
        }
        return AIR_PROXIMITY_RADIUS + 1;
    }

    private static boolean isAir(
            ServerWorld world,
            BlockPos.Mutable pos,
            int x,
            int y,
            int z
    ) {
        pos.set(x, y, z);
        return world.getBlockState(pos).isAir();
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

    record OreSample(String material, OreGrade grade, boolean graded, String host) {
        private static OreSample vanilla(String material) {
            return new OreSample(material, null, false, null);
        }
    }

    static final class MaterialStats {
        private long total;
        private long exposed;
        private long airFaces;
        private long planeTotal;
        private long planeExposed;
        private long graded;
        private long vanillaTagged;
        private long gradedExposed;
        private long vanillaExposed;
        private long ySum;
        private long gradedYSum;
        private long gradedWithin2Air;
        private long gradedWithin4Air;
        private long gradedWithin8Air;
        private long gradedWithin12Air;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int gradedMinY = Integer.MAX_VALUE;
        private int gradedMaxY = Integer.MIN_VALUE;
        private int gradedMinAirDistance = Integer.MAX_VALUE;
        private final EnumMap<OreGrade, Long> grades = new EnumMap<>(OreGrade.class);
        private final Map<String, EnumMap<OreGrade, Long>> gradesByHost = new LinkedHashMap<>();
        private final Map<Integer, Long> yBands = new LinkedHashMap<>();
        private final Map<String, Map<Integer, Long>> gradedYBandsByHost = new LinkedHashMap<>();
        private final Set<Long> exposedChunks = new HashSet<>();
        private final Set<Long> exposedPositions = new HashSet<>();
        private final Set<Long> gradedExposedPositions = new HashSet<>();
        private final Set<Long> vanillaExposedPositions = new HashSet<>();

        void record(
                OreSample sample,
                boolean plane,
                int exposedFaces,
                long chunkKey,
                long pos,
                int y,
                int nearestAir
        ) {
            total++;
            ySum += y;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            int yBand = Math.floorDiv(y, Y_BAND_HEIGHT) * Y_BAND_HEIGHT;
            yBands.merge(yBand, 1L, Long::sum);
            if (sample.graded()) {
                graded++;
                gradedYSum += y;
                gradedMinY = Math.min(gradedMinY, y);
                gradedMaxY = Math.max(gradedMaxY, y);
                gradedMinAirDistance = Math.min(gradedMinAirDistance, nearestAir);
                if (nearestAir <= 2) {
                    gradedWithin2Air++;
                }
                if (nearestAir <= 4) {
                    gradedWithin4Air++;
                }
                if (nearestAir <= 8) {
                    gradedWithin8Air++;
                }
                if (nearestAir <= 12) {
                    gradedWithin12Air++;
                }
                grades.merge(sample.grade(), 1L, Long::sum);
                gradesByHost.computeIfAbsent(sample.host(), ignored -> new EnumMap<>(OreGrade.class))
                        .merge(sample.grade(), 1L, Long::sum);
                gradedYBandsByHost.computeIfAbsent(sample.host(), ignored -> new LinkedHashMap<>())
                        .merge(yBand, 1L, Long::sum);
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
            recordSourceExposure(sample, pos);
            if (plane) {
                planeExposed++;
            }
        }

        private void recordSourceExposure(OreSample sample, long pos) {
            if (sample.graded()) {
                gradedExposed++;
                gradedExposedPositions.add(pos);
            } else {
                vanillaExposed++;
                vanillaExposedPositions.add(pos);
            }
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("total", total);
            json.addProperty("airExposed", exposed);
            json.addProperty("airExposurePercent", total == 0 ? 0.0 : exposed * 100.0 / total);
            json.addProperty("airFaces", airFaces);
            json.addProperty("chunksWithAirExposed", exposedChunks.size());
            json.addProperty("airExposedClusters", exposedClusters(exposedPositions));
            json.addProperty("planeTotal", planeTotal);
            json.addProperty("planeAirExposed", planeExposed);
            json.addProperty("minY", total == 0 ? null : minY);
            json.addProperty("maxY", total == 0 ? null : maxY);
            json.addProperty("meanY", total == 0 ? null : ySum / (double) total);
            json.add("yBands", yBandsJson());
            json.addProperty("gradedBlocks", graded);
            json.addProperty("vanillaTaggedBlocks", vanillaTagged);
            json.addProperty("gradedAirExposed", gradedExposed);
            json.addProperty("vanillaTaggedAirExposed", vanillaExposed);
            json.addProperty("gradedAirExposedClusters", exposedClusters(gradedExposedPositions));
            json.addProperty("vanillaTaggedAirExposedClusters", exposedClusters(vanillaExposedPositions));
            json.addProperty("gradedMinY", graded == 0 ? null : gradedMinY);
            json.addProperty("gradedMaxY", graded == 0 ? null : gradedMaxY);
            json.addProperty("gradedMeanY", graded == 0 ? null : gradedYSum / (double) graded);
            json.addProperty(
                    "gradedMinAirDistance",
                    graded == 0 || gradedMinAirDistance > AIR_PROXIMITY_RADIUS ? null : gradedMinAirDistance
            );
            json.addProperty("gradedWithin2Air", gradedWithin2Air);
            json.addProperty("gradedWithin4Air", gradedWithin4Air);
            json.addProperty("gradedWithin8Air", gradedWithin8Air);
            json.addProperty("gradedWithin12Air", gradedWithin12Air);
            JsonObject gradeJson = new JsonObject();
            for (OreGrade grade : OreGrade.values()) {
                gradeJson.addProperty(grade.id(), grades.getOrDefault(grade, 0L));
            }
            json.add("grades", gradeJson);
            JsonObject hostJson = new JsonObject();
            gradesByHost.forEach((host, hostGrades) -> {
                JsonObject hostGradeJson = new JsonObject();
                for (OreGrade grade : OreGrade.values()) {
                    hostGradeJson.addProperty(grade.id(), hostGrades.getOrDefault(grade, 0L));
                }
                hostJson.add(host, hostGradeJson);
            });
            json.add("gradesByHost", hostJson);
            json.add("gradedYBandsByHost", gradedYBandsByHostJson());
            return json;
        }

        private JsonObject yBandsJson() {
            JsonObject bands = new JsonObject();
            yBands.forEach((startY, count) -> {
                JsonObject band = new JsonObject();
                band.addProperty("blocks", count);
                band.addProperty("share", total == 0 ? 0.0 : count / (double) total);
                bands.add(startY + ".." + (startY + Y_BAND_HEIGHT - 1), band);
            });
            return bands;
        }

        private JsonObject gradedYBandsByHostJson() {
            JsonObject hosts = new JsonObject();
            gradedYBandsByHost.forEach((host, hostBands) -> {
                JsonObject bands = new JsonObject();
                hostBands.forEach((startY, count) ->
                        bands.addProperty(startY + ".." + (startY + Y_BAND_HEIGHT - 1), count));
                hosts.add(host, bands);
            });
            return hosts;
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

        private void record(
                OreSample sample,
                boolean plane,
                int airFaces,
                long chunkKey,
                long pos,
                int y,
                int nearestAir
        ) {
            materials.computeIfAbsent(sample.material(), ignored -> new MaterialStats())
                    .record(sample, plane, airFaces, chunkKey, pos, y, nearestAir);
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

        private JsonObject toJson(ServerWorld world, int minChunk, int maxChunk) {
            JsonObject root = new JsonObject();
            root.addProperty("mode", OreDepositExperiment.current().enabled() ? "geostrata_experiment" : "vanilla_baseline");
            root.addProperty("seed", world.getSeed());
            root.addProperty("chunkMin", minChunk);
            root.addProperty("chunkMax", maxChunk);
            root.addProperty("chunkCount", GRID_CHUNKS * GRID_CHUNKS);
            root.addProperty("generationHaloChunks", GENERATION_HALO_CHUNKS);
            root.addProperty("airProximityRadius", AIR_PROXIMITY_RADIUS);
            root.addProperty("verticalBandHeight", Y_BAND_HEIGHT);
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
