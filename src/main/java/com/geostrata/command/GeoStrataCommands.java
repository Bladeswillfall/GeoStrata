package com.geostrata.command;

import com.geostrata.geology.CorrelatedExperimentChunkOwnership;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.CorrelatedSedimentaryRuntime;
import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.FaultControlledOrePlanner;
import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.GeologySurvey;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.OreDepositCandidatePlanner;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreDepositGeometry;
import com.geostrata.geology.OreOccurrenceCatalog;
import com.geostrata.geology.ProvinceBackgroundRuntime;
import com.geostrata.geology.SedimentaryContactPlanner;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.geostrata.geology.SedimentaryStratigraphicField;
import com.geostrata.geology.SedimentarySuccessionSelector;
import com.geostrata.geology.SedimentarySuccessions;
import com.geostrata.geology.TerrainMorphologySample;
import com.geostrata.geology.TerrainAwareStructuralField;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                        .then(CommandManager.literal("terrain")
                                .executes(context -> showTerrain(context.getSource())))
                        .then(CommandManager.literal("experiment")
                                .executes(context -> showExperiment(context.getSource())))
                        .then(CommandManager.literal("lithology")
                                .then(CommandManager.argument("lithology", StringArgumentType.word())
                                        .executes(context -> showLithology(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "lithology")
                                        ))))
                        .then(CommandManager.literal("ore")
                                .then(CommandManager.argument("material", StringArgumentType.word())
                                        .executes(context -> showOre(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "material")
                                        ))
                                        .then(CommandManager.literal("candidate")
                                                .executes(context -> showOreCandidate(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "material")
                                                )))))
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
                                + " | correlated companion reuses this selector where it owns generation"
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
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated =
                CorrelatedSedimentaryRuntime.resolve(source.getWorld(), x, z);
        if (correlated.isPresent()) {
            CorrelatedSedimentaryRuntime.TerrainAwareSite site = correlated.get();
            SedimentaryStratigraphicField.Sample fieldSample = site.sample(x, position.y, z);
            TerrainAwareStructuralField.Field structuralField = site.field();
            source.sendFeedback(
                    () -> Text.literal(
                            "GeoStrata field: " + fieldSample.bed().lithology()
                                    + " | succession " + site.succession().id()
                                    + " [" + site.ownership().province().displayName() + "]"
                                    + " | authority correlated"
                                    + " | cycle " + fieldSample.cycleIndex()
                                    + ", position " + Math.round(fieldSample.fraction() * 100.0) + "%"
                                    + " | structural offset " + Math.round(fieldSample.verticalOffset()) + " blocks"
                                    + " (drape " + Math.round(structuralField.drapeOffset(x, z))
                                    + ", terrain fold " + Math.round(structuralField.foldOffset(x, z))
                                    + ", tectonic fold " + Math.round(structuralField.tectonicFoldOffset(x, z)) + ")"
                                    + " | " + site.succession().continuity() + " profile, cycle "
                                    + Math.round(structuralField.baseField().cycleThicknessBlocks()) + " blocks"
                                    + " | active worldgen field"
                    ),
                    false
            );
            return 1;
        }

        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!profiles.loaded() || !successions.loaded() || !fieldProfiles.loaded()) {
            source.sendError(Text.literal("GeoStrata geology field metadata has not been loaded yet."));
            return 0;
        }

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
        TerrainAwareStructuralField.Field structuralField = ChunkGeneratorTerrainMorphologySampler.structuralField(
                source.getWorld(),
                x,
                z,
                province.province(),
                field
        );
        SedimentaryStratigraphicField.Sample fieldSample = structuralField.sample(x, position.y, z, plan);

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata field: " + fieldSample.bed().lithology()
                                + " | succession " + plan.successionId()
                                + " [" + province.province().displayName() + "]"
                                + " | authority virtual"
                                + " | cycle " + fieldSample.cycleIndex()
                                + ", position " + Math.round(fieldSample.fraction() * 100.0) + "%"
                                + " | structural offset " + Math.round(fieldSample.verticalOffset()) + " blocks"
                                + " (drape " + Math.round(structuralField.drapeOffset(x, z))
                                + ", fold " + Math.round(structuralField.foldOffset(x, z)) + ")"
                                + " | " + selection.succession().continuity() + " profile, cycle "
                                + Math.round(parameters.cycleThicknessBlocks()) + " blocks"
                                + " | response drape "
                                + Math.round(structuralField.response().drapeCoupling() * 100.0) + "%"
                                + ", fold "
                                + Math.round(structuralField.response().foldCoupling() * 100.0) + "%"
                                + " | virtual model outside correlated ownership"
                ),
                false
        );
        return 1;
    }

    private static int showTerrain(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        TerrainMorphologySample terrain = ChunkGeneratorTerrainMorphologySampler.sample(source.getWorld(), x, z);
        GeologyProvinceSampler.Sample province = sample(source);
        TerrainAwareStructuralField.Response response = TerrainAwareStructuralField.responseFor(province.province());

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata terrain: generator height " + Math.round(terrain.centerHeight())
                                + " | relief " + Math.round(terrain.relief()) + " blocks"
                                + " | slope " + round(terrain.slopeMagnitude())
                                + " [x " + round(terrain.gradientX()) + ", z " + round(terrain.gradientZ()) + "]"
                                + " | prominence " + round(terrain.prominence()) + " blocks"
                                + " | spacing "
                                + ChunkGeneratorTerrainMorphologySampler.DEFAULT_SAMPLE_SPACING_BLOCKS + " blocks"
                                + " | " + province.province().displayName() + " coupling "
                                + "drape " + Math.round(response.drapeCoupling() * 100.0) + "%"
                                + ", fold " + Math.round(response.foldCoupling() * 100.0) + "%"
                                + " | active in the field model and opt-in correlated generation"
                ),
                false
        );
        return 1;
    }

    private static int showExperiment(ServerCommandSource source) {
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        if (!experiment.loaded()) {
            source.sendError(Text.literal("GeoStrata correlated experiment metadata has not been loaded yet."));
            return 0;
        }

        if (!experiment.enabled()) {
            source.sendFeedback(
                    () -> Text.literal(
                            "GeoStrata correlated experiment: disabled"
                                    + " | targets " + String.join(",", experiment.targetSuccessionIds())
                                    + " | supersedes " + String.join(",", experiment.supersededLithologies())
                                    + " | core remains on baseline worldgen"
                    ),
                    false
            );
            return 1;
        }

        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int y = MathHelper.floor(position.y);
        int z = MathHelper.floor(position.z);
        long seed = source.getWorld().getSeed();
        CorrelatedSedimentaryExperiment.Ownership ownership =
                CorrelatedExperimentChunkOwnership.ownershipForChunk(seed, x, z);
        var resolved = CorrelatedSedimentaryRuntime.resolve(source.getWorld(), x, z);
        if (resolved.isPresent()) {
            return showResolvedExperiment(source, position, y, resolved.get());
        }

        String province = ownership.province() == null ? "n/a" : ownership.province().displayName();
        String succession = ownership.successionId() == null ? "n/a" : ownership.successionId();
        String boundary = Double.isFinite(ownership.boundaryDistanceBlocks())
                ? Long.toString(Math.round(ownership.boundaryDistanceBlocks()))
                : "n/a";

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata correlated experiment: baseline"
                                + " | reason " + ownership.reason()
                                + " | province " + province
                                + " | boundary ~" + boundary + " blocks"
                                + " | succession " + succession
                ),
                false
        );
        return 1;
    }

    private static int showResolvedExperiment(
            ServerCommandSource source,
            Vec3d position,
            int y,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site
    ) {
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        SedimentaryStratigraphicField.Sample sample = site.sample(x, position.y, z);
        int minY = Math.max(
                source.getWorld().getBottomY(),
                source.getWorld().getSeaLevel() + experiment.verticalWindow().minOffsetBlocks()
        );
        int maxY = Math.min(
                source.getWorld().getTopY() - 1,
                source.getWorld().getSeaLevel() + experiment.verticalWindow().maxOffsetBlocks()
        );
        String actualBlock = Registries.BLOCK.getId(
                source.getWorld().getBlockState(new BlockPos(x, y, z)).getBlock()
        ).toString();
        String windowState = y >= minY && y <= maxY ? "inside" : "outside";
        CorrelatedSedimentaryExperiment.Ownership ownership = site.ownership();

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata correlated experiment: OWNS"
                                + " | province " + ownership.province().displayName()
                                + " | boundary ~" + Math.round(ownership.boundaryDistanceBlocks()) + " blocks"
                                + " | succession " + site.succession().id()
                                + " | field " + sample.bed().lithology()
                                + " at Y " + y + " (actual " + actualBlock + ")"
                                + " | cycle " + sample.cycleIndex()
                                + ", position " + Math.round(sample.fraction() * 100.0) + "%"
                                + " | terrain offset " + Math.round(site.field().terrainOffset(x, z))
                                + " blocks (fold " + Math.round(site.field().foldOffset(x, z)) + ")"
                                + " | " + windowState + " mutation window " + minY + ".." + maxY
                ),
                false
        );
        return 1;
    }

    private static int showLithology(ServerCommandSource source, String lithology) {
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!catalog.loaded()) {
            source.sendError(Text.literal("GeoStrata lithology catalog has not been loaded yet."));
            return 0;
        }

        LithologyCatalog.Entry entry;
        try {
            entry = catalog.require(lithology);
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata lithology " + entry.id()
                                + ": " + entry.rockClass() + ", " + entry.genesis()
                                + " | body " + entry.bodyStyle()
                                + ", depth " + entry.depthAffinity()
                                + ", continuity " + entry.continuity()
                                + " | block " + entry.block()
                                + " | biome tag #" + entry.biomeTag()
                                + " | baseline " + entry.baselineFeature()
                ),
                false
        );
        return 1;
    }

    private static int showOre(ServerCommandSource source, String material) {
        OreOccurrenceCatalog.Snapshot catalog = OreOccurrenceCatalog.current();
        if (!catalog.loaded()) {
            source.sendError(Text.literal("GeoStrata ore occurrence catalog has not been loaded yet."));
            return 0;
        }

        OreOccurrenceCatalog.Occurrence occurrence;
        try {
            occurrence = catalog.require(material);
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata ore " + occurrence.id()
                                + ": output " + occurrence.outputItem()
                                + " | hosts " + String.join(",", occurrence.hostLithologies())
                                + " | provinces " + occurrence.provinceContexts().stream()
                                .map(province -> province.id())
                                .collect(Collectors.joining(","))
                                + " | deposit styles " + String.join(",", occurrence.depositStyles())
                                + " | grades " + gradeEconomy(catalog.gradeModel(), occurrence)
                                + (OreDepositExperiment.current().enabled()
                                        ? " | EXPERIMENTAL deposit placement enabled; native suppression inactive"
                                        : " | deposit experiment disabled; native suppression inactive")
                ),
                false
        );
        return 1;
    }

    private static int showOreCandidate(ServerCommandSource source, String material) {
        OreOccurrenceCatalog.Snapshot catalog = OreOccurrenceCatalog.current();
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!catalog.loaded() || !lithologies.loaded() || !fieldProfiles.loaded()) {
            source.sendError(Text.literal("GeoStrata ore, lithology and field metadata have not been loaded yet."));
            return 0;
        }

        OreOccurrenceCatalog.Occurrence occurrence;
        try {
            occurrence = catalog.require(material);
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }

        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int y = MathHelper.floor(position.y);
        int z = MathHelper.floor(position.z);
        long seed = source.getWorld().getSeed();
        OreDepositCandidatePlanner.Proposal rawProposal = OreDepositCandidatePlanner.propose(
                seed,
                x,
                y,
                z,
                occurrence
        );
        FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(
                seed,
                rawProposal,
                fieldProfiles.parametersFor("regional").cycleThicknessBlocks()
        );
        OreDepositCandidatePlanner.Proposal proposal = binding.proposal();
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(
                seed,
                proposal.anchorX(),
                proposal.anchorZ()
        );
        String host = candidateHost(source, proposal, lithologies);
        Optional<OreDepositCandidatePlanner.Candidate> candidate = OreDepositCandidatePlanner.accept(
                proposal,
                occurrence,
                province.province(),
                host
        );
        String status = candidate.isPresent()
                ? "anchor-qualified geometry preview"
                : "anchor diagnostic: " + candidateRejection(source, proposal, occurrence, province, host);
        String geometry = candidate.isPresent()
                ? bodySummary(binding.body(seed), binding.faultAligned(), x, y, z)
                : "anchor-preview unavailable until host and province qualify";

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata ore candidate " + occurrence.id()
                                + ": cell " + proposal.cellX() + "," + proposal.cellY() + "," + proposal.cellZ()
                                + " | anchor " + proposal.anchorX() + "," + proposal.anchorY() + "," + proposal.anchorZ()
                                + " | style " + proposal.depositStyle()
                                + " | province " + province.province().id()
                                + " | host " + (host == null ? "unresolved" : host)
                                + " | " + status
                                + " | " + geometry
                                + (OreDepositExperiment.current().enabled()
                                        ? " | experiment enabled; runtime placement uses province + local-host clipping; native suppression inactive"
                                        : " | preview only; deposit experiment disabled; native suppression inactive")
                ),
                false
        );
        return 1;
    }

    private static String bodySummary(
            OreDepositGeometry.Body body,
            boolean faultAligned,
            int x,
            int y,
            int z
    ) {
        OreDepositGeometry.Sample sample = body.sample(x, y, z);
        return "body " + Math.round(body.lengthRadius() * 2.0)
                + "x" + Math.round(body.widthRadius() * 2.0)
                + "x" + Math.round(body.thicknessRadius() * 2.0)
                + " blocks, dip " + Math.round(Math.toDegrees(body.dipRadians())) + "deg"
                + ", bearing " + Math.round(Math.toDegrees(body.azimuthRadians())) + "deg"
                + (faultAligned ? " fault-aligned" : "")
                + ", branches " + body.branches().size()
                + " | here " + sample.zone()
                + " at " + Math.round(sample.concentration() * 100.0) + "% concentration";
    }

    private static String gradeEconomy(
            OreOccurrenceCatalog.GradeModel model,
            OreOccurrenceCatalog.Occurrence occurrence
    ) {
        return model.economies().stream()
                .map(economy -> economy.grade().id()
                        + "=" + occurrence.gradeBlocks().get(economy.grade())
                        + " x" + economy.baseYield()
                        + " xp" + economy.experienceMin() + "-" + economy.experienceMax())
                .collect(Collectors.joining(","));
    }

    private static String candidateHost(
            ServerCommandSource source,
            OreDepositCandidatePlanner.Proposal proposal,
            LithologyCatalog.Snapshot lithologies
    ) {
        BlockPos anchor = new BlockPos(proposal.anchorX(), proposal.anchorY(), proposal.anchorZ());
        String block = Registries.BLOCK.getId(source.getWorld().getBlockState(anchor).getBlock()).toString();
        Optional<String> direct = lithologies.entries().stream()
                .filter(entry -> entry.block().equals(block))
                .map(LithologyCatalog.Entry::id)
                .findFirst();
        if (direct.isPresent()) {
            return direct.get();
        }
        if (!insideCorrelatedWindow(source, proposal.anchorY()) || !isVirtualHost(source, anchor)) {
            return null;
        }

        Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated = CorrelatedSedimentaryRuntime.resolve(
                source.getWorld(),
                proposal.anchorX(),
                proposal.anchorZ()
        );
        if (correlated.isPresent()) {
            return correlated.get().outputLithology(
                    source.getWorld().getSeed(),
                    proposal.anchorX(),
                    proposal.anchorY(),
                    proposal.anchorZ(),
                    lithologies
            );
        }
        return ProvinceBackgroundRuntime.resolve(
                source.getWorld(),
                proposal.anchorX(),
                proposal.anchorZ()
        ).map(background -> background.lithologyAt(
                proposal.anchorX(),
                proposal.anchorY(),
                proposal.anchorZ()
        )).orElse(null);
    }

    private static boolean isVirtualHost(ServerCommandSource source, BlockPos anchor) {
        Identifier id = Identifier.tryParse(CorrelatedSedimentaryExperiment.current().hostBlockTag());
        if (id == null) {
            throw new IllegalStateException("Invalid correlated host block tag");
        }
        return source.getWorld().getBlockState(anchor).isIn(TagKey.of(RegistryKeys.BLOCK, id));
    }

    private static boolean insideCorrelatedWindow(ServerCommandSource source, int y) {
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }
        int minY = Math.max(
                source.getWorld().getBottomY(),
                source.getWorld().getSeaLevel() + experiment.verticalWindow().minOffsetBlocks()
        );
        int maxY = Math.min(
                source.getWorld().getTopY() - 1,
                source.getWorld().getSeaLevel() + experiment.verticalWindow().maxOffsetBlocks()
        );
        return y >= minY && y <= maxY;
    }

    private static String candidateRejection(
            ServerCommandSource source,
            OreDepositCandidatePlanner.Proposal proposal,
            OreOccurrenceCatalog.Occurrence occurrence,
            GeologyProvinceSampler.Sample province,
            String host
    ) {
        if (!occurrence.provinceContexts().contains(province.province())) {
            return "province is outside the occurrence contract";
        }
        if (host == null) {
            return "anchor block " + blockAt(source, proposal) + " has no GeoStrata host identity";
        }
        return "host " + host + " is outside the occurrence contract";
    }

    private static String blockAt(ServerCommandSource source, OreDepositCandidatePlanner.Proposal proposal) {
        BlockPos anchor = new BlockPos(proposal.anchorX(), proposal.anchorY(), proposal.anchorZ());
        return Registries.BLOCK.getId(source.getWorld().getBlockState(anchor).getBlock()).toString();
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

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
