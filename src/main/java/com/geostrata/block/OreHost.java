package com.geostrata.block;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
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
    GRANITE("granite"),
    BASALT("basalt"),
    RHYOLITE("rhyolite"),
    GABBRO("gabbro"),
    PERIDOTITE("peridotite"),
    CONGLOMERATE("conglomerate"),
    BRECCIA("breccia");

    private static final Map<String, OreHost> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OreHost::asString, host -> host));

    private final String id;

    OreHost(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }

    public static boolean supports(String id) {
        return BY_ID.containsKey(id);
    }

    public static OreHost byId(String id) {
        OreHost host = BY_ID.get(id);
        if (host == null) {
            throw new IllegalArgumentException("unknown ore host: " + id);
        }
        return host;
    }

    public static OreHost defaultFor(String material) {
        return switch (material) {
            case "coal", "iron", "copper", "zinc" -> SHALE;
            case "tin", "thorium", "uranium", "silver" -> GRANITE;
            case "gold" -> SLATE;
            case "emerald" -> SCHIST;
            default -> throw new IllegalArgumentException("unknown ore material: " + material);
        };
    }
}
