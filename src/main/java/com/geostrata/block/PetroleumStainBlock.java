package com.geostrata.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.LichenGrower;
import net.minecraft.block.MultifaceGrowthBlock;

/** Thin sculk-vein-style surface evidence for natural petroleum seepage. */
public final class PetroleumStainBlock extends MultifaceGrowthBlock {
    private final LichenGrower grower = new LichenGrower(this);

    public PetroleumStainBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public LichenGrower getGrower() {
        return grower;
    }
}
