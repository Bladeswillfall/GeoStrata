package com.geostrata.block;

import java.util.Arrays;
import net.minecraft.util.StringIdentifiable;

/** Stable rock identities stored on graded ore block states for host-aware rendering. */
public enum OreHost implements StringIdentifiable {
    LIMESTONE("limestone"),
    CHALK("chalk"),
    SHALE("shale"),
    SLATE("slate"),
    MUDSTONE("mudstone"),
    SILTSTONE("siltstone"),
    MARBLE("marble"),
    QUARTZITE("quartzite"),
    SCHIST("schist"),
    GNEISS("gneiss"),
    BASALT("basalt"),
    RHYOLITE("rhyolite"),
    CONGLOMERATE("conglomerate"),
    BRECCIA("breccia");

    private final String id;

    OreHost(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }

    public static OreHost byId(String id) {
        return Arrays.stream(values())
                .filter(host -> host.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown ore host: " + id));
    }

    public static OreHost defaultFor(String material) {
        return switch (material) {
            case "coal", "iron", "copper" -> SHALE;
            case "gold" -> SLATE;
            default -> throw new IllegalArgumentException("unknown ore material: " + material);
        };
    }
}
