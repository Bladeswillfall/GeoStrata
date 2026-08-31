package com.geostrata.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PngAssetIntegrityTest {
    private static final Set<String> ECONOMIC_GRADES = Set.of("poor", "medium", "rich", "massive");
    private static final List<String> SOFT_EARTH_MODELS = List.of(
            "clay_loam",
            "sandy_loam",
            "silty_loam",
            "peat_soil",
            "wet_mud",
            "compacted_mud",
            "blue_clay",
            "red_clay"
    );

    @Test
    void bundledPngAssetsAreReadable() throws IOException {
        Path assets = Path.of("src/main/resources/assets/geostrata");
        try (var paths = Files.walk(assets)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".png")).sorted().toList()) {
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, path.toString());
                assertTrue(image.getWidth() > 0 && image.getHeight() > 0, path.toString());
            }
        }
    }

    @Test
    void everyOreHostHasFourDistinctEconomicGradeTextures() throws IOException {
        Path oreTextures = Path.of("src/main/resources/assets/geostrata/textures/block/ore");
        try (var directories = Files.walk(oreTextures, 2)) {
            for (Path hostDirectory : directories.filter(path -> Files.isDirectory(path) && path.getNameCount()
                    == oreTextures.getNameCount() + 2).sorted().toList()) {
                List<Path> textures;
                try (var files = Files.list(hostDirectory)) {
                    textures = files.filter(path -> path.toString().endsWith(".png")).sorted().toList();
                }
                Set<String> grades = textures.stream()
                        .map(path -> path.getFileName().toString().replaceFirst("\\.png$", ""))
                        .collect(java.util.stream.Collectors.toSet());
                assertEquals(ECONOMIC_GRADES, grades, hostDirectory.toString());

                for (int left = 0; left < textures.size(); left++) {
                    for (int right = left + 1; right < textures.size(); right++) {
                        assertTrue(
                                Files.mismatch(textures.get(left), textures.get(right)) >= 0,
                                hostDirectory + " has duplicate grade textures: "
                                        + textures.get(left).getFileName() + " / " + textures.get(right).getFileName()
                        );
                    }
                }
            }
        }
    }

    @Test
    void softEarthModelsDoNotCollapseToOnePlaceholder() throws IOException {
        Path models = Path.of("src/main/resources/assets/geostrata/models/block");
        Set<String> definitions = new java.util.HashSet<>();
        for (String model : SOFT_EARTH_MODELS) {
            String definition = Files.readString(models.resolve(model + ".json"));
            assertFalse(definition.contains("placeholder_earth"), model + " still uses placeholder_earth");
            assertTrue(definitions.add(definition), model + " duplicates another soft-earth model");
        }
    }
}
