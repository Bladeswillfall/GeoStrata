package com.geostrata.command;

import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyResolver;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeologyResolveCommandsTest {
    @Test
    void descriptionIncludesSemanticAndActualGeology() {
        GeologyResolver.Result result = new GeologyResolver.Result(
                "basalt",
                Optional.empty(),
                GeologyProvince.VOLCANIC_ARC,
                GeologyResolver.Source.PROVINCE_BACKGROUND,
                Optional.of("dike")
        );

        assertEquals(
                "GeoStrata resolve: basalt | province Volcanic Arc | body dike | parent n/a"
                        + " | authority province_background | actual minecraft:basalt | at 10,-5,20",
                GeologyResolveCommands.description(result, "minecraft:basalt", 10, -5, 20)
        );
    }

    @Test
    void unresolvedDescriptionDoesNotGuessGeology() {
        assertEquals(
                "GeoStrata resolve: no active semantic geology | actual minecraft:stone | at 0,64,0"
                        + " | advanced runtime may be disabled or unavailable",
                GeologyResolveCommands.unresolvedDescription("minecraft:stone", 0, 64, 0)
        );
    }
}
