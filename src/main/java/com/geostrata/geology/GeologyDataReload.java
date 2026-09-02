package com.geostrata.geology;

import com.geostrata.GeoStrata;
import com.google.gson.JsonArray;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

/** Loads and publishes the shared geology resource graph in dependency order. */
public final class GeologyDataReload {
    public static final String COMPANION_MOD_ID = "geostrata_correlated_experiment";

    private static final Identifier LITHOLOGIES = GeoStrata.id("geology/lithologies.json");
    private static final Identifier ORE_OCCURRENCES = GeoStrata.id("geology/ore_occurrences.json");
    private static final Identifier EXTERNAL_ORE_OCCURRENCES = GeoStrata.id("geology/external_ore_occurrences.json");
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
            State loaded = parseIncludingExternal(
                    readObject(manager, LITHOLOGIES),
                    readObject(manager, ORE_OCCURRENCES),
                    readObject(manager, EXTERNAL_ORE_OCCURRENCES),
                    readObject(manager, ORE_DEPOSIT_EXPERIMENT),
                    readObject(manager, PROVINCES),
                    readObject(manager, SUCCESSIONS),
                    readObject(manager, FIELD_PROFILES),
                    readObject(manager, EXPERIMENT),
                    GeologyDataReload::registeredItem
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

    private static boolean registeredItem(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        return id != null && Registries.ITEM.containsId(id);
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
            JsonObject experimentRoot
    ) {
        return parse(
                lithologiesRoot,
                oreOccurrencesRoot,
                oreDepositExperimentRoot,
                provincesRoot,
                successionsRoot,
                fieldProfilesRoot,
                experimentRoot,
                ignored -> true
        );
    }

    static State parse(
            JsonObject lithologiesRoot,
            JsonObject oreOccurrencesRoot,
            JsonObject oreDepositExperimentRoot,
            JsonObject provincesRoot,
            JsonObject successionsRoot,
            JsonObject fieldProfilesRoot,
            JsonObject experimentRoot,
            Predicate<String> outputAvailable
    ) {
        return parseState(
                lithologiesRoot,
                oreOccurrencesRoot,
                oreDepositExperimentRoot,
                provincesRoot,
                successionsRoot,
                fieldProfilesRoot,
                experimentRoot,
                outputAvailable
        );
    }

    static State parseIncludingExternal(
            JsonObject lithologiesRoot,
            JsonObject oreOccurrencesRoot,
            JsonObject externalOreOccurrencesRoot,
            JsonObject oreDepositExperimentRoot,
            JsonObject provincesRoot,
            JsonObject successionsRoot,
            JsonObject fieldProfilesRoot,
            JsonObject experimentRoot,
            Predicate<String> outputAvailable
    ) {
        return parseState(
                lithologiesRoot,
                mergeOccurrenceRoots(oreOccurrencesRoot, externalOreOccurrencesRoot),
                oreDepositExperimentRoot,
                provincesRoot,
                successionsRoot,
                fieldProfilesRoot,
                experimentRoot,
                outputAvailable
        );
    }

    private static JsonObject mergeOccurrenceRoots(JsonObject core, JsonObject external) {
        if (core == null || external == null) {
            throw new IllegalArgumentException("core and external ore occurrence catalogs must not be null");
        }
        requireInt(external, "schemaVersion", 1);
        requireString(external, "model", "geostrata:external_ore_occurrence_catalog");
        requireString(external, "runtimeStatus", "optional_provider_gated");
        JsonElement rawExternal = external.get("occurrences");
        if (rawExternal == null || !rawExternal.isJsonArray()) {
            throw new IllegalArgumentException("external ore occurrences must be an array");
        }

        JsonObject merged = core.deepCopy();
        JsonArray occurrences = merged.getAsJsonArray("occurrences");
        if (occurrences == null) {
            throw new IllegalArgumentException("core ore occurrences must be an array");
        }
        for (JsonElement rawOccurrence : rawExternal.getAsJsonArray()) {
            if (!rawOccurrence.isJsonObject()) {
                throw new IllegalArgumentException("external ore occurrence entry must be an object");
            }
            JsonObject occurrence = rawOccurrence.getAsJsonObject();
            if ("minecraft".equals(stringValue(occurrence, "providerMod"))) {
                throw new IllegalArgumentException("external ore occurrence must not declare minecraft as its provider");
            }
            occurrences.add(occurrence.deepCopy());
        }
        return merged;
    }

    private static State parseState(
            JsonObject lithologiesRoot,
            JsonObject oreOccurrencesRoot,
            JsonObject oreDepositExperimentRoot,
            JsonObject provincesRoot,
            JsonObject successionsRoot,
            JsonObject fieldProfilesRoot,
            JsonObject experimentRoot,
            Predicate<String> outputAvailable
    ) {
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.parse(lithologiesRoot);
        validateRuntimeArchitectureLithologies(lithologies);
        OreOccurrenceCatalog.Snapshot oreOccurrences = availableOreOccurrences(
                OreOccurrenceCatalog.parse(lithologies, oreOccurrencesRoot),
                outputAvailable
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
        );
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

    private static OreOccurrenceCatalog.Snapshot availableOreOccurrences(
            OreOccurrenceCatalog.Snapshot occurrences,
            Predicate<String> outputAvailable
    ) {
        if (outputAvailable == null) {
            throw new IllegalArgumentException("ore output availability predicate must not be null");
        }
        LinkedHashMap<String, OreOccurrenceCatalog.Occurrence> available = new LinkedHashMap<>();
        for (OreOccurrenceCatalog.Occurrence occurrence : occurrences.occurrences()) {
            if (outputAvailable.test(occurrence.outputItem())) {
                available.put(occurrence.id(), occurrence);
                continue;
            }
            if ("minecraft".equals(occurrence.providerMod())) {
                throw new IllegalArgumentException(
                        occurrence.id() + " references unavailable core output item " + occurrence.outputItem()
                );
            }
            GeoStrata.LOGGER.info(
                    "Skipping optional GeoStrata ore occurrence {} because provider output {} is not registered",
                    occurrence.id(),
                    occurrence.outputItem()
            );
        }
        return new OreOccurrenceCatalog.Snapshot(
                occurrences.runtimeStatus(),
                occurrences.generationOwner(),
                occurrences.nativeGenerationSuppression(),
                occurrences.gradeModel(),
                List.copyOf(available.values()),
                Collections.unmodifiableMap(available)
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

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static void requireString(JsonObject object, String key, String expected) {
        if (!expected.equals(stringValue(object, key))) {
            throw new IllegalArgumentException(key + " must be " + expected);
        }
    }

    private static void requireInt(JsonObject object, String key, int expected) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || value.getAsDouble() != expected) {
            throw new IllegalArgumentException(key + " must be " + expected);
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
