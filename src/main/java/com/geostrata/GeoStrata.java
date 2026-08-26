package com.geostrata;

import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.command.GeoStrataCommands;
import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.SedimentarySuccessions;
import com.geostrata.item.GeoStrataItemGroups;
import com.geostrata.worldgen.GeoStrataWorldgen;
import com.geostrata.worldgen.feature.GeoStrataFeatures;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeoStrata implements ModInitializer {
    public static final String MOD_ID = "geostrata";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        GeoStrataBlocks.register();
        GeoStrataItemGroups.register();
        GeoStrataFeatures.register();
        GeologyProvinceProfiles.register();
        SedimentarySuccessions.register();
        GeoStrataWorldgen.register();
        GeoStrataCommands.register();
        LOGGER.info(
                "GeoStrata initialized {} runtime blocks with data-driven overworld geology",
                GeoStrataBlocks.count()
        );
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
