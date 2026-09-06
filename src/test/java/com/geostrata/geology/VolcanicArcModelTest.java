package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VolcanicArcModelTest {
    private static final Set<String> LITHOLOGIES = Set.of(
            "gneiss",
            "schist",
            "quartzite",
            "tuff",
            "basalt",
            "rhyolite",
            "andesite",
            "granite",
            "diorite",
            "gabbro",
            "peridotite",
            "breccia"
    );

    @Test
    void samplingIsDeterministic() {
        VolcanicArcModel.Sample first = VolcanicArcModel.sample(
                123456789L,
                144,
                -18.0,
                -320,
                170,
                -575,
                4.5,
                63.0
        );
        VolcanicArcModel.Sample second = VolcanicArcModel.sample(
                123456789L,
                144,
                -18.0,
                -320,
                170,
                -575,
                4.5,
                63.0
        );

        assertEquals(first, second);
    }

    @Test
    void architectureOnlyReturnsVolcanicArcLithologies() {
        for (int x = -384; x <= 384; x += 32) {
            for (int z = -384; z <= 384; z += 32) {
                for (int y = -48; y <= 96; y += 24) {
                    VolcanicArcModel.Sample sample = VolcanicArcModel.sample(
                            987654321L,
                            x,
                            y,
                            z,
                            64,
                            -128,
                            6.0,
                            63.0
                    );
                    assertTrue(LITHOLOGIES.contains(sample.lithology()), sample.toString());
                }
            }
        }
    }

    @Test
    void volcanicComplexGradesFromRhyoliteThroughAndesiteIntoPlutonAndContactAureole() {
        VolcanicArcModel.Context context = VolcanicArcModel.forSite(987654321L, 64, -128, 63.0);
        VolcanicArcModel.Column center = null;

        for (int x = -256; x <= 256 && center == null; x++) {
            for (int z = -384; z <= 128 && center == null; z++) {
                VolcanicArcModel.Column column = context.column(x, z, 0.0);
                if (column.complexHorizontalRadius() < 0.05 && column.dikeDistance() > 3.25) {
                    center = column;
                }
            }
        }

        assertNotNull(center, "expected a sampled column near the centre of a volcanic complex");
        assertEquals("rhyolite", center.sample(center.complexCenterY()).lithology());
        assertEquals(
                new VolcanicArcModel.Sample("andesite", "andesite_body"),
                center.sample(center.complexCenterY() + center.complexRadiusY() * 0.80),
                "the outer shallow volcanic complex should grade into intermediate andesite"
        );
        assertEquals(
                new VolcanicArcModel.Sample("granite", "plutonic_core"),
                center.sample(center.complexCenterY() - center.complexRadiusY() * 0.40)
        );
        assertEquals(
                new VolcanicArcModel.Sample("granite", "pegmatite_fertile_margin"),
                center.sample(center.complexCenterY() - center.complexRadiusY() * 0.70),
                "the evolved outer granite core should expose the reusable pegmatite formation context"
        );
        assertEquals(
                new VolcanicArcModel.Sample("peridotite", "ultramafic_intrusive"),
                center.sample(center.complexCenterY() - center.complexRadiusY() * 0.85),
                "the deepest central keel should expose an ultramafic ore-formation signal"
        );
        assertEquals(
                "contact_aureole",
                center.sample(center.complexCenterY() - center.complexRadiusY() * 1.02).bodyStyle(),
                "deep country rock just outside the pluton should enter the contact aureole"
        );
        assertNotEquals(
                "pyroclastic_halo",
                center.sample(center.complexCenterY() - center.complexRadiusY() * 1.02).bodyStyle(),
                "deep plutonic roots must not inherit the shallow tuff halo"
        );
    }

    @Test
    void shallowVolcanicComplexesHaveATuffPyroclasticHalo() {
        VolcanicArcModel.Context context = VolcanicArcModel.forSite(987654321L, 64, -128, 63.0);
        VolcanicArcModel.Sample halo = null;

        for (int x = -256; x <= 256 && halo == null; x += 2) {
            for (int z = -384; z <= 128 && halo == null; z += 2) {
                VolcanicArcModel.Column column = context.column(x, z, 0.0);
                for (double offset = 0.0; offset <= 1.3 && halo == null; offset += 0.05) {
                    VolcanicArcModel.Sample sample = column.sample(
                            column.complexCenterY() + column.complexRadiusY() * offset
                    );
                    if ("pyroclastic_halo".equals(sample.bodyStyle())) {
                        halo = sample;
                    }
                }
            }
        }

        assertEquals(new VolcanicArcModel.Sample("tuff", "pyroclastic_halo"), halo);
    }

    @Test
    void lowerPlutonicRootGradesFromGabbroIntoAPeridotiteKeel() {
        VolcanicArcModel.Context context = VolcanicArcModel.forSite(987654321L, 64, -128, 63.0);
        VolcanicArcModel.Column gabbro = null;
        VolcanicArcModel.Column peridotite = null;

        for (int x = -256; x <= 256 && (gabbro == null || peridotite == null); x++) {
            for (int z = -384; z <= 128 && (gabbro == null || peridotite == null); z++) {
                VolcanicArcModel.Column column = context.column(x, z, 0.0);
                VolcanicArcModel.Sample lowerRoot = column.sample(
                        column.complexCenterY() - column.complexRadiusY() * 0.80
                );
                if ("gabbro".equals(lowerRoot.lithology())) {
                    gabbro = column;
                } else if ("peridotite".equals(lowerRoot.lithology())) {
                    peridotite = column;
                }
            }
        }

        assertNotNull(gabbro, "expected gabbro around the lower-root margin");
        assertNotNull(peridotite, "expected a peridotite keel in the lower-root core");
        assertEquals("mafic_intrusive", gabbro.sample(
                gabbro.complexCenterY() - gabbro.complexRadiusY() * 0.80
        ).bodyStyle());
        assertEquals("ultramafic_intrusive", peridotite.sample(
                peridotite.complexCenterY() - peridotite.complexRadiusY() * 0.80
        ).bodyStyle());
    }

    @Test
    void basaltSillsTerminateLaterallyAroundVolcanicComplexes() {
        VolcanicArcModel.Context context = VolcanicArcModel.forSite(987654321L, 64, -128, 63.0);
        VolcanicArcModel.Column inside = null;
        VolcanicArcModel.Column outside = null;

        for (int x = -256; x <= 256 && (inside == null || outside == null); x += 4) {
            for (int z = -384; z <= 128 && (inside == null || outside == null); z += 4) {
                VolcanicArcModel.Column column = context.column(x, z, 0.0);
                VolcanicArcModel.Sample atSill = column.sample(column.sillCenterY());
                if (inside == null && column.sillFootprint() < 0.5 && "sill".equals(atSill.bodyStyle())) {
                    inside = column;
                }
                if (outside == null && column.sillFootprint() > 1.5 && !"sill".equals(atSill.bodyStyle())) {
                    outside = column;
                }
            }
        }

        assertNotNull(inside, "expected a sampled column inside a finite basalt sill");
        assertNotNull(outside, "expected a sampled column outside the sill footprint");
        assertEquals("sill", inside.sample(inside.sillCenterY()).bodyStyle());
        assertNotEquals("sill", outside.sample(outside.sillCenterY()).bodyStyle());
    }
}
