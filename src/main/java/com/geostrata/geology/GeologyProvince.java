package com.geostrata.geology;

/** Broad geological province archetypes used as stable regional context. */
public enum GeologyProvince {
    SEDIMENTARY_BASIN("Sedimentary Basin", "bedded carbonate and clastic cover"),
    CRATONIC_SHIELD("Cratonic Shield", "old metamorphic basement with limited younger cover"),
    OROGENIC_BELT("Orogenic Belt", "deformed and metamorphosed mountain-belt geology"),
    VOLCANIC_ARC("Volcanic Arc", "extrusive volcanic rocks above active-style basement"),
    RIFT_PROVINCE("Rift Province", "faulted basin fill with mafic volcanic influence");

    private final String displayName;
    private final String summary;

    GeologyProvince(String displayName, String summary) {
        this.displayName = displayName;
        this.summary = summary;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }
}
