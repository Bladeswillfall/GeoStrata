package com.geostrata.geology;

import com.geostrata.GeoStrata;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/** Loads and publishes the shared geology resource graph in dependency order. */
public final class GeologyDataReload {
    public static final String COMPANION_MOD_ID = "geostrata_correlated_experiment";

    private static final Identifier LITHOLOGIES = GeoStrata.id("geology/lithologies.json");
    private static final Identifier ORE_OCCURRENCES = GeoStrata.id("geology/ore_occurrences.json");
    private static final Identifier ORE_DEPOSIT_EXPERIMENT = GeoStrata.id("geology/ore_deposit_experiment.json");
    private static final Identifier DIAMOND_GEOLOGY_EXPERIMENT = GeoStrata.id("geology/diamond_geology_experiment.json");
    private static final Identifier PROVINCES = GeoStrata.id("geology/province_profiles.json");
    private static final Identifier SUCCESSIONS = GeoStrata.id("geology/sedimentary_successions.json");
    private static final Identifier FIELD_PROFILES = GeoStrata.id("geology/sedimentary_field_profiles.json");
    private static final Identifier EXPERIMENT = GeoStrata.id("geology/correlated_sedimentary_experiment.json");
    private static final List<String> RUNTIME_ARCHITECTURE_LITHOLOGIES = List.of(
            "gneiss",
            "schist",
            "phyllite",
            "slate",
            "quartzite",
            "marble",
            "hornfels",
            "basalt",
            "rhyolite",
            "granite",
            "diorite",
            "breccia"
    );

    private GeologyDataReload() {
    }

    public static void reload(ResourceManager manager, boolean companionLoaded) {
        try {
            State loaded = parse(
                    readObject(manager, LITHOLOGIES),
                    readObject(manager, ORE_OCCURRENCES),
                    readObject(manager, ORE_DEPOSIT_EXPERIMENT),
                    readObject(manager, PROVINCES),
                    readObject(manager, SUCCESSIONS),
                    readObject(manager, FIELD_PROFILES),
                    readObject(manager, EXPERIMENT),
                    companionLoaded
            );
            OreDepositExperiment.Snapshot oreExperiment = loaded.oreExperiment().activated(companionLoaded);
            DiamondGeologyExperiment.Snapshot diamondExperiment = DiamondGeologyExperiment.parse(
                    readObject(manager, DIAMOND_GEOLOGY_EXPERIMENT)
            ).activated(companionLoaded);
            validateOreRegistries(loaded.oreOccurrences());
            loaded.publish(oreExperiment);
            DiamondGeologyExperiment.install(diamondExperiment);
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata geology data: {} lithologies, {} ore occurrences, ore placement experiment enabled={}, diamond geology experiment enabled={}, {} successions, correlated experiment enabled={}",
                    loaded.lithologies().entries().size(),
                    loaded.oreOccurrences().occurrences().size(),
                    oreExperiment.enabled(),
                    diamondExperiment.enabled(),
                    loaded.successions().successions().size(),
                    loaded.experiment().enabled()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata geology data", exception);
        }
    }

    private static void validateOreRegistries(OreOccurrenceCatalog.Snapshot occurrences) {
        for (OreOccurrenceCatalog.Occurrence occurrence : occurrences.occurrences()) {
            Identifier output = new Identifier(occurrence.outputItem());
            if (!Registries.ITEM.containsId(output)) {
                throw new IllegalArgumentException(occurrence.id() + " references unregistered output item " + output);
            }
            for (String blockId : occurrence.gradeBlocks().values()) {
                Identifier block = new Identifier(blockId);
                if (!Registries.BLOCK.containsId(block)) {
                    throw new IllegalArgumentException(occurrence.id() + " references unregistered grade block " + block);
                }
            }
        }
    }

    static State parse(
            JsonObject lithologiesRoot,
            JsonObject oreOccurrencesRoot,
            JsonObject oreDepositExperimentRoot,
            JsonObject provincesRoot,
            JsonObject successionsRoot,
            JsonObject fieldProfilesRoot,
            JsonObject experimentRoot,
            boolean companionLoaded
    ) {
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.parse(lithologiesRoot);
        validateRuntimeArchitectureLithologies(lithologies);
        OreOccurrenceCatalog.Snapshot oreOccurrences = OreOccurrenceCatalog.parse(
                lithologies,
                oreOccurrencesRoot
        );
        OreDepositExperiment.Snapshot oreExperiment = OreDepositExperiment.parse(
                oreDepositExperimentRoot,
                oreOccurrences
        );
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
        return new State(
                lithologies,
                oreOccurrences,
                oreExperiment,
                provinces,
                successions,
                fieldProfiles,
                experiment
        );
    }

    private static void validateRuntimeArchitectureLithologies(LithologyCatalog.Snapshot lithologies) {
        for (String lithology : RUNTIME_ARCHITECTURE_LITHOLOGIES) {
            try {
                lithologies.require(lithology);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "core province architecture requires lithology " + lithology,
                        exception
                );
            }
        }
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
            OreOccurrenceCatalog.Snapshot oreOccurrences,
            OreDepositExperiment.Snapshot oreExperiment,
            GeologyProvinceProfiles.Snapshot provinces,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles,
            CorrelatedSedimentaryExperiment.Snapshot experiment
    ) {
        private void publish(OreDepositExperiment.Snapshot activeOreExperiment) {
            LithologyCatalog.install(lithologies);
            OreOccurrenceCatalog.install(oreOccurrences);
            OreDepositExperiment.install(activeOreExperiment);
            GeologyProvinceProfiles.install(provinces);
            SedimentarySuccessions.install(successions);
            SedimentaryFieldProfiles.install(fieldProfiles);
            CorrelatedSedimentaryExperiment.install(experiment);
        }
    }
}
