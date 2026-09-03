package com.geostrata.block;

import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderResolvedOreLootTest {
    @Test
    void emitsResolvedProviderOutput() {
        OreOccurrenceCatalog.Occurrence zinc = new OreOccurrenceCatalog.Occurrence(
                "zinc",
                "test_provider",
                "test:resolved_zinc",
                List.of("shale"),
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                List.of("stratiform"),
                gradeBlocks("zinc")
        );
        CapturingLootBuilder builder = new CapturingLootBuilder();

        assertThrows(CapturedDrop.class, () -> GradedOreBlock.addProviderOutputDrop(
                "zinc",
                zinc,
                builder,
                outputId -> {
                    throw new CapturedOutput(outputId);
                }
        ));

        CapturedOutput output = assertThrows(CapturedOutput.class, () -> builder.dynamicDrop.add(ignored -> {
        }));
        assertEquals(new Identifier("test", "resolved_zinc"), output.outputId);
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

    private static final class CapturedOutput extends RuntimeException {
        private final Identifier outputId;

        private CapturedOutput(Identifier outputId) {
            this.outputId = outputId;
        }
    }
}
