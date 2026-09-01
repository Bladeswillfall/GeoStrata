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
        assertFalse(core.experiment().enabled());
        assertEquals("metadata_only", core.experiment().runtimeStatus());
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
        assertEquals(0.5, core.oreExperiment().activationChance("gold"), 1.0e-12);
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
        assertEquals("experimental_runtime", activated.experiment().runtimeStatus());
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
            if ("phyllite".equals(entries.get(index).getAsJsonObject().get("id").getAsString())) {
                entries.remove(index);
                break;
            }
        }

        IllegalArgumentException error = assertThrows(
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
        assertTrue(error.getMessage().contains("phyllite"));
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

    private static void assertCharacteristicProvincePalettes(GeologyDataReload.State core) {
        for (GeologyProvince province : GeologyProvince.values()) {
            assertFalse(core.provinces().profile(province).lithologyWeights().isEmpty());
        }
    }

    private static void assertOrdinaryProvinceMatrixCandidates(GeologyDataReload.State core) {
        assertTrue(core.provinces().profile(GeologyProvince.SEDIMENTARY_BASIN).lithologyWeights().containsKey("shale"));
        assertTrue(core.provinces().profile(GeologyProvince.CRATONIC_SHIELD).lithologyWeights().containsKey("gneiss"));
        assertTrue(core.provinces().profile(GeologyProvince.OROGENIC_BELT).lithologyWeights().containsKey("slate"));
        assertTrue(core.provinces().profile(GeologyProvince.VOLCANIC_ARC).lithologyWeights().containsKey("basalt"));
        assertTrue(core.provinces().profile(GeologyProvince.RIFT_PROVINCE).lithologyWeights().containsKey("basalt"));
    }

    private static void assertSuccessionContextCoverage(SedimentarySuccessions.Snapshot successions) {
        Set<String> contexts = new HashSet<>();
        successions.successions().forEach(successionsEntry -> contexts.addAll(successionsEntry.contexts()));
        assertTrue(contexts.containsAll(Set.of("marine_shelf", "fluvial", "coastal", "rift_basin")));
    }

    private static void assertExperimentTagsExist(CorrelatedSedimentaryExperiment.Snapshot experiment) {
        assertTrue(tagPath("tags/worldgen/biome", experiment.registrationBiomeTag()).toFile().isFile());
        assertTrue(tagPath("tags/blocks", experiment.hostBlockTag()).toFile().isFile());
    }

    private static void assertStrataLensResourcesArePaired() throws IOException {
        try (var configured = Files.list(CONFIGURED)) {
            configured.filter(path -> path.getFileName().toString().endsWith("_lens.json"))
                    .forEach(path -> assertTrue(PLACED.resolve(path.getFileName()).toFile().isFile()));
        }
    }

    private static void assertCorrelatedWorldgenStaging() {
        assertTrue(CONFIGURED.resolve("correlated_sedimentary_experiment.json").toFile().isFile());
        assertTrue(PLACED.resolve("correlated_sedimentary_experiment.json").toFile().isFile());
        assertTrue(CONFIGURED.resolve("province_background_experiment.json").toFile().isFile());
        assertTrue(PLACED.resolve("province_background_experiment.json").toFile().isFile());
    }

    private static void assertOreDepositWorldgenStaging() {
        assertTrue(CONFIGURED.resolve("ore_deposit_experiment.json").toFile().isFile());
        assertTrue(PLACED.resolve("ore_deposit_experiment.json").toFile().isFile());
    }

    private static void assertCompanionMetadata() throws IOException {
        JsonObject metadata = read(Path.of("experiment-companion/src/main/resources/fabric.mod.json"));
        assertEquals("geostrata_correlated_experiment", metadata.get("id").getAsString());
    }

    private static Path tagPath(String kind, String rawId) {
        String path = rawId.substring(rawId.indexOf(':') + 1);
        return RESOURCES.resolve("data/geostrata").resolve(kind).resolve(path + ".json");
    }

    private static JsonObject read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IOException(path + " root is not an object");
            }
            return element.getAsJsonObject();
        }
    }
}
