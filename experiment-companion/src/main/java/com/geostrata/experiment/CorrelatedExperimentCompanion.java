package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;
import java.util.function.Consumer;

/** Explicit opt-in companion for rare ore/diamond experiments and debug tools. */
public final class CorrelatedExperimentCompanion implements ModInitializer {
    private static final String BENCHMARK_SUPPRESS_DIAMOND_ENV = "GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND";
    private static final Identifier CREATE_RAW_ZINC = new Identifier("create", "raw_zinc");
    private static final RegistryKey<PlacedFeature> CREATE_ZINC_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("create", "zinc_ore")
    );
    private static final Identifier CREATE_DD_RAW_TIN = new Identifier("create_dd", "raw_tin");
    private static final RegistryKey<PlacedFeature> CREATE_DD_TIN_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("create_dd", "tin_ore")
    );
    private static final Identifier MODERN_INDUSTRIALIZATION_RAW_TIN = new Identifier(
            "modern_industrialization",
            "raw_tin"
    );
    private static final List<RegistryKey<PlacedFeature>> MODERN_INDUSTRIALIZATION_TIN_ORES = List.of(
            RegistryKey.of(
                    RegistryKeys.PLACED_FEATURE,
                    new Identifier("modern_industrialization", "ore_generator_tin")
            ),
            RegistryKey.of(
                    RegistryKeys.PLACED_FEATURE,
                    new Identifier("modern_industrialization", "deepslate_ore_generator_tin")
            )
    );
    private static final Identifier TECH_REBORN_RAW_TIN = new Identifier("techreborn", "raw_tin");
    private static final RegistryKey<PlacedFeature> TECH_REBORN_TIN_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("techreborn", "tin_ore")
    );
    private static final Identifier TECH_REBORN_RAW_SILVER = new Identifier("techreborn", "raw_silver");
    private static final RegistryKey<PlacedFeature> TECH_REBORN_SILVER_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("techreborn", "silver_ore")
    );
    private static final Identifier CREATE_NEW_AGE_THORIUM = new Identifier("create_new_age", "thorium");
    private static final RegistryKey<PlacedFeature> CREATE_NEW_AGE_THORIUM_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("create_new_age", "thorium_ore")
    );
    private static final Identifier CREATE_NUCLEAR_RAW_URANIUM = new Identifier("createnuclear", "raw_uranium");
    private static final RegistryKey<PlacedFeature> CREATE_NUCLEAR_URANIUM_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("createnuclear", "uranium_ore")
    );
    private static final Identifier MODERN_INDUSTRIALIZATION_RAW_URANIUM = new Identifier(
            "modern_industrialization",
            "raw_uranium"
    );
    private static final List<RegistryKey<PlacedFeature>> MODERN_INDUSTRIALIZATION_URANIUM_ORES = List.of(
            RegistryKey.of(
                    RegistryKeys.PLACED_FEATURE,
                    new Identifier("modern_industrialization", "ore_generator_uranium")
            ),
            RegistryKey.of(
                    RegistryKeys.PLACED_FEATURE,
                    new Identifier("modern_industrialization", "deepslate_ore_generator_uranium")
            )
    );
    private static final Identifier TFMG_RAW_LEAD = new Identifier("tfmg", "raw_lead");
    private static final RegistryKey<PlacedFeature> TFMG_LEAD_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("tfmg", "lead_ore")
    );
    private static final Identifier CREATE_NUCLEAR_RAW_LEAD = new Identifier("createnuclear", "raw_lead");
    private static final RegistryKey<PlacedFeature> CREATE_NUCLEAR_LEAD_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("createnuclear", "lead_ore")
    );
    private static final Identifier TFMG_RAW_NICKEL = new Identifier("tfmg", "raw_nickel");
    private static final RegistryKey<PlacedFeature> TFMG_NICKEL_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("tfmg", "nickel_ore")
    );
    private static final List<RegistryKey<PlacedFeature>> BENCHMARK_DIAMOND_ORES = List.of(
            OrePlacedFeatures.ORE_DIAMOND,
            OrePlacedFeatures.ORE_DIAMOND_LARGE,
            OrePlacedFeatures.ORE_DIAMOND_BURIED
    );

    @Override
    public void onInitialize() {
        boolean benchmarkSuppressVanillaDiamond = Boolean.parseBoolean(System.getenv(BENCHMARK_SUPPRESS_DIAMOND_ENV));
        BiomeModifications.create(GeoStrata.id("companion_ore_worldgen_ownership"))
                    .add(
                            ModificationPhase.REMOVALS,
                            BiomeSelectors.foundInOverworld(),
                            context -> {
                                if (Registries.ITEM.containsId(CREATE_RAW_ZINC)) {
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            CREATE_ZINC_ORE
                                    );
                                }
                                if (Registries.ITEM.containsId(CREATE_DD_RAW_TIN)) {
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            CREATE_DD_TIN_ORE
                                    );
                                }
                                if (Registries.ITEM.containsId(MODERN_INDUSTRIALIZATION_RAW_TIN)) {
                                    MODERN_INDUSTRIALIZATION_TIN_ORES.forEach(feature ->
                                            context.getGenerationSettings().removeFeature(
                                                    GenerationStep.Feature.UNDERGROUND_ORES,
                                                    feature
                                            ));
                                }
                                if (Registries.ITEM.containsId(TECH_REBORN_RAW_TIN)) {
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            TECH_REBORN_TIN_ORE
                                    );
                                }
                                if (Registries.ITEM.containsId(TECH_REBORN_RAW_SILVER)) {
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            TECH_REBORN_SILVER_ORE
                                    );
                                }
                                if (Registries.ITEM.containsId(CREATE_NEW_AGE_THORIUM)) {
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            CREATE_NEW_AGE_THORIUM_ORE
                                    );
                                }
                                if (Registries.ITEM.containsId(CREATE_NUCLEAR_RAW_URANIUM)) {
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            CREATE_NUCLEAR_URANIUM_ORE
                                    );
                                }
                                if (Registries.ITEM.containsId(MODERN_INDUSTRIALIZATION_RAW_URANIUM)) {
                                    MODERN_INDUSTRIALIZATION_URANIUM_ORES.forEach(feature ->
                                            context.getGenerationSettings().removeFeature(
                                                    GenerationStep.Feature.UNDERGROUND_ORES,
                                                    feature
                                            ));
                                }
                                suppressProviderWorldgen(feature -> context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        feature
                                ));
                                if (benchmarkSuppressVanillaDiamond) {
                                    BENCHMARK_DIAMOND_ORES.forEach(feature ->
                                            context.getGenerationSettings().removeFeature(
                                                    GenerationStep.Feature.UNDERGROUND_ORES,
                                                    feature
                                            ));
                                }
                            }
                    );
        OreDebugCommands.register();
        GeoStrata.LOGGER.info("GeoStrata experiment companion enabled; rare ore/diamond experiments and debug tools active");
        if (benchmarkSuppressVanillaDiamond) {
            GeoStrata.LOGGER.info("GeoStrata benchmark mode: vanilla diamond generation suppressed for attribution");
        }
    }

    private static void suppressProviderWorldgen(Consumer<RegistryKey<PlacedFeature>> remove) {
        if (Registries.ITEM.containsId(TFMG_RAW_LEAD)) {
            remove.accept(TFMG_LEAD_ORE);
        }
        if (Registries.ITEM.containsId(CREATE_NUCLEAR_RAW_LEAD)) {
            remove.accept(CREATE_NUCLEAR_LEAD_ORE);
        }
        if (Registries.ITEM.containsId(TFMG_RAW_NICKEL)) {
            remove.accept(TFMG_NICKEL_ORE);
        }
    }
}
