package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeologyResourceContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path GEOLOGY = RESOURCES.resolve("data/geostrata/geology");
    private static final Path CONFIGURED = RESOURCES.resolve("data/geostrata/worldgen/configured_feature");
    private static final Path PLACED = RESOURCES.resolve("data/geostrata/worldgen/placed_feature");

    @Test
    void bundledResourcesFormAValidRuntimeGraph() throws IOException {
        GeologyDataReload.State core = parseGeology(false);
        assertTrue(core.lithologies().loaded());
        assertTrue(core.oreOccurrences().loaded());
        assertTrue(core.oreExperiment().loaded());
        assertTrue(core.provinces().loaded());
        assertTrue(core.successions().loaded());
        assertTrue(core.fieldProfiles().loaded());
        assertTrue(core.experiment().enabled());
        assertEquals("core_runtime", core.experiment().runtimeStatus());
        assertTrue(core.experiment().verticalWindow().isFullDimension());
        assertFalse(core.oreExperiment().enabled());
        assertEquals("experimental_opt_in", core.oreExperiment().runtimeStatus());
        assertEquals("chunk_local_valid_host_clipping", core.oreExperiment().placementMode());
        assertEquals("not_implemented", core.oreExperiment().nativeGenerationSuppression());
        assertEquals(
                Set.of("coal", "iron", "copper", "gold", "emerald"),
                core.oreExperiment().activationChancePerCandidate().keySet()
        );
        assertEquals(0.8, core.oreExperiment().activationChance("coal"), 1.0e-12);
        assertEquals(0.5, core.oreExperiment().activationChance("iron"), 1.0e-12);
        assertEquals(0.36, core.oreExperiment().activationChance("copper"), 1.0e-12);
        assertEquals(0.8, core.oreExperiment().activationChance("gold"), 1.0e-12);
        assertEquals(0.08, core.oreExperiment().activationChance("emerald"), 1.0e-12);
        assertEquals(
                Set.of("coal", "iron", "copper", "gold", "emerald"),
                core.oreOccurrences().byId().keySet()
        );
        assertEquals("grade_economy_active", core.oreOccurrences().runtimeStatus());
        assertEquals(
                List.of("poor", "medium", "rich", "massive"),
                core.oreOccurrences().gradeModel().economicGrades()
        );
        assertEquals(List.of(1, 2, 4, 8), core.oreOccurrences().gradeModel().economies().stream()
                .map(OreOccurrenceCatalog.GradeEconomy::baseYield)
                .toList());
        assertEquals("loot_tables_active", core.oreOccurrences().gradeModel().yieldStatus());
        assertEquals("block_runtime_active", core.oreOccurrences().gradeModel().experienceStatus());
        assertTrue(core.oreOccurrences().occurrences().stream()
                .allMatch(occurrence -> occurrence.gradeBlocks().size() == 4));
        assertFalse(core.oreOccurrences().gradeModel().traceEconomic());
        assertEquals("not_implemented", core.oreOccurrences().nativeGenerationSuppression());

        GeologyDataReload.State activated = parseGeology(true);
        assertTrue(activated.experiment().enabled());
        assertEquals("core_runtime", activated.experiment().runtimeStatus());
        assertTrue(activated.experiment().verticalWindow().isFullDimension());
        assertFalse(activated.oreExperiment().enabled());

        assertCharacteristicProvincePalettes(core);
        assertOrdinaryProvinceMatrixCandidates(core);
        assertSuccessionContextCoverage(core.successions());
        assertExperimentTagsExist(core.experiment());
        assertStrataLensResourcesArePaired();
        assertCorrelatedWorldgenStaging();
        assertOreDepositWorldgenStaging();
        assertCompanionMetadata();
    }

    @Test
    void companionRejectsCatalogMissingPhyllite() throws IOException {
        JsonObject lithologies = read(GEOLOGY.resolve("lithologies.json"));
        JsonArray entries = lithologies.getAsJsonArray("lithologies");
        for (int index = 0; index < entries.size(); index++) {
            if ("phyllite".equals(string(entries.get(index).getAsJsonObject(), "id"))) {
                entries.remove(index);
                break;
            }
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GeologyDataReload.parse(
                        lithologies,
                        read(GEOLOGY.resolve("ore_occurrences.json")),
                        read(GEOLOGY.resolve("ore_deposit_experiment.json")),
                        read(GEOLOGY.resolve("province_profiles.json")),
                        read(GEOLOGY.resolve("sedimentary_successions.json")),
                        read(GEOLOGY.resolve("sedimentary_field_profiles.json")),
                        read(GEOLOGY.resolve("correlated_sedimentary_experiment.json")),
                        true
                )
        );
        assertTrue(exception.getMessage().contains("phyllite"));
    }

    private static GeologyDataReload.State parseGeology(boolean companionLoaded) throws IOException {
        return GeologyDataReload.parse(
                read(GEOLOGY.resolve("lithologies.json")),
                read(GEOLOGY.resolve("ore_occurrences.json")),
                read(GEOLOGY.resolve("ore_deposit_experiment.json")),
                read(GEOLOGY.resolve("province_profiles.json")),
                read(GEOLOGY.resolve("sedimentary_successions.json")),
                read(GEOLOGY.resolve("sedimentary_field_profiles.json")),
                read(GEOLOGY.resolve("correlated_sedimentary_experiment.json")),
                companionLoaded
        );
    }

    private static void assertCharacteristicProvincePalettes(GeologyDataReload.State data) {
        for (var weights : data.provinces().weights().values()) {
            assertTrue(weights.values().stream().filter(weight -> weight >= 0.65).count() >= 3);
        }
        for (LithologyCatalog.Entry lithology : data.lithologies().entries()) {
            assertTrue(data.provinces().weights().values().stream()
                    .mapToDouble(weights -> weights.get(lithology.id()))
                    .max()
                    .orElseThrow() >= 0.65, lithology.id());
        }
    }

    private static void assertOrdinaryProvinceMatrixCandidates(GeologyDataReload.State data) {
        List<LithologyCatalog.Entry> ordinary = data.lithologies().entries().stream()
                .filter(entry -> entry.baselineFeature() != null && entry.baselineFeature().endsWith("_ore"))
                .toList();
        assertTrue(ordinary.size() >= 4);
        assertFalse(ordinary.stream().anyMatch(entry -> entry.id().equals("kimberlite") || entry.id().equals("lamproite")));
        for (GeologyProvince province : GeologyProvince.values()) {
            assertEquals(4, ordinary.stream()
                    .sorted(java.util.Comparator
                            .<LithologyCatalog.Entry>comparingDouble(entry -> data.provinces().weight(province, entry.id()))
                            .reversed()
                            .thenComparing(LithologyCatalog.Entry::id))
                    .limit(4)
                    .count());
        }
    }

    private static void assertSuccessionContextCoverage(SedimentarySuccessions.Snapshot successions) {
        Set<GeologyProvince> contexts = new HashSet<>();
        for (SedimentarySuccessions.Succession succession : successions.successions()) {
            contexts.addAll(succession.contexts());
        }
        assertTrue(contexts.contains(GeologyProvince.SEDIMENTARY_BASIN));
        assertTrue(contexts.contains(GeologyProvince.RIFT_PROVINCE));
    }

    private static void assertExperimentTagsExist(CorrelatedSedimentaryExperiment.Snapshot experiment) {
        assertTrue(tagPath("tags/worldgen/biome", experiment.registrationBiomeTag()).toFile().isFile());
        assertTrue(tagPath("tags/blocks", experiment.hostBlockTag()).toFile().isFile());
        assertTrue(RESOURCES.resolve(
                "data/geostrata/tags/worldgen/biome/has_experimental_ore_deposits.json"
        ).toFile().isFile());
    }

    private static Path tagPath(String directory, String identifier) {
        assertTrue(identifier.startsWith("geostrata:"));
        return RESOURCES.resolve("data/geostrata")
                .resolve(directory)
                .resolve(identifier.substring(identifier.indexOf(':') + 1) + ".json");
    }

    private static void assertStrataLensResourcesArePaired() throws IOException {
        int lenses = 0;
        try (var files = Files.list(CONFIGURED)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).sorted().toList()) {
                JsonObject configured = read(path);
                if (!"geostrata:strata_lens".equals(string(configured, "type"))) {
                    continue;
                }
                lenses++;
                assertStrataLensShape(configured.getAsJsonObject("config"), path);
                assertPlacement(path);
            }
        }
        assertTrue(lenses > 0);
    }

    private static void assertStrataLensShape(JsonObject config, Path path) {
        assertTrue(config != null, path.toString());
        assertTrue(config.has("targets") && config.getAsJsonArray("targets").size() > 0, path.toString());
        for (String field : List.of(
                "discard_chance_on_air_exposure", "long_radius", "short_radius_ratio",
                "short_radius_variation", "half_thickness", "edge_half_thickness", "max_slope",
                "warp_amplitude", "warp_variation", "warp_wavelength"
        )) {
            assertTrue(config.has(field), path + " missing " + field);
        }
        assertFalse(config.has("size"), path.toString());
    }

    private static void assertPlacement(Path configuredPath) throws IOException {
        Path path = PLACED.resolve(configuredPath.getFileName());
        assertTrue(path.toFile().isFile(), path.toString());
        JsonObject placed = read(path);
        String name = configuredPath.getFileName().toString().replaceFirst("\\.json$", "");
        assertEquals("geostrata:" + name, string(placed, "feature"));

        JsonArray modifiers = placed.getAsJsonArray("placement");
        assertTrue(modifiers != null && !modifiers.isEmpty(), path.toString());
        List<String> types = modifiers.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(modifier -> string(modifier, "type"))
                .toList();
        for (String required : List.of(
                "minecraft:count", "minecraft:in_square", "geostrata:subsurface_anchor", "minecraft:biome"
        )) {
            assertEquals(1, types.stream().filter(required::equals).count(), path.toString());
        }
        assertFalse(types.contains("minecraft:height_range"), path + " must use generated terrain height, not a fixed Y range");

        JsonObject count = modifiers.get(types.indexOf("minecraft:count")).getAsJsonObject();
        assertTrue(count.get("count").getAsInt() >= 1 && count.get("count").getAsInt() <= 8);
    }

    private static void assertCorrelatedWorldgenStaging() throws IOException {
        JsonObject configured = read(CONFIGURED.resolve("correlated_sedimentary_experiment.json"));
        assertEquals("geostrata:correlated_sedimentary", string(configured, "type"));
        assertEquals(0, configured.getAsJsonObject("config").size());

        JsonObject placed = read(PLACED.resolve("correlated_sedimentary_experiment.json"));
        assertEquals("geostrata:correlated_sedimentary_experiment", string(placed, "feature"));
        assertTrue(placed.getAsJsonArray("placement").isEmpty());

        JsonObject backgroundConfigured = read(CONFIGURED.resolve("province_background_experiment.json"));
        assertEquals("geostrata:province_background", string(backgroundConfigured, "type"));
        assertEquals(0, backgroundConfigured.getAsJsonObject("config").size());

        JsonObject backgroundPlaced = read(PLACED.resolve("province_background_experiment.json"));
        assertEquals("geostrata:province_background_experiment", string(backgroundPlaced, "feature"));
        assertTrue(backgroundPlaced.getAsJsonArray("placement").isEmpty());
    }

    private static void assertOreDepositWorldgenStaging() throws IOException {
        JsonObject configured = read(CONFIGURED.resolve("ore_deposit_experiment.json"));
        assertEquals("geostrata:ore_deposit", string(configured, "type"));
        assertEquals(0, configured.getAsJsonObject("config").size());

        JsonObject placed = read(PLACED.resolve("ore_deposit_experiment.json"));
        assertEquals("geostrata:ore_deposit_experiment", string(placed, "feature"));
        assertTrue(placed.getAsJsonArray("placement").isEmpty());
    }

    private static void assertCompanionMetadata() throws IOException {
        JsonObject metadata = read(Path.of("experiment-companion/src/main/resources/fabric.mod.json"));
        assertEquals(GeologyDataReload.COMPANION_MOD_ID, string(metadata, "id"));
        assertTrue(string(metadata.getAsJsonObject("depends"), "geostrata").startsWith(">="));
        assertEquals(
                List.of("com.geostrata.experiment.CorrelatedExperimentCompanion"),
                metadata.getAsJsonObject("entrypoints").getAsJsonArray("main").asList().stream()
                        .map(JsonElement::getAsString)
                        .toList()
        );
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null ? null : value.getAsString();
    }
}
