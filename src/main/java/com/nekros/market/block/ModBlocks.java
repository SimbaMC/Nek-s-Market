package com.nekros.market.block;

import com.nekros.market.NeksMarket;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NeksMarket.MODID);

    public static final DeferredBlock<SellerBoxBlock> SELLER_BOX = BLOCKS.registerBlock(
            "seller_box",
            SellerBoxBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    private ModBlocks() {
    }
}
