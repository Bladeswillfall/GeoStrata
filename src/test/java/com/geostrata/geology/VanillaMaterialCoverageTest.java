package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaMaterialCoverageTest {
    private static final Path RESOURCES = Path.of("src/main/resources/data/geostrata");

    @Test
    void everyVanillaOreResourceHasAnExplicitOwner() throws IOException {
        JsonArray resources = read("geology/vanilla_material_coverage.json").getAsJsonArray("oreResources");
        Map<String, String> ownership = new HashMap<>();
        resources.forEach(raw -> {
            JsonObject resource = raw.getAsJsonObject();
            ownership.put(resource.get("id").getAsString(), resource.get("status").getAsString());
        });

        assertEquals(Map.ofEntries(
                Map.entry("coal", "geostrata_core"),
                Map.entry("iron", "geostrata_core"),
                Map.entry("copper", "geostrata_core"),
                Map.entry("gold", "geostrata_core"),
                Map.entry("emerald", "geostrata_core"),
                Map.entry("diamond", "geostrata_core"),
                Map.entry("redstone", "minecraft_owned"),
                Map.entry("lapis", "minecraft_owned"),
                Map.entry("nether_gold", "dimension_out_of_scope"),
                Map.entry("nether_quartz", "dimension_out_of_scope"),
                Map.entry("ancient_debris", "dimension_out_of_scope")
        ), ownership);

        Set<String> configured = ids(read("geology/ore_occurrences.json").getAsJsonArray("occurrences"));
        assertTrue(configured.containsAll(Set.of("coal", "iron", "copper", "gold", "emerald")));
        JsonObject diamond = read("geology/diamond_geology_experiment.json");
        assertEquals("core_runtime", diamond.get("runtimeStatus").getAsString());
        assertTrue(diamond.get("enabled").getAsBoolean());
        assertEquals("core_overworld", diamond.get("nativeGenerationSuppression").getAsString());
    }

    @Test
    void residualVanillaCommonOreStatesAreRemovedByExistingGeologyPasses() throws IOException {
        Set<String> cleanup = strings(read("tags/blocks/worldgen/replaced_vanilla_common_ores.json")
                .getAsJsonArray("values"));
        assertEquals(Set.of(
                "minecraft:coal_ore",
                "minecraft:deepslate_coal_ore",
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:raw_iron_block",
                "minecraft:copper_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:raw_copper_block"
        ), cleanup);

        String helper = Files.readString(Path.of(
                "src/main/java/com/geostrata/worldgen/feature/VanillaCommonOreCleanup.java"
        ));
        assertTrue(helper.contains("core_common_overworld"));
        assertTrue(Files.readString(Path.of(
                "src/main/java/com/geostrata/worldgen/feature/CorrelatedSedimentaryFeature.java"
        )).contains("VanillaCommonOreCleanup.replaceable"));
        assertTrue(Files.readString(Path.of(
                "src/main/java/com/geostrata/worldgen/feature/ProvinceBackgroundFeature.java"
        )).contains("VanillaCommonOreCleanup.replaceable"));
    }

    @Test
    void coreVanillaIgneousBlobsAreSuppressed() throws IOException {
        String registration = Files.readString(Path.of(
                "src/main/java/com/geostrata/platform/fabric/FabricWorldgenRegistration.java"
        ));

        assertTrue(registration.contains("REPLACED_VANILLA_IGNEOUS_BLOBS"));
        assertTrue(registration.contains("ORE_GRANITE_UPPER"));
        assertTrue(registration.contains("ORE_GRANITE_LOWER"));
        assertTrue(registration.contains("ORE_DIORITE_UPPER"));
        assertTrue(registration.contains("ORE_DIORITE_LOWER"));
        assertTrue(registration.contains("ORE_ANDESITE_UPPER"));
        assertTrue(registration.contains("ORE_ANDESITE_LOWER"));
        assertTrue(registration.contains("ORE_TUFF"));
    }

    @Test
    void vanillaTuffCalciteAndBasaltAreCoreLithologies() throws IOException {
        JsonArray groups = read("geology/vanilla_material_coverage.json").getAsJsonArray("naturalBlockGroups");
        Set<String> core = groupBlocks(groups, "core_vanilla_lithologies");
        Set<String> preserved = groupBlocks(groups, "preserved_overworld_geology");
        Set<String> netherOnly = groupBlocks(groups, "nether_geology");

        assertTrue(core.containsAll(Set.of("minecraft:tuff", "minecraft:calcite", "minecraft:basalt")));
        assertFalse(preserved.contains("minecraft:tuff"));
        assertFalse(preserved.contains("minecraft:calcite"));
        assertFalse(netherOnly.contains("minecraft:basalt"));

        JsonArray lithologies = read("geology/lithologies.json").getAsJsonArray("lithologies");
        Map<String, String> blocks = new HashMap<>();
        lithologies.forEach(raw -> {
            JsonObject entry = raw.getAsJsonObject();
            blocks.put(entry.get("id").getAsString(), entry.get("block").getAsString());
        });
        assertEquals("minecraft:tuff", blocks.get("tuff"));
        assertEquals("minecraft:calcite", blocks.get("calcite"));
        assertEquals("minecraft:basalt", blocks.get("basalt"));

        String basaltFallback = Files.readString(Path.of(
                "src/main/resources/data/geostrata/worldgen/configured_feature/basalt_ore.json"
        ));
        assertTrue(basaltFallback.contains("minecraft:basalt"));
        assertFalse(basaltFallback.contains("geostrata:basalt"));
    }

    @Test
    void naturalVanillaGeologyHasAnExplicitHandlingGroup() throws IOException {
        JsonArray groups = read("geology/vanilla_material_coverage.json").getAsJsonArray("naturalBlockGroups");
        Set<String> covered = new HashSet<>();
        groups.forEach(raw -> covered.addAll(strings(raw.getAsJsonObject().getAsJsonArray("blocks"))));

        assertTrue(covered.containsAll(Set.of(
                "minecraft:stone", "minecraft:deepslate", "minecraft:tuff",
                "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                "minecraft:basalt", "minecraft:calcite", "minecraft:sandstone", "minecraft:red_sandstone",
                "minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt",
                "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt", "minecraft:moss_block",
                "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mud",
                "minecraft:terracotta", "minecraft:white_terracotta", "minecraft:orange_terracotta",
                "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:red_terracotta",
                "minecraft:light_gray_terracotta",
                "minecraft:dripstone_block", "minecraft:pointed_dripstone",
                "minecraft:smooth_basalt", "minecraft:amethyst_block", "minecraft:budding_amethyst",
                "minecraft:bedrock", "minecraft:obsidian",
                "minecraft:netherrack", "minecraft:blackstone",
                "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:magma_block", "minecraft:glowstone",
                "minecraft:end_stone"
        )));
    }

    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(path))).getAsJsonObject();
    }

    private static Set<String> groupBlocks(JsonArray groups, String id) {
        for (var raw : groups) {
            JsonObject group = raw.getAsJsonObject();
            if (id.equals(group.get("id").getAsString())) {
                return strings(group.getAsJsonArray("blocks"));
            }
        }
        throw new AssertionError("missing vanilla material group " + id);
    }

    private static Set<String> ids(JsonArray values) {
        Set<String> ids = new HashSet<>();
        values.forEach(raw -> ids.add(raw.getAsJsonObject().get("id").getAsString()));
        return ids;
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> strings = new HashSet<>();
        values.forEach(raw -> strings.add(raw.getAsString()));
        return strings;
    }
}
