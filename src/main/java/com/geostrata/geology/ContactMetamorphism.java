package com.geostrata.geology;

/** Resolves thermal contact products from the semantic parent lithology. */
public final class ContactMetamorphism {
    private ContactMetamorphism() {
    }

    public static String product(String parentLithology, LithologyCatalog.Snapshot catalog) {
        if (parentLithology == null || parentLithology.isBlank() || catalog == null || !catalog.loaded()) {
            throw new IllegalArgumentException("contact metamorphism requires a parent and loaded lithology catalog");
        }

        String genesis = catalog.require(parentLithology).genesis();
        String product = switch (genesis) {
            case "mudrock", "silt_clastic", "low_grade_foliated", "medium_grade_foliated" -> "hornfels";
            case "carbonate", "carbonate_metamorphic" -> "marble";
            case "quartz_rich_metamorphic" -> "quartzite";
            default -> parentLithology;
        };
        catalog.require(product);
        return product;
    }
}
