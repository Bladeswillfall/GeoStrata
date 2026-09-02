package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptionalOreProviderTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void missingOptionalProviderOutputRemovesOccurrenceAndActivation() throws IOException {
        JsonObject ores = read("ore_occurrences.json");
        JsonObject optional = ores.getAsJsonArray("occurrences").get(0).getAsJsonObject().deepCopy();
        optional.addProperty("id", "zinc");
        optional.addProperty("providerMod", "create");
        optional.addProperty("outputItem", "create:raw_zinc");
        JsonObject gradeBlocks = optional.getAsJsonObject("gradeBlocks");
        gradeBlocks.addProperty("poor", "geostrata:poor_zinc_ore");
        gradeBlocks.addProperty("medium", "geostrata:medium_zinc_ore");
        gradeBlocks.addProperty("rich", "geostrata:rich_zinc_ore");
        gradeBlocks.addProperty("massive", "geostrata:massive_zinc_ore");
        ores.getAsJsonArray("occurrences").add(optional);

        GeologyDataReload.State state = parse(ores, output -> !"create:raw_zinc".equals(output));

        assertFalse(state.oreOccurrences().byId().containsKey("zinc"));
        assertFalse(state.oreExperiment().activationChancePerCandidate().containsKey("zinc"));
        assertTrue(state.oreOccurrences().byId().containsKey("coal"));
    }

    @Test
    void missingMinecraftOutputRemainsFatal() throws IOException {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parse(read("ore_occurrences.json"), output -> !"minecraft:coal".equals(output))
        );
        assertTrue(exception.getMessage().contains("minecraft:coal"));
    }

    private static GeologyDataReload.State parse(JsonObject ores, Predicate<String> outputAvailable) throws IOException {
        return GeologyDataReload.parse(
                read("lithologies.json"),
                ores,
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
