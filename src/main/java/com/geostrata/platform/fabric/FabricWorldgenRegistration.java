package com.geostrata.platform.fabric;

import com.geostrata.GeoStrata;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;
import java.util.function.Consumer;

/** Fabric biome-modification adapter for GeoStrata's shared data-driven placed features. */
public final class FabricWorldgenRegistration {
    private static final String BENCHMARK_DISABLE_COMMON_OWNERSHIP_ENV =
            "GEOSTRATA_BENCHMARK_DISABLE_CORE_COMMON_ORE_OWNERSHIP";
    private static final List<RegistryKey<PlacedFeature>> REPLACED_VANILLA_CORE_ORES = List.of(
            OrePlacedFeatures.ORE_COAL_UPPER,
            OrePlacedFeatures.ORE_COAL_LOWER,
            OrePlacedFeatures.ORE_IRON_UPPER,
            OrePlacedFeatures.ORE_IRON_MIDDLE,
            OrePlacedFeatures.ORE_IRON_SMALL,
            OrePlacedFeatures.ORE_COPPER,
            OrePlacedFeatures.ORE_COPPER_LARGE,
            OrePlacedFeatures.ORE_GOLD_EXTRA,
            OrePlacedFeatures.ORE_GOLD,
            OrePlacedFeatures.ORE_GOLD_LOWER,
            OrePlacedFeatures.ORE_EMERALD,
            OrePlacedFeatures.ORE_DIAMOND,
            OrePlacedFeatures.ORE_DIAMOND_LARGE,
            OrePlacedFeatures.ORE_DIAMOND_BURIED
    );
    private static final Identifier CREATE_RAW_ZINC = new Identifier("create", "raw_zinc");
    private static final RegistryKey<PlacedFeature> CREATE_ZINC_ORE = providerFeature("create", "zinc_ore");
    private static final Identifier CREATE_DD_RAW_TIN = new Identifier("create_dd", "raw_tin");
    private static final RegistryKey<PlacedFeature> CREATE_DD_TIN_ORE = providerFeature("create_dd", "tin_ore");
    private static final Identifier MODERN_INDUSTRIALIZATION_RAW_TIN =
            new Identifier("modern_industrialization", "raw_tin");
    private static final List<RegistryKey<PlacedFeature>> MODERN_INDUSTRIALIZATION_TIN_ORES = List.of(
            providerFeature("modern_industrialization", "ore_generator_tin"),
            providerFeature("modern_industrialization", "deepslate_ore_generator_tin")
    );
    private static final Identifier TECH_REBORN_RAW_TIN = new Identifier("techreborn", "raw_tin");
    private static final RegistryKey<PlacedFeature> TECH_REBORN_TIN_ORE = providerFeature("techreborn", "tin_ore");
    private static final Identifier TECH_REBORN_RAW_SILVER = new Identifier("techreborn", "raw_silver");
    private static final RegistryKey<PlacedFeature> TECH_REBORN_SILVER_ORE =
            providerFeature("techreborn", "silver_ore");
    private static final Identifier CREATE_NEW_AGE_THORIUM = new Identifier("create_new_age", "thorium");
    private static final RegistryKey<PlacedFeature> CREATE_NEW_AGE_THORIUM_ORE =
            providerFeature("create_new_age", "thorium_ore");
    private static final Identifier CREATE_NEW_AGE_MAGNETITE = new Identifier("create_new_age", "magnetite_block");
    private static final RegistryKey<PlacedFeature> CREATE_NEW_AGE_MAGNETITE_ORE =
            providerFeature("create_new_age", "magnetite");
    private static final Identifier CREATE_NUCLEAR_RAW_URANIUM = new Identifier("createnuclear", "raw_uranium");
    private static final RegistryKey<PlacedFeature> CREATE_NUCLEAR_URANIUM_ORE =
            providerFeature("createnuclear", "uranium_ore");
    private static final Identifier MODERN_INDUSTRIALIZATION_RAW_URANIUM =
            new Identifier("modern_industrialization", "raw_uranium");
    private static final List<RegistryKey<PlacedFeature>> MODERN_INDUSTRIALIZATION_URANIUM_ORES = List.of(
            providerFeature("modern_industrialization", "ore_generator_uranium"),
            providerFeature("modern_industrialization", "deepslate_ore_generator_uranium")
    );
    private static final Identifier TFMG_RAW_LEAD = new Identifier("tfmg", "raw_lead");
    private static final Identifier TFMG_BAUXITE = new Identifier("tfmg", "bauxite");
    private static final Identifier TFMG_LIGNITE = new Identifier("tfmg", "lignite");
    private static final Identifier TFMG_FIRECLAY_BALL = new Identifier("tfmg", "fireclay_ball");
    private static final RegistryKey<PlacedFeature> TFMG_LEAD_ORE = providerFeature("tfmg", "lead_ore");
    private static final RegistryKey<PlacedFeature> TFMG_STRIATED_ORES_OVERWORLD =
            providerFeature("tfmg", "tfmg_striated_ores_overworld");
    private static final Identifier CREATE_NUCLEAR_RAW_LEAD = new Identifier("createnuclear", "raw_lead");
    private static final RegistryKey<PlacedFeature> CREATE_NUCLEAR_LEAD_ORE =
            providerFeature("createnuclear", "lead_ore");
    private static final Identifier TFMG_RAW_NICKEL = new Identifier("tfmg", "raw_nickel");
    private static final RegistryKey<PlacedFeature> TFMG_NICKEL_ORE = providerFeature("tfmg", "nickel_ore");
    private static final Identifier TFMG_RAW_LITHIUM = new Identifier("tfmg", "raw_lithium");
    private static final RegistryKey<PlacedFeature> TFMG_LITHIUM_ORE = providerFeature("tfmg", "lithium_ore");
    private static final TagKey<Biome> HAS_COMMON_ROCKS = biomeTag("has_common_rocks");
    private static final TagKey<Biome> HAS_COASTAL_ROCKS = biomeTag("has_coastal_rocks");
    private static final TagKey<Biome> HAS_SURFACE_SEDIMENTS = biomeTag("has_surface_sediments");
    private static final TagKey<Biome> HAS_BADLANDS_SOILS = biomeTag("has_badlands_soils");
    private static final TagKey<Biome> HAS_EXPERIMENTAL_ORE_DEPOSITS = biomeTag("has_experimental_ore_deposits");
    private static final TagKey<Biome> HAS_EXPERIMENTAL_DIAMOND_GEOLOGY = biomeTag("has_experimental_diamond_geology");
    private static final List<RegistryKey<PlacedFeature>> REPLACED_VANILLA_IGNEOUS_BLOBS = List.of(
            OrePlacedFeatures.ORE_GRANITE_UPPER,
            OrePlacedFeatures.ORE_GRANITE_LOWER,
            OrePlacedFeatures.ORE_DIORITE_UPPER,
            OrePlacedFeatures.ORE_DIORITE_LOWER,
            OrePlacedFeatures.ORE_ANDESITE_UPPER,
            OrePlacedFeatures.ORE_ANDESITE_LOWER,
            OrePlacedFeatures.ORE_TUFF
    );

    private FabricWorldgenRegistration() {
    }

    public static void register() {
        if (commonOreOwnershipEnabled()) {
            removeCoreVanillaOres();
        }
        removeProviderNativeOres();
        removeVanillaIgneousBlobs();
        addToTag("correlated_sedimentary_experiment", HAS_COMMON_ROCKS, GenerationStep.Feature.TOP_LAYER_MODIFICATION);
        addToTag("province_background_experiment", HAS_COMMON_ROCKS, GenerationStep.Feature.TOP_LAYER_MODIFICATION);

        addToTag("clay_loam_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("silty_loam_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("peat_soil_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("wet_mud_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("compacted_mud_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("sandy_loam_patch", HAS_COASTAL_ROCKS);

        addToTag("blue_clay_patch", HAS_COMMON_ROCKS);
        addToTag("blue_clay_background_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_background_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_badlands_patch", HAS_BADLANDS_SOILS);

        addToTag(
                "ore_deposit_experiment",
                HAS_EXPERIMENTAL_ORE_DEPOSITS,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
        addToTag(
                "kimberlite_pipe",
                HAS_EXPERIMENTAL_DIAMOND_GEOLOGY,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
        addToTag(
                "lamproite_pipe",
                HAS_EXPERIMENTAL_DIAMOND_GEOLOGY,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
        addToTag(
                "diamond_structural_experiment",
                HAS_EXPERIMENTAL_DIAMOND_GEOLOGY,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
    }

    static boolean commonOreOwnershipEnabled() {
        return !Boolean.parseBoolean(System.getenv(BENCHMARK_DISABLE_COMMON_OWNERSHIP_ENV));
    }

    private static void removeCoreVanillaOres() {
        BiomeModifications.create(GeoStrata.id("core_ore_worldgen_ownership"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> REPLACED_VANILLA_CORE_ORES.forEach(feature ->
                                context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        feature
                                ))
                );
    }

    private static void removeProviderNativeOres() {
        BiomeModifications.create(GeoStrata.id("provider_ore_worldgen_ownership"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> suppressProviderWorldgen(feature ->
                                context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        feature
                                ))
                );
    }

    private static void suppressProviderWorldgen(Consumer<RegistryKey<PlacedFeature>> remove) {
        suppressWhenPresent(CREATE_RAW_ZINC, remove, CREATE_ZINC_ORE);
        suppressWhenPresent(CREATE_DD_RAW_TIN, remove, CREATE_DD_TIN_ORE);
        suppressWhenPresent(MODERN_INDUSTRIALIZATION_RAW_TIN, remove, MODERN_INDUSTRIALIZATION_TIN_ORES);
        suppressWhenPresent(TECH_REBORN_RAW_TIN, remove, TECH_REBORN_TIN_ORE);
        suppressWhenPresent(TECH_REBORN_RAW_SILVER, remove, TECH_REBORN_SILVER_ORE);
        suppressWhenPresent(CREATE_NEW_AGE_THORIUM, remove, CREATE_NEW_AGE_THORIUM_ORE);
        suppressWhenPresent(CREATE_NEW_AGE_MAGNETITE, remove, CREATE_NEW_AGE_MAGNETITE_ORE);
        suppressWhenPresent(CREATE_NUCLEAR_RAW_URANIUM, remove, CREATE_NUCLEAR_URANIUM_ORE);
        suppressWhenPresent(
                MODERN_INDUSTRIALIZATION_RAW_URANIUM,
                remove,
                MODERN_INDUSTRIALIZATION_URANIUM_ORES
        );
        suppressWhenPresent(TFMG_RAW_LEAD, remove, TFMG_LEAD_ORE);
        if (Registries.ITEM.containsId(TFMG_RAW_LEAD)
                && Registries.ITEM.containsId(TFMG_BAUXITE)
                && Registries.ITEM.containsId(TFMG_LIGNITE)
                && Registries.ITEM.containsId(TFMG_FIRECLAY_BALL)) {
            remove.accept(TFMG_STRIATED_ORES_OVERWORLD);
        }
        suppressWhenPresent(CREATE_NUCLEAR_RAW_LEAD, remove, CREATE_NUCLEAR_LEAD_ORE);
        suppressWhenPresent(TFMG_RAW_NICKEL, remove, TFMG_NICKEL_ORE);
        suppressWhenPresent(TFMG_RAW_LITHIUM, remove, TFMG_LITHIUM_ORE);
    }

    @SafeVarargs
    private static void suppressWhenPresent(
            Identifier providerOutput,
            Consumer<RegistryKey<PlacedFeature>> remove,
            RegistryKey<PlacedFeature>... features
    ) {
        if (Registries.ITEM.containsId(providerOutput)) {
            List.of(features).forEach(remove);
        }
    }

    private static void suppressWhenPresent(
            Identifier providerOutput,
            Consumer<RegistryKey<PlacedFeature>> remove,
            List<RegistryKey<PlacedFeature>> features
    ) {
        if (Registries.ITEM.containsId(providerOutput)) {
            features.forEach(remove);
        }
    }

    private static void removeVanillaIgneousBlobs() {
        BiomeModifications.create(GeoStrata.id("core_igneous_ownership"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> REPLACED_VANILLA_IGNEOUS_BLOBS.forEach(feature ->
                                context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        feature
                                ))
                );
    }

    private static void addToTag(String feature, TagKey<Biome> tag) {
        addToTag(feature, tag, GenerationStep.Feature.UNDERGROUND_ORES);
    }

    private static void addToTag(String feature, TagKey<Biome> tag, GenerationStep.Feature step) {
        BiomeModifications.addFeature(BiomeSelectors.tag(tag), step, key(feature));
    }

    private static RegistryKey<PlacedFeature> key(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, GeoStrata.id(path));
    }

    private static RegistryKey<PlacedFeature> providerFeature(String namespace, String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(namespace, path));
    }

    private static TagKey<Biome> biomeTag(String name) {
        return TagKey.of(RegistryKeys.BIOME, GeoStrata.id(name));
    }
}
