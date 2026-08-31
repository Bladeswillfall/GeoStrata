package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ContactMetamorphismTest {
    @Test
    void peliticAndSiltyParentsBakeToHornfels() {
        LithologyCatalog.Snapshot catalog = catalog();

        assertEquals("hornfels", ContactMetamorphism.product("shale", catalog));
        assertEquals("hornfels", ContactMetamorphism.product("siltstone", catalog));
        assertEquals("hornfels", ContactMetamorphism.product("slate", catalog));
        assertEquals("hornfels", ContactMetamorphism.product("schist", catalog));
    }

    @Test
    void carbonateParentsResolveToMarble() {
        LithologyCatalog.Snapshot catalog = catalog();

        assertEquals("marble", ContactMetamorphism.product("limestone", catalog));
        assertEquals("marble", ContactMetamorphism.product("marble", catalog));
    }

    @Test
    void QuartzRichAndUnsupportedParentsRemainCompositionallyStable() {
        LithologyCatalog.Snapshot catalog = catalog();

        assertEquals("quartzite", ContactMetamorphism.product("quartzite", catalog));
        assertEquals("gneiss", ContactMetamorphism.product("gneiss", catalog));
    }

    @Test
    void unknownParentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContactMetamorphism.product("missing", catalog()));
    }

    private static LithologyCatalog.Snapshot catalog() {
        List<LithologyCatalog.Entry> entries = List.of(
                entry("shale", "sedimentary", "mudrock"),
                entry("siltstone", "sedimentary", "silt_clastic"),
                entry("limestone", "sedimentary", "carbonate"),
                entry("slate", "metamorphic", "low_grade_foliated"),
                entry("schist", "metamorphic", "medium_grade_foliated"),
                entry("gneiss", "metamorphic", "high_grade_banded"),
                entry("marble", "metamorphic", "carbonate_metamorphic"),
                entry("quartzite", "metamorphic", "quartz_rich_metamorphic"),
                entry("hornfels", "metamorphic", "contact_metamorphic")
        );
        return new LithologyCatalog.Snapshot(
                "metadata_only",
                entries,
                entries.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        LithologyCatalog.Entry::id,
                        entry -> entry
                ))
        );
    }

    private static LithologyCatalog.Entry entry(String id, String rockClass, String genesis) {
        return new LithologyCatalog.Entry(
                id,
                "geostrata:" + id,
                rockClass,
                genesis,
                "test",
                "mid",
                "local",
                "geostrata:has_common_rocks",
                id.equals("hornfels") ? null : id + "_ore"
        );
    }
}
