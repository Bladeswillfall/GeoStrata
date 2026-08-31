package com.geostrata.platform.fabric;

import com.geostrata.GeoStrata;
import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.command.GeoStrataCommands;
import com.geostrata.command.GeologyResolveCommands;
import com.geostrata.command.MetamorphismCommands;
import com.geostrata.command.OreDistributionBenchmarkCommands;
import com.geostrata.command.StructuralCommands;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint. Shared geology/content code must not depend on this class. */
public final class FabricGeoStrata implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricContentRegistration.register();
        FabricGeologyReloadRegistration.register();
        FabricWorldgenRegistration.register();
        GeoStrataCommands.register();
        GeologyResolveCommands.register();
        MetamorphismCommands.register();
        StructuralCommands.register();
        OreDistributionBenchmarkCommands.register();
        GeoStrata.LOGGER.info(
                "GeoStrata initialized {} runtime blocks through the Fabric adapter",
                GeoStrataBlocks.count()
        );
    }
}
