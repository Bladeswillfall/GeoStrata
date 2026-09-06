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
                Map.entry("gold", "geostrata_experimental_with_vanilla_fallback"),
                Map.entry("emerald", "geostrata_experimental_with_vanilla_fallback"),
                Map.entry("diamond", "geostrata_experimental_with_vanilla_fallback"),
                Map.entry("redstone", "minecraft_owned"),
                Map.entry("lapis", "minecraft_owned"),
                Map.entry("nether_gold", "dimension_out_of_scope"),
                Map.entry("nether_quartz", "dimension_out_of_scope"),
                Map.entry("ancient_debris", "dimension_out_of_scope")
        ), ownership);

        Set<String> configured = ids(read("geology/ore_occurrences.json").getAsJsonArray("occurrences"));
        assertTrue(configured.containsAll(Set.of("coal", "iron", "copper", "gold", "emerald")));
        assertEquals("experimental_opt_in", read("geology/diamond_geology_experiment.json")
                .get("runtimeStatus").getAsString());
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
    }

    @Test
    void naturalVanillaGeologyHasAnExplicitHandlingGroup() throws IOException {
        JsonArray groups = read("geology/vanilla_material_coverage.json").getAsJsonArray("naturalBlockGroups");
        Set<String> covered = new HashSet<>();
        groups.forEach(raw -> covered.addAll(strings(raw.getAsJsonObject().getAsJsonArray("blocks"))));

        assertTrue(covered.containsAll(Set.of(
                "minecraft:stone", "minecraft:deepslate", "minecraft:tuff",
                "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                "minecraft:sandstone", "minecraft:red_sandstone",
                "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
                "minecraft:clay", "minecraft:mud", "minecraft:terracotta",
                "minecraft:calcite", "minecraft:dripstone_block", "minecraft:pointed_dripstone",
                "minecraft:smooth_basalt", "minecraft:amethyst_block", "minecraft:budding_amethyst",
                "minecraft:bedrock", "minecraft:obsidian",
                "minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone",
                "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:magma_block",
                "minecraft:end_stone"
        )));
    }

    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(path))).getAsJsonObject();
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
