package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SculkCompatibilityResourceTest {
    @Test
    void naturalRocksExtendVanillaSculkReplacement() throws Exception {
        JsonObject tag = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/minecraft/tags/blocks/sculk_replaceable.json"
        ))).getAsJsonObject();

        assertFalse(tag.get("replace").getAsBoolean());
        assertTrue(tag.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> "#geostrata:rocks".equals(value.getAsString())));
    }
}
