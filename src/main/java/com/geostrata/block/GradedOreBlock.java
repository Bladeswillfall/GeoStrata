package com.geostrata.block;

import com.geostrata.GeoStrata;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.UniformIntProvider;

import java.util.List;

/** Ore block whose mining XP follows the shared grade contract. */
public final class GradedOreBlock extends ExperienceDroppingBlock {
    public static final EnumProperty<OreHost> HOST = EnumProperty.of("host", OreHost.class);

    private final String material;
    private final OreGrade grade;

    public GradedOreBlock(String material, OreGrade grade, Settings settings) {
        super(settings, UniformIntProvider.create(grade.experienceMin(), grade.experienceMax()));
        this.material = material;
        this.grade = grade;
        setDefaultState(getStateManager().getDefaultState().with(HOST, OreHost.defaultFor(material)));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HOST);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        OreOccurrenceCatalog.Occurrence occurrence = OreOccurrenceCatalog.current().byId().get(material);
        if (occurrence != null && !"minecraft".equals(occurrence.providerMod())) {
            Identifier outputId = new Identifier(occurrence.outputItem());
            builder.addDynamicDrop(
                    GeoStrata.id("provider_output/" + material),
                    consumer -> consumer.accept(new ItemStack(Registries.ITEM.get(outputId)))
            );
        }
        return super.getDroppedStacks(state, builder);
    }

    public String material() {
        return material;
    }

    public OreGrade grade() {
        return grade;
    }

    public BlockState withHost(String host) {
        return getDefaultState().with(HOST, OreHost.byId(host));
    }
}
