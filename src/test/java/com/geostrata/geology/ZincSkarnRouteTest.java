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

final class ZincSkarnRouteTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void zincSkarnUsesContactAureoleWithoutDilutingSedimentaryRoute() throws IOException {
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

        OreOccurrenceCatalog.Occurrence zinc = state.oreOccurrences().require("zinc");
        assertEquals(List.of("shale", "siltstone", "limestone", "marble"), zinc.hostLithologies());
        assertEquals(
                List.of("stratiform", "massive_lens_or_pocket", "disseminated"),
                zinc.depositStyles()
        );
        assertEquals(0.75, zinc.generation().activationChance(), 1.0e-12);

        double totalWeight = zinc.depositStyles().stream()
                .mapToDouble(zinc.generation()::depositStyleWeight)
                .sum();
        double sedimentaryRate = zinc.generation().activationChance()
                * zinc.generation().depositStyleWeight("stratiform")
                / totalWeight;
        double skarnRate = zinc.generation().activationChance()
                * (zinc.generation().depositStyleWeight("massive_lens_or_pocket")
                + zinc.generation().depositStyleWeight("disseminated"))
                / totalWeight;
        assertEquals(0.5, sedimentaryRate, 1.0e-12);
        assertEquals(0.25, skarnRate, 1.0e-12);

        assertFalse(zinc.requiresBodyStyleContext("stratiform", GeologyProvince.SEDIMENTARY_BASIN));
        assertEquals(
                List.of("shale", "siltstone"),
                zinc.hostLithologiesFor("stratiform", GeologyProvince.SEDIMENTARY_BASIN)
        );
        assertTrue(zinc.requiresBodyStyleContext("disseminated", GeologyProvince.VOLCANIC_ARC));
        assertTrue(zinc.requiresBodyStyleContext("massive_lens_or_pocket", GeologyProvince.VOLCANIC_ARC));
        assertEquals(List.of(), zinc.hostLithologiesFor("disseminated", GeologyProvince.VOLCANIC_ARC));
        assertEquals(
                List.of("limestone", "marble"),
                zinc.hostLithologiesFor("disseminated", GeologyProvince.VOLCANIC_ARC, "contact_aureole")
        );
        assertEquals(
                List.of("limestone", "marble"),
                zinc.hostLithologiesFor(
                        "massive_lens_or_pocket",
                        GeologyProvince.VOLCANIC_ARC,
                        "contact_aureole"
                )
        );
        assertEquals(
                List.of(),
                zinc.hostLithologiesFor("disseminated", GeologyProvince.OROGENIC_BELT, "contact_aureole")
        );

        OreOccurrenceCatalog.FormationRoute skarn = zinc.formationRoutes().stream()
                .filter(route -> "intrusion_contact_skarn".equals(route.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(GeologyProvince.VOLCANIC_ARC), skarn.provinceContexts());
        assertEquals(List.of("contact_aureole"), skarn.bodyStyles());
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(GEOLOGY.resolve(name))).getAsJsonObject();
    }
}
