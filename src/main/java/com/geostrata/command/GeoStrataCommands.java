package com.geostrata.command;

import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.GeologySurvey;
import com.geostrata.geology.SedimentaryContactPlanner;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.geostrata.geology.SedimentaryStratigraphicField;
import com.geostrata.geology.SedimentarySuccessionSelector;
import com.geostrata.geology.SedimentarySuccessions;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small diagnostic command surface for inspecting deterministic geology. */
public final class GeoStrataCommands {
    private static final int SURVEY_RADIUS = 1536;
    private static final int SURVEY_STEP = 96;

    private GeoStrataCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("province")
                                .executes(context -> showProvince(context.getSource())))
                        .then(CommandManager.literal("profile")
                                .executes(context -> showProfile(context.getSource())))
                        .then(CommandManager.literal("succession")
                                .executes(context -> showSuccession(context.getSource())))
                        .then(CommandManager.literal("column")
                                .executes(context -> showColumn(context.getSource())))
                        .then(CommandManager.literal("field")
                                .executes(context -> showField(context.getSource())))
                        .then(CommandManager.literal("survey")
                                .then(CommandManager.argument("lithology", StringArgumentType.word())
                                        .executes(context -> survey(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "lithology")
                                        )))))
        );
    }

    private static int showProvince(ServerCommandSource source) {
        GeologyProvinceSampler.Sample sample = sample(source);

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata province: " + sample.province().displayName()
                                + " — " + sample.province().summary()
                                + " | site " + sample.siteX() + ", " + sample.siteZ()
                                + " | " + Math.round(sample.distanceToSite()) + " blocks from site"
                                + " | nearest boundary ~" + Math.round(sample.distanceToBoundary()) + " blocks"
                                + " toward " + sample.neighborProvince().displayName()
                ),
                false
        );
        return 1;
    }

    private static int showProfile(ServerCommandSource source) {
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        if (!profiles.loaded()) {
            source.sendError(Text.literal("GeoStrata province profiles have not been loaded yet."));
            return 0;
        }

        GeologyProvinceSampler.Sample sample = sample(source);
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(profiles.effectiveWeights(sample).entrySet());
        ranked.sort((left, right) -> {
            int weightOrder = Double.compare(right.getValue(), left.getValue());
            return weightOrder != 0 ? weightOrder : left.getKey().compareTo(right.getKey());
        });

        StringBuilder top = new StringBuilder();
        int count = Math.min(5, ranked.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                top.append(", ");
            }
            Map.Entry<String, Double> entry = ranked.get(i);
            top.append(entry.getKey()).append(' ').append(Math.round(entry.getValue() * 100.0)).append('%');
        }

        int primaryShare = (int) Math.round((0.5 + 0.5 * sample.interiorBlend(profiles.blendWidthBlocks())) * 100.0);
        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata profile: " + sample.province().displayName()
                                + " / " + sample.neighborProvince().displayName()
                                + " | primary share " + primaryShare + "%"
                                + " | strongest lithologies: " + top
                ),
                false
        );
        return 1;
    }

    private static int showSuccession(ServerCommandSource source) {
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.current();
        if (!profiles.loaded() || !successions.loaded()) {
            source.sendError(Text.literal("GeoStrata geology metadata has not been loaded yet."));
            return 0;
        }

        GeologyProvinceSampler.Sample sample = sample(source);
        long seed = source.getWorld().getSeed();
        SedimentarySuccessionSelector.Selection primary = SedimentarySuccessionSelector.selectForSite(
                seed,
                sample.province(),
                sample.siteX(),
                sample.siteZ(),
                profiles,
                successions
        );
        SedimentarySuccessionSelector.Selection neighbor = SedimentarySuccessionSelector.selectForSite(
                seed,
                sample.neighborProvince(),
                sample.neighborSiteX(),
                sample.neighborSiteZ(),
                profiles,
                successions
        );

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata succession: " + primary.succession().id()
                                + " [" + sample.province().displayName() + "]"
                                + " | beds lower→upper: " + sequence(primary.succession())
                                + " | neighbor: " + neighbor.succession().id()
                                + " [" + sample.neighborProvince().displayName() + "]"
                                + " | diagnostic model only; chunk generation still uses independent features"
                ),
                false
        );
        return 1;
    }

    private static int showColumn(ServerCommandSource source) {
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.current();
        if (!profiles.loaded() || !successions.loaded()) {
            source.sendError(Text.literal("GeoStrata geology metadata has not been loaded yet."));
            return 0;
        }

        GeologyProvinceSampler.Sample sample = sample(source);
        long seed = source.getWorld().getSeed();
        SedimentarySuccessionSelector.Selection selection = SedimentarySuccessionSelector.selectForSite(
                seed,
                sample.province(),
                sample.siteX(),
                sample.siteZ(),
                profiles,
                successions
        );
        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                seed,
                sample.siteX(),
                sample.siteZ(),
                selection.succession()
        );

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata column: " + plan.successionId()
                                + " [" + sample.province().displayName() + "]"
                                + " | normalized lower→upper: " + column(plan)
                                + " | site phase " + Math.round(plan.phase() * 100.0) + "%"
                                + " | percentages are relative motif thickness, not Minecraft Y levels"
                ),
                false
        );
        return 1;
    }

    private static int showField(ServerCommandSource source) {
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!profiles.loaded() || !successions.loaded() || !fieldProfiles.loaded()) {
            source.sendError(Text.literal("GeoStrata geology field metadata has not been loaded yet."));
            return 0;
        }

        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        long seed = source.getWorld().getSeed();
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(seed, x, z);
        SedimentarySuccessionSelector.Selection selection = SedimentarySuccessionSelector.selectForSite(
                seed,
                province.province(),
                province.siteX(),
                province.siteZ(),
                profiles,
                successions
        );
        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                seed,
                province.siteX(),
                province.siteZ(),
                selection.succession()
        );
        SedimentaryStratigraphicField.Parameters parameters = fieldProfiles.parametersFor(
                selection.succession().continuity()
        );
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                seed,
                province.siteX(),
                province.siteZ(),
                parameters
        );
        SedimentaryStratigraphicField.Sample fieldSample = field.sample(x, position.y, z, plan);

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata field: " + fieldSample.bed().lithology()
                                + " | succession " + plan.successionId()
                                + " [" + province.province().displayName() + "]"
                                + " | cycle " + fieldSample.cycleIndex()
                                + ", position " + Math.round(fieldSample.fraction() * 100.0) + "%"
                                + " | structural offset " + Math.round(fieldSample.verticalOffset()) + " blocks"
                                + " | " + selection.succession().continuity() + " profile, cycle "
                                + Math.round(parameters.cycleThicknessBlocks()) + " blocks"
                                + " | virtual model only; no blocks placed"
                ),
                false
        );
        return 1;
    }

    private static String sequence(SedimentarySuccessions.Succession succession) {
        return succession.beds().stream()
                .map(SedimentarySuccessions.Bed::lithology)
                .collect(Collectors.joining(" → "));
    }

    private static String column(SedimentaryContactPlanner.Plan plan) {
        return plan.intervals().stream()
                .map(interval -> Math.round(interval.lowerFraction() * 100.0)
                        + "–" + Math.round(interval.upperFraction() * 100.0)
                        + "% " + interval.lithology())
                .collect(Collectors.joining(" → "));
    }

    private static int survey(ServerCommandSource source, String lithology) {
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        if (!profiles.loaded()) {
            source.sendError(Text.literal("GeoStrata province profiles have not been loaded yet."));
            return 0;
        }
        if (!profiles.lithologyIds().contains(lithology)) {
            source.sendError(Text.literal("Unknown GeoStrata lithology: " + lithology));
            return 0;
        }

        Vec3d position = source.getPosition();
        int originX = MathHelper.floor(position.x);
        int originZ = MathHelper.floor(position.z);
        long seed = source.getWorld().getSeed();
        GeologyProvinceSampler.Sample here = GeologyProvinceSampler.sample(seed, originX, originZ);
        double hereWeight = profiles.effectiveWeight(here, lithology);
        GeologySurvey.Result best = GeologySurvey.findBest(
                seed,
                originX,
                originZ,
                lithology,
                profiles,
                SURVEY_RADIUS,
                SURVEY_STEP
        );

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata survey " + lithology
                                + ": here " + Math.round(hereWeight * 100.0) + "% suitability"
                                + " | best sampled " + Math.round(best.weight() * 100.0) + "% at "
                                + best.x() + ", " + best.z()
                                + " (~" + Math.round(best.distance()) + " blocks, "
                                + best.province().displayName() + ")"
                                + " | regional suitability only; not a located rock body"
                ),
                false
        );
        return 1;
    }

    private static GeologyProvinceSampler.Sample sample(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        return GeologyProvinceSampler.sample(source.getWorld().getSeed(), x, z);
    }
}
