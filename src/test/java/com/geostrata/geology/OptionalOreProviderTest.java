package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptionalOreProviderTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void availableProvidersAddBundledOccurrencesAndActivation() throws IOException {
        GeologyDataReload.State state = parse(output -> true);

        OreOccurrenceCatalog.Occurrence zinc = state.oreOccurrences().require("zinc");
        assertEquals("create", zinc.providerMod());
        assertEquals("create:raw_zinc", zinc.outputItem());
        assertEquals(List.of("shale", "siltstone"), zinc.hostLithologies());
        assertEquals(List.of("stratiform"), zinc.depositStyles());
        assertEquals(0.5, state.oreExperiment().activationChance("zinc"), 1.0e-12);

        OreOccurrenceCatalog.Occurrence tin = state.oreOccurrences().require("tin");
        assertEquals("create_dreams_and_desires", tin.providerMod());
        assertEquals("create_dd:raw_tin", tin.outputItem());
        assertEquals(List.of("granite", "gneiss"), tin.hostLithologies());
        assertEquals(List.of("vein", "disseminated", "massive_lens_or_pocket"), tin.depositStyles());
        assertEquals(0.28, state.oreExperiment().activationChance("tin"), 1.0e-12);
        assertEquals(
                List.of("pegmatite_fertile_margin"),
                tin.formationRoutes().stream()
                        .filter(route -> "pegmatite_greisen".equals(route.id()))
                        .findFirst()
                        .orElseThrow()
                        .bodyStyles()
        );

        OreOccurrenceCatalog.Occurrence thorium = state.oreOccurrences().require("thorium");
        assertEquals("create_new_age", thorium.providerMod());
        assertEquals("create_new_age:thorium", thorium.outputItem());
        assertEquals(List.of("granite", "rhyolite", "gneiss"), thorium.hostLithologies());
        assertEquals(List.of("disseminated", "vein", "massive_lens_or_pocket"), thorium.depositStyles());
        assertEquals(0.2, state.oreExperiment().activationChance("thorium"), 1.0e-12);
        assertEquals(
                List.of("pegmatite_fertile_margin"),
                thorium.formationRoutes().stream()
                        .filter(route -> "pegmatite_accessory".equals(route.id()))
                        .findFirst()
                        .orElseThrow()
                        .bodyStyles()
        );
    }

    @Test
    void missingOptionalProviderOutputRemovesOccurrenceAndActivation() throws IOException {
        GeologyDataReload.State state = parse(output -> !"create:raw_zinc".equals(output));

        assertFalse(state.oreOccurrences().byId().containsKey("zinc"));
        assertFalse(state.oreExperiment().activationChancePerCandidate().containsKey("zinc"));
        assertTrue(state.oreOccurrences().byId().containsKey("coal"));
    }

    @Test
    void missingTinProviderOutputRemovesOnlyTin() throws IOException {
        GeologyDataReload.State state = parse(output -> !"create_dd:raw_tin".equals(output));

        assertFalse(state.oreOccurrences().byId().containsKey("tin"));
        assertFalse(state.oreExperiment().activationChancePerCandidate().containsKey("tin"));
        assertTrue(state.oreOccurrences().byId().containsKey("zinc"));
    }

    @Test
    void missingThoriumProviderOutputRemovesOnlyThorium() throws IOException {
        GeologyDataReload.State state = parse(output -> !"create_new_age:thorium".equals(output));

        assertFalse(state.oreOccurrences().byId().containsKey("thorium"));
        assertFalse(state.oreExperiment().activationChancePerCandidate().containsKey("thorium"));
        assertTrue(state.oreOccurrences().byId().containsKey("tin"));
    }

    @Test
    void missingMinecraftOutputRemainsFatal() throws IOException {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parse(output -> !"minecraft:coal".equals(output))
        );
        assertTrue(exception.getMessage().contains("minecraft:coal"));
    }

    private static GeologyDataReload.State parse(Predicate<String> outputAvailable) throws IOException {
        return GeologyDataReload.parseIncludingExternal(
                read("lithologies.json"),
                read("ore_occurrences.json"),
                read("external_ore_occurrences.json"),
                read("ore_deposit_experiment.json"),
                read("province_profiles.json"),
                read("sedimentary_successions.json"),
                read("sedimentary_field_profiles.json"),
                read("correlated_sedimentary_experiment.json"),
                outputAvailable
        );
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(GEOLOGY.resolve(name))).getAsJsonObject();
    }
}
