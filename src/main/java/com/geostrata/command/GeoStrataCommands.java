package com.geostrata.command;

import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.GeologyProvinceSampler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small diagnostic command surface for inspecting deterministic geology. */
public final class GeoStrataCommands {
    private GeoStrataCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("province")
                                .executes(context -> showProvince(context.getSource())))
                        .then(CommandManager.literal("profile")
                                .executes(context -> showProfile(context.getSource()))))
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

    private static GeologyProvinceSampler.Sample sample(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        return GeologyProvinceSampler.sample(source.getWorld().getSeed(), x, z);
    }
}
