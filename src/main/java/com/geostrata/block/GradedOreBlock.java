package com.geostrata.block;

import com.geostrata.geology.OreGrade;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.util.math.intprovider.UniformIntProvider;

/** Ore block whose mining XP follows the shared grade contract. */
public final class GradedOreBlock extends ExperienceDroppingBlock {
    private final OreGrade grade;

    public GradedOreBlock(OreGrade grade, Settings settings) {
        super(settings, UniformIntProvider.create(grade.experienceMin(), grade.experienceMax()));
        this.grade = grade;
    }

    public OreGrade grade() {
        return grade;
    }
}
