package com.geostrata;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared GeoStrata identity and logging helpers. Loader entrypoints live under platform packages. */
public final class GeoStrata {
    public static final String MOD_ID = "geostrata";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private GeoStrata() {
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
