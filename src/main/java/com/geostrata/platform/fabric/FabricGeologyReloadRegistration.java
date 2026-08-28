package com.geostrata.platform.fabric;

import com.geostrata.GeoStrata;
import com.geostrata.geology.GeologyDataReload;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/** Bridges Fabric's server-data reload event into the shared geology loader. */
public final class FabricGeologyReloadRegistration implements SimpleSynchronousResourceReloadListener {
    private static final String COMPANION_MOD_ID = "geostrata_correlated_experiment";
    private static final FabricGeologyReloadRegistration INSTANCE = new FabricGeologyReloadRegistration();
    private static final Identifier RELOAD_ID = GeoStrata.id("geology_data");

    private FabricGeologyReloadRegistration() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(INSTANCE);
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        GeologyDataReload.reload(manager, FabricLoader.getInstance().isModLoaded(COMPANION_MOD_ID));
    }
}
