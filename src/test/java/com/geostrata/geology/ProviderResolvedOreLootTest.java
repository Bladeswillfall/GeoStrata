package com.geostrata.geology;

import com.geostrata.block.GradedOreBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderResolvedOreLootTest {
    @Test
    void emitsResolvedProviderOutput() {
        OreOccurrenceCatalog.Snapshot previous = OreOccurrenceCatalog.current();
        OreOccurrenceCatalog.Occurrence zinc = new OreOccurrenceCatalog.Occurrence(
                "zinc",
                "test_provider",
                "minecraft:diamond",
                List.of("shale"),
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                List.of("stratiform"),
                gradeBlocks("zinc")
        );
        OreOccurrenceCatalog.install(new OreOccurrenceCatalog.Snapshot(
                "grade_economy_active",
                "geostrata",
                "not_implemented",
                null,
                List.of(zinc),
                Map.of("zinc", zinc)
        ));

        try {
            GradedOreBlock block = new GradedOreBlock(
                    "zinc",
                    OreGrade.POOR,
                    AbstractBlock.Settings.copy(Blocks.IRON_ORE)
            );
            CapturingLootBuilder builder = new CapturingLootBuilder();

            assertThrows(CapturedDrop.class, () -> block.getDroppedStacks(block.getDefaultState(), builder));

            List<ItemStack> drops = new ArrayList<>();
            builder.dynamicDrop.add(drops::add);
            assertEquals(1, drops.size());
            assertEquals(Items.DIAMOND, drops.get(0).getItem());
        } finally {
            OreOccurrenceCatalog.install(previous);
        }
    }

    private static Map<OreGrade, String> gradeBlocks(String material) {
        EnumMap<OreGrade, String> blocks = new EnumMap<>(OreGrade.class);
        for (OreGrade grade : OreGrade.values()) {
            blocks.put(grade, "geostrata:" + grade.id() + "_" + material + "_ore");
        }
        return blocks;
    }

    private static final class CapturingLootBuilder extends LootContextParameterSet.Builder {
        private LootContextParameterSet.DynamicDrop dynamicDrop;

        private CapturingLootBuilder() {
            super(null);
        }

        @Override
        public LootContextParameterSet.Builder addDynamicDrop(
                Identifier id,
                LootContextParameterSet.DynamicDrop dynamicDrop
        ) {
            assertEquals(new Identifier("geostrata", "provider_output/zinc"), id);
            this.dynamicDrop = dynamicDrop;
            throw new CapturedDrop();
        }
    }

    private static final class CapturedDrop extends RuntimeException {
    }
}
