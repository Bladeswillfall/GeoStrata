package com.geostrata.geology;

/** Broad geological province archetypes used as stable regional context. */
public enum GeologyProvince {
    SEDIMENTARY_BASIN("sedimentary_basin", "Sedimentary Basin", "bedded carbonate and clastic cover"),
    CRATONIC_SHIELD("cratonic_shield", "Cratonic Shield", "old metamorphic basement with limited younger cover"),
    OROGENIC_BELT("orogenic_belt", "Orogenic Belt", "deformed and metamorphosed mountain-belt geology"),
    VOLCANIC_ARC("volcanic_arc", "Volcanic Arc", "extrusive volcanic rocks above active-style basement"),
    RIFT_PROVINCE("rift_province", "Rift Province", "faulted basin fill with mafic volcanic influence");

    private final String id;
    private final String displayName;
    private final String summary;

    GeologyProvince(String id, String displayName, String summary) {
        this.id = id;
        this.displayName = displayName;
        this.summary = summary;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }
}
