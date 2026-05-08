package com.nekros.market.item;

import com.nekros.market.NeksMarket;
import com.nekros.market.block.ModBlocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NeksMarket.MODID);

    public static final DeferredItem<BlockItem> SELLER_BOX = ITEMS.registerSimpleBlockItem(
            ModBlocks.SELLER_BOX,
            new Item.Properties());

    private ModItems() {
    }
}
