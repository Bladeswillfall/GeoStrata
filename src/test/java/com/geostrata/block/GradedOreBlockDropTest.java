package com.geostrata.block;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GradedOreBlockDropTest {
    @Test
    void externalDropUsesResolvedProviderOutputAndPreservesCount() {
        ItemStack candidate = new ItemStack(Items.RAW_COPPER, 4);

        ItemStack normalized = GradedOreBlock.normalizeExternalDrop(
                candidate,
                Items.STONE,
                "example_provider",
                Items.RAW_IRON
        );

        assertEquals(Items.RAW_IRON, normalized.getItem());
        assertEquals(4, normalized.getCount());
    }

    @Test
    void silkTouchSelfDropIsNotRewritten() {
        ItemStack self = new ItemStack(Items.STONE, 1);

        ItemStack normalized = GradedOreBlock.normalizeExternalDrop(
                self,
                Items.STONE,
                "example_provider",
                Items.RAW_IRON
        );

        assertSame(self, normalized);
    }

    @Test
    void minecraftOwnedDropIsNotRewritten() {
        ItemStack candidate = new ItemStack(Items.DIAMOND, 2);

        ItemStack normalized = GradedOreBlock.normalizeExternalDrop(
                candidate,
                Items.STONE,
                "minecraft",
                Items.RAW_IRON
        );

        assertSame(candidate, normalized);
    }
}
