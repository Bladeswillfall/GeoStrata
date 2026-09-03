package com.geostrata.block;

import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderResolvedOreLootTest {
    @Test
    void emitsResolvedProviderOutput() {
        OreOccurrenceCatalog.Snapshot previous = OreOccurrenceCatalog.current();
        OreOccurrenceCatalog.Occurrence zinc = new OreOccurrenceCatalog.Occurrence(
                "zinc",
                "test_provider",
                "test:resolved_zinc",
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
            CapturingLootBuilder builder = new CapturingLootBuilder();
            Item expected = new Item(new Item.Settings());

            assertThrows(CapturedDrop.class, () -> GradedOreBlock.addProviderOutputDrop(
                    "zinc",
                    builder,
                    outputId -> {
                        assertEquals(new Identifier("test", "resolved_zinc"), outputId);
                        return new ItemStack(expected);
                    }
            ));

            List<ItemStack> drops = new ArrayList<>();
            builder.dynamicDrop.add(drops::add);
            assertEquals(1, drops.size());
            assertSame(expected, drops.get(0).getItem());
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
