package com.geostrata.geology;

import com.geostrata.GeoStrata;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;

/** Loads and publishes the geology resource graph in dependency order. */
public final class GeologyDataReload implements SimpleSynchronousResourceReloadListener {
    static final String COMPANION_MOD_ID = "geostrata_correlated_experiment";

    private static final GeologyDataReload INSTANCE = new GeologyDataReload();
    private static final Identifier RELOAD_ID = GeoStrata.id("geology_data");
    private static final Identifier LITHOLOGIES = GeoStrata.id("geology/lithologies.json");
    private static final Identifier PROVINCES = GeoStrata.id("geology/province_profiles.json");
    private static final Identifier SUCCESSIONS = GeoStrata.id("geology/sedimentary_successions.json");
    private static final Identifier FIELD_PROFILES = GeoStrata.id("geology/sedimentary_field_profiles.json");
    private static final Identifier EXPERIMENT = GeoStrata.id("geology/correlated_sedimentary_experiment.json");

    private GeologyDataReload() {
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
        try {
            State loaded = parse(
                    readObject(manager, LITHOLOGIES),
                    readObject(manager, PROVINCES),
                    readObject(manager, SUCCESSIONS),
                    readObject(manager, FIELD_PROFILES),
                    readObject(manager, EXPERIMENT),
                    FabricLoader.getInstance().isModLoaded(COMPANION_MOD_ID)
            );
            loaded.publish();
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata geology data: {} lithologies, {} successions, experiment enabled={}",
                    loaded.lithologies().entries().size(),
                    loaded.successions().successions().size(),
                    loaded.experiment().enabled()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata geology data", exception);
        }
    }

    static State parse(
            JsonObject lithologiesRoot,
            JsonObject provincesRoot,
            JsonObject successionsRoot,
            JsonObject fieldProfilesRoot,
            JsonObject experimentRoot,
            boolean companionLoaded
    ) {
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.parse(lithologiesRoot);
        GeologyProvinceProfiles.Snapshot provinces = GeologyProvinceProfiles.parse(lithologies, provincesRoot);
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.parse(lithologies, successionsRoot);
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.parse(
                successions,
                fieldProfilesRoot
        );
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.parse(
                experimentRoot,
                successions,
                lithologies,
                provinces
        ).activated(companionLoaded);
        return new State(lithologies, provinces, successions, fieldProfiles, experiment);
    }

    private static JsonObject readObject(ResourceManager manager, Identifier id) throws IOException {
        Resource resource = manager.getResource(id)
                .orElseThrow(() -> new IOException("missing server-data resource " + id));
        try (BufferedReader reader = resource.getReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException(id + " root must be a JSON object");
            }
            return root.getAsJsonObject();
        }
    }

    record State(
            LithologyCatalog.Snapshot lithologies,
            GeologyProvinceProfiles.Snapshot provinces,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles,
            CorrelatedSedimentaryExperiment.Snapshot experiment
    ) {
        private void publish() {
            LithologyCatalog.install(lithologies);
            GeologyProvinceProfiles.install(provinces);
            SedimentarySuccessions.install(successions);
            SedimentaryFieldProfiles.install(fieldProfiles);
            CorrelatedSedimentaryExperiment.install(experiment);
        }
    }
}

