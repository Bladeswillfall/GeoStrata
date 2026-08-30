package com.geostrata.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PngAssetIntegrityTest {
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
}
