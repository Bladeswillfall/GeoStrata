package com.geostrata.worldgen.feature;

import com.geostrata.GeoStrata;
import com.geostrata.geology.OreDepositExperiment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/** Reuses the late geology pass to remove vanilla common-ore states that are not placed features. */
final class VanillaCommonOreCleanup {
    private static final TagKey<Block> REPLACED_BLOCKS = TagKey.of(
            RegistryKeys.BLOCK,
            GeoStrata.id("worldgen/replaced_vanilla_common_ores")
    );

    private VanillaCommonOreCleanup() {
    }

    static boolean replaceable(BlockState state, TagKey<Block> hostTag) {
        return state.isIn(hostTag)
                || ownsCommonOverworldOres() && state.isIn(REPLACED_BLOCKS);
    }

    private static boolean ownsCommonOverworldOres() {
        return "core_common_overworld".equals(OreDepositExperiment.current().nativeGenerationSuppression());
    }
}
