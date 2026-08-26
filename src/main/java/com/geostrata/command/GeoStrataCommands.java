package com.geostrata.command;

import com.geostrata.geology.GeologyProvinceSampler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Small diagnostic command surface for inspecting deterministic geology. */
public final class GeoStrataCommands {
    private GeoStrataCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("province")
                                .executes(context -> showProvince(context.getSource()))))
        );
    }

    private static int showProvince(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(source.getWorld().getSeed(), x, z);

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata province: " + sample.province().displayName()
                                + " — " + sample.province().summary()
                                + " | site " + sample.siteX() + ", " + sample.siteZ()
                                + " | " + Math.round(sample.distanceToSite()) + " blocks from site"
                ),
                false
        );
        return 1;
    }
}
