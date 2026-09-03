package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderedExternalOreProviderTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void selectsFirstAvailableProviderOnceAtReload() throws IOException {
        GeologyDataReload.State state = parse(
                zincWithProviders(
                        provider("preferred", "preferred:raw_zinc"),
                        provider("fallback", "fallback:raw_zinc")
                ),
                available("preferred:raw_zinc", "fallback:raw_zinc")
        );

        OreOccurrenceCatalog.Occurrence zinc = state.oreOccurrences().require("zinc");
        assertEquals("preferred", zinc.providerMod());
        assertEquals("preferred:raw_zinc", zinc.outputItem());
    }

    @Test
    void fallsBackToNextAvailableProvider() throws IOException {
        GeologyDataReload.State state = parse(
                zincWithProviders(
                        provider("preferred", "preferred:raw_zinc"),
                        provider("fallback", "fallback:raw_zinc")
                ),
                available("fallback:raw_zinc")
        );

        OreOccurrenceCatalog.Occurrence zinc = state.oreOccurrences().require("zinc");
        assertEquals("fallback", zinc.providerMod());
        assertEquals("fallback:raw_zinc", zinc.outputItem());
    }

    @Test
    void omitsOccurrenceWhenNoProviderIsAvailable() throws IOException {
        GeologyDataReload.State state = parse(
                zincWithProviders(
                        provider("preferred", "preferred:raw_zinc"),
                        provider("fallback", "fallback:raw_zinc")
                ),
                available()
        );

        assertFalse(state.oreOccurrences().byId().containsKey("zinc"));
        assertFalse(state.oreExperiment().activationChancePerCandidate().containsKey("zinc"));
        assertTrue(state.oreOccurrences().byId().containsKey("coal"));
    }

    @Test
    void validatesOccurrenceBeforeOmittingUnavailableProviders() throws IOException {
        JsonObject external = zincWithProviders(provider("preferred", "preferred:raw_zinc"));
        zinc(external).remove("hostLithologies");

        assertThrows(IllegalArgumentException.class, () -> parse(external, available()));
    }

    @Test
    void rejectsMixedLegacyAndOrderedProviderDeclarations() throws IOException {
        JsonObject external = zincWithProviders(provider("preferred", "preferred:raw_zinc"));
        zinc(external).addProperty("providerMod", "legacy");

        assertThrows(IllegalArgumentException.class, () -> parse(external, available("preferred:raw_zinc")));
    }

    @Test
    void validatesEveryProviderCandidateEvenAfterSelectingOne() throws IOException {
        JsonObject malformed = new JsonObject();
        malformed.addProperty("providerMod", "broken");

        JsonObject external = zincWithProviders(
                provider("preferred", "preferred:raw_zinc"),
                malformed
        );

        assertThrows(IllegalArgumentException.class, () -> parse(external, available("preferred:raw_zinc")));
    }

    private static JsonObject zincWithProviders(JsonObject... providers) throws IOException {
        JsonObject external = read("external_ore_occurrences.json");
        JsonObject zinc = zinc(external);
        zinc.remove("providerMod");
        zinc.remove("outputItem");
        JsonArray candidates = new JsonArray();
        for (JsonObject provider : providers) {
            candidates.add(provider);
        }
        zinc.add("providers", candidates);
        return external;
    }

    private static JsonObject provider(String providerMod, String outputItem) {
        JsonObject provider = new JsonObject();
        provider.addProperty("providerMod", providerMod);
        provider.addProperty("outputItem", outputItem);
        return provider;
    }

    private static JsonObject zinc(JsonObject external) {
        for (JsonElement rawOccurrence : external.getAsJsonArray("occurrences")) {
            JsonObject occurrence = rawOccurrence.getAsJsonObject();
            if ("zinc".equals(occurrence.get("id").getAsString())) {
                return occurrence;
            }
        }
        throw new IllegalStateException("bundled external occurrence catalog has no zinc entry");
    }

    private static Predicate<String> available(String... externalOutputs) {
        Set<String> available = Set.of(externalOutputs);
        return output -> output.startsWith("minecraft:") || available.contains(output);
    }

    private static GeologyDataReload.State parse(
            JsonObject external,
            Predicate<String> outputAvailable
    ) throws IOException {
        return GeologyDataReload.parseIncludingExternal(
                read("lithologies.json"),
                read("ore_occurrences.json"),
                external,
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
