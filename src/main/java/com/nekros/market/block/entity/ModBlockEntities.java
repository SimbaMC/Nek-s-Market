package com.nekros.market.block.entity;

import com.nekros.market.NeksMarket;
import com.nekros.market.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NeksMarket.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SellerBoxBlockEntity>> SELLER_BOX =
            BLOCK_ENTITIES.register("seller_box",
                    () -> BlockEntityType.Builder.of(SellerBoxBlockEntity::new, ModBlocks.SELLER_BOX.get()).build(null));

    private ModBlockEntities() {
    }
}
