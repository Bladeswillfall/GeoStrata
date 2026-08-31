package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ContactMetamorphismResolverTest {
    @Test
    void backgroundContactProductRetainsParentProvenance() {
        ProvinceBackgroundRuntime.ResolvedColumn resolvedColumn = new ProvinceBackgroundRuntime.ResolvedColumn(
                GeologyProvince.VOLCANIC_ARC,
                y -> "hornfels",
                y -> new ProvinceBackgroundRuntime.ResolvedSample(
                        "hornfels",
                        "contact_aureole",
                        "schist"
                )
        );
        ProvinceBackgroundRuntime.Column column = new ProvinceBackgroundRuntime.Column(resolvedColumn, null, null);
        ProvinceBackgroundRuntime.Column[] columns = new ProvinceBackgroundRuntime.Column[256];
        Arrays.fill(columns, column);
        ProvinceBackgroundRuntime.Chunk background = new ProvinceBackgroundRuntime.Chunk(992, -512, columns);

        GeologyResolver.Result resolved = GeologyResolver.resolve(1000, -20, -500, background);

        assertEquals("hornfels", resolved.lithology());
        assertEquals(Optional.of("schist"), resolved.parentLithology());
        assertEquals(Optional.of("contact_aureole"), resolved.bodyStyle());
    }
}
