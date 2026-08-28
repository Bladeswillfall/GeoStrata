package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class WorldgenDeterminismContractTest {
    private static final List<String> FORBIDDEN = List.of(
            "Math.random(",
            "System.currentTimeMillis(",
            "System.nanoTime(",
            "ThreadLocalRandom",
            "SecureRandom",
            "UUID.randomUUID("
    );

    @Test
    void geologyAndWorldgenDoNotUseProcessLocalEntropy() throws IOException {
        for (Path root : List.of(
                Path.of("src/main/java/com/geostrata/geology"),
                Path.of("src/main/java/com/geostrata/worldgen")
        )) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(WorldgenDeterminismContractTest::assertDeterministicSource);
            }
        }
    }

    private static void assertDeterministicSource(Path path) {
        final String source;
        try {
            source = Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Could not read " + path, exception);
        }

        for (String forbidden : FORBIDDEN) {
            assertFalse(
                    source.contains(forbidden),
                    () -> path + " uses process-local entropy source " + forbidden
            );
        }
    }
}
