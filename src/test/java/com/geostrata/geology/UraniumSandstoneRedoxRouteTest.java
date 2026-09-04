package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UraniumSandstoneRedoxRouteTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void sandstoneRoutePreservesExistingHydrothermalCandidateRate() throws IOException {
        GeologyDataReload.State state = GeologyDataReload.parseIncludingExternal(
                read("lithologies.json"),
                read("ore_occurrences.json"),
                read("external_ore_occurrences.json"),
                read("ore_deposit_experiment.json"),
                read("province_profiles.json"),
                read("sedimentary_successions.json"),
                read("sedimentary_field_profiles.json"),
                read("correlated_sedimentary_experiment.json"),
                output -> true
        );

        OreOccurrenceCatalog.Occurrence uranium = state.oreOccurrences().require("uranium");
        assertEquals(List.of("granite", "rhyolite", "gneiss", "sandstone"), uranium.hostLithologies());
        assertEquals(List.of("vein", "disseminated", "stratiform"), uranium.depositStyles());
        assertEquals(0.24, state.oreExperiment().activationChance("uranium"), 1.0e-12);

        double totalWeight = uranium.depositStyles().stream()
                .mapToDouble(uranium.generation()::depositStyleWeight)
                .sum();
        double hydrothermalRate = 0.24 * (
                uranium.generation().depositStyleWeight("vein")
                        + uranium.generation().depositStyleWeight("disseminated")
        ) / totalWeight;
        double redoxRate = 0.24 * (
                uranium.generation().depositStyleWeight("stratiform")
                        + uranium.generation().depositStyleWeight("disseminated")
        ) / totalWeight;
        assertEquals(0.16, hydrothermalRate, 1.0e-12);
        assertEquals(0.16, redoxRate, 1.0e-12);

        assertFalse(uranium.requiresBodyStyleContext("disseminated", GeologyProvince.CRATONIC_SHIELD));
        assertEquals(
                List.of("granite", "rhyolite", "gneiss"),
                uranium.hostLithologiesFor("disseminated", GeologyProvince.CRATONIC_SHIELD)
        );
        assertTrue(uranium.requiresBodyStyleContext("stratiform", GeologyProvince.SEDIMENTARY_BASIN));
        assertTrue(uranium.requiresBodyStyleContext("disseminated", GeologyProvince.RIFT_PROVINCE));
        assertEquals(List.of(), uranium.hostLithologiesFor("stratiform", GeologyProvince.SEDIMENTARY_BASIN));
        assertEquals(
                List.of("sandstone"),
                uranium.hostLithologiesFor(
                        "stratiform",
                        GeologyProvince.SEDIMENTARY_BASIN,
                        SandstoneRedoxField.BODY_STYLE
                )
        );
        assertEquals(
                List.of("sandstone"),
                uranium.hostLithologiesFor(
                        "disseminated",
                        GeologyProvince.RIFT_PROVINCE,
                        SandstoneRedoxField.BODY_STYLE
                )
        );

        OreOccurrenceCatalog.FormationRoute redox = uranium.formationRoutes().stream()
                .filter(route -> "sandstone_redox".equals(route.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("sandstone"), redox.hostLithologies());
        assertEquals(List.of(GeologyProvince.SEDIMENTARY_BASIN, GeologyProvince.RIFT_PROVINCE), redox.provinceContexts());
        assertEquals(List.of("stratiform", "disseminated"), redox.depositStyles());
        assertEquals(List.of(SandstoneRedoxField.BODY_STYLE), redox.bodyStyles());
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(GEOLOGY.resolve(name))).getAsJsonObject();
    }
}
